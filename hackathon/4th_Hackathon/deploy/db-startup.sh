#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# openrun-db VM 부팅 스크립트 — DB를 "펫 손작업"이 아니라 코드로 박제 (재현성 핵심)
#
# 이 한 스크립트가 부팅 시 전부 자동 수행:
#  1) MariaDB 설치 + VPC 내부 수신(bind-address) + DB/계정 생성
#  2) 소스 클론 + jar 빌드
#  3) 스키마 + 시드 1회 생성 (prod=validate라 최초 1회 필요)
#  4) 빌드한 jar을 비공개 버킷에 업로드 → 앱 인스턴스(MIG)가 받아감
#
# 메타데이터: jar-bucket(업로드 대상), repo-url(클론 대상)
# 비밀: DB 비번은 Secret Manager(openrun-db-pw)에서 인증 조회 (평문 금지)
# 로그: /var/log/openrun-db-startup.log
# ─────────────────────────────────────────────────────────────────────────────
set -e
exec > /var/log/openrun-db-startup.log 2>&1

M="http://metadata.google.internal/computeMetadata/v1"
H="Metadata-Flavor: Google"
PROJECT=$(curl -s -H "$H" "$M/project/project-id")
BUCKET=$(curl -s -H "$H" "$M/instance/attributes/jar-bucket")
REPO_URL=$(curl -s -H "$H" "$M/instance/attributes/repo-url")
TOKEN=$(curl -s -H "$H" "$M/instance/service-accounts/default/token" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
DB_PW=$(curl -s -H "Authorization: Bearer $TOKEN" "https://secretmanager.googleapis.com/v1/projects/${PROJECT}/secrets/openrun-db-pw/versions/latest:access" | python3 -c "import sys,json,base64;print(base64.b64decode(json.load(sys.stdin)['payload']['data']).decode())")

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y mariadb-server openjdk-21-jdk git curl python3

# 1) MariaDB: VPC 내부 수신 + DB/계정 (원격 접속용 @'%')
sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mariadb.conf.d/50-server.cnf
systemctl restart mariadb
mysql -e "CREATE DATABASE IF NOT EXISTS openrun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -e "CREATE USER IF NOT EXISTS 'openrun'@'%' IDENTIFIED BY '${DB_PW}'; GRANT ALL ON openrun.* TO 'openrun'@'%'; FLUSH PRIVILEGES;"

# 2) 소스 클론 + 빌드
cd /opt
rm -rf repo
git clone "$REPO_URL" repo
cd repo/hackathon/4th_Hackathon
chmod +x gradlew
# Gradle 배포판 다운로드가 느려 실패할 수 있어 재시도 (wrapper networkTimeout=120s)
for i in 1 2 3; do ./gradlew clean bootJar --no-daemon && break; echo "빌드 실패 — 재시도 $i/3"; sleep 15; done
cp build/libs/openrun-*.jar /opt/openrun.jar

# 3) 스키마 + 시드 1회 생성 (백그라운드로 띄워 'Started' 뜨면 종료)
DB_USER=openrun DB_PASSWORD="$DB_PW" SPRING_DATASOURCE_URL="jdbc:mariadb://localhost:3306/openrun?serverTimezone=Asia/Seoul" \
  java -jar /opt/openrun.jar --spring.profiles.active=prod --spring.jpa.hibernate.ddl-auto=update > /tmp/openrun-boot.log 2>&1 &
APP_PID=$!
for i in $(seq 1 120); do grep -q "Started OpenrunApplication" /tmp/openrun-boot.log && break; sleep 2; done
kill "$APP_PID" 2>/dev/null || true
sleep 3

# 4) jar을 비공개 버킷에 업로드 (앱 인스턴스가 받아갈 곳)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/java-archive" \
  --data-binary @/opt/openrun.jar \
  "https://storage.googleapis.com/upload/storage/v1/b/${BUCKET}/o?uploadType=media&name=openrun.jar"

echo "DB 부트스트랩 + jar 업로드 완료"

# 5) cloudflared Quick Tunnel — GCP LB로 공개 HTTPS URL 제공 (실패해도 DB/jar엔 영향 없게 best-effort)
set +e
curl -sL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb" -o /tmp/cloudflared.deb
dpkg -i /tmp/cloudflared.deb || apt-get install -f -y

# LB(openrun-fr) IP가 생길 때까지 기다렸다가 터널을 띄우는 래퍼. (DB VM 부팅이 LB 생성보다 빠를 수 있으므로 폴링)
cat > /opt/cloudflared-run.sh <<'CFRUN'
#!/bin/bash
M="http://metadata.google.internal/computeMetadata/v1"; H="Metadata-Flavor: Google"
PROJECT=$(curl -s -H "$H" "$M/project/project-id")
while :; do
  TOKEN=$(curl -s -H "$H" "$M/instance/service-accounts/default/token" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
  LB_IP=$(curl -s -H "Authorization: Bearer $TOKEN" "https://compute.googleapis.com/compute/v1/projects/$PROJECT/global/forwardingRules/openrun-fr" | python3 -c "import sys,json;print(json.load(sys.stdin).get('IPAddress',''))" 2>/dev/null)
  [ -n "$LB_IP" ] && break
  echo "LB(openrun-fr) 아직 없음 — 15초 후 재시도"; sleep 15
done
echo "cloudflared → http://$LB_IP 로 Quick Tunnel 시작"
exec cloudflared tunnel --no-autoupdate --url "http://$LB_IP"
CFRUN
chmod +x /opt/cloudflared-run.sh

cat > /etc/systemd/system/cloudflared.service <<'CFUNIT'
[Unit]
Description=cloudflared quick tunnel -> GCP LB (openrun)
After=network.target
[Service]
ExecStart=/opt/cloudflared-run.sh
Restart=always
RestartSec=5
[Install]
WantedBy=multi-user.target
CFUNIT
systemctl daemon-reload
systemctl enable --now cloudflared
set -e

echo "cloudflared 설정 완료 (공개 URL은 journalctl -u cloudflared 에서 확인)"

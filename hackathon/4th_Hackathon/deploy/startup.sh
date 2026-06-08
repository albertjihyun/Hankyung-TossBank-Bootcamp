#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# 오픈런 MIG 인스턴스 부팅 스크립트 (DEPLOY.md §7과 동일 — 버전관리되는 정본)
#
# 매니지드 인스턴스 그룹(MIG)의 각 인스턴스가 부팅할 때 실행된다.
# - jar  : 비공개 GCS 버킷에서 인스턴스 서비스계정 토큰으로 인증 다운로드
# - 비번 : Secret Manager에서 인증 조회 (메타데이터 평문 금지)
# - DB IP / 버킷명 : 인스턴스 메타데이터(db-ip, jar-bucket)에서 읽음
#
# 사용: gcloud compute instance-templates create openrun-tpl \
#         --service-account=$SA --scopes=cloud-platform \
#         --metadata-from-file startup-script=deploy/startup.sh \
#         --metadata db-ip=$DB_IP,jar-bucket=$BUCKET
# ─────────────────────────────────────────────────────────────────────────────
set -e
exec > /var/log/openrun-startup.log 2>&1

M="http://metadata.google.internal/computeMetadata/v1"
H="Metadata-Flavor: Google"
PROJECT=$(curl -s -H "$H" "$M/project/project-id")
DB_IP=$(curl -s -H "$H" "$M/instance/attributes/db-ip")
BUCKET=$(curl -s -H "$H" "$M/instance/attributes/jar-bucket")

apt-get update
apt-get install -y openjdk-21-jdk curl python3

# 인스턴스 서비스계정 액세스 토큰 (IAM이 권한 제어)
TOKEN=$(curl -s -H "$H" "$M/instance/service-accounts/default/token" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

mkdir -p /opt/openrun
# 비공개 버킷에서 jar (인증된 Storage API).
# DB VM이 빌드·업로드를 끝낼 때까지 jar이 없을 수 있으므로 성공할 때까지 재시도 → 부팅 순서에 무관하게 자가수렴.
until curl -fs -H "Authorization: Bearer $TOKEN" -o /opt/openrun/openrun.jar "https://storage.googleapis.com/storage/v1/b/${BUCKET}/o/openrun.jar?alt=media"; do
  echo "jar 아직 준비 안 됨 — 10초 후 재시도"; sleep 10
done
# Secret Manager에서 DB 비번
DB_PW=$(curl -s -H "Authorization: Bearer $TOKEN" "https://secretmanager.googleapis.com/v1/projects/${PROJECT}/secrets/openrun-db-pw/versions/latest:access" | python3 -c "import sys,json,base64;print(base64.b64decode(json.load(sys.stdin)['payload']['data']).decode())")

cat > /opt/openrun/openrun.env <<EOF
DB_USER=openrun
DB_PASSWORD=$DB_PW
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:mariadb://$DB_IP:3306/openrun?serverTimezone=Asia/Seoul
EOF
chmod 600 /opt/openrun/openrun.env

cat > /etc/systemd/system/openrun.service <<'UNIT'
[Unit]
Description=Openrun app
After=network.target
[Service]
EnvironmentFile=/opt/openrun/openrun.env
ExecStart=/usr/bin/java -Xms128m -Xmx512m -jar /opt/openrun/openrun.jar --server.port=8080
Restart=always
RestartSec=3
[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now openrun

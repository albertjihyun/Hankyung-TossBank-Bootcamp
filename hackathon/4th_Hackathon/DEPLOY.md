# DEPLOY.md — GCP 배포 가이드 (오픈런)

이 문서는 [ARCHITECTURE.md §3 "이번"](ARCHITECTURE.md) 열을 그대로 구현한다:
**매니지드 글로벌 로드밸런서 + 리저널(멀티존) MIG + 단일 MariaDB VM**, 그리고 보안 3종 —
**Secret Manager(비번)·비공개 버킷(jar)·TLS**.

```
[사용자] ─HTTPS→ Global LB (단일 IP, Google 관리·자체 HA, 헬스체크 게이팅)
                    └─ Backend Service ─→ Regional MIG (앱 인스턴스, a/b/c 존 분산 + 오토힐)
                                               │  부팅 시: 비공개 버킷에서 jar(인증) +
                                               │           Secret Manager에서 DB 비번
                                               ▼
                                        openrun-db VM : MariaDB (데이터 + SPRING_SESSION)
```
> **왜 이 구조인가**는 [ARCHITECTURE.md §1·§4](ARCHITECTURE.md). 핵심: 앱은 무상태 가축(cattle), 상태·정합성은 공유 DB, 비밀/산출물은 인증 경유.
> 단순한 **단일 VM 경로(systemd×2 + Nginx)** 도 끝의 [부록 A](#부록-a--단순-경로-단일-vm--systemd2--nginx)에 있다.

명령은 전부 **Cloud Shell**(콘솔 우측 상단 `>_`)에서 실행한다. *VM 안(SSH)에서 gcloud 리소스를 만들면 scope 오류가 난다 — 리소스 생성은 항상 Cloud Shell.*

> ⚠️ **비용**: LB(포워딩 룰)·VM은 시간당 과금. 끝나면 [§12 정리](#12-정리-teardown)로 꼭 삭제.
> 📌 **붙여넣기 팁**: 여러 줄/주석 블록은 Cloud Shell에서 잘 깨진다. **한 줄씩** 붙여넣기를 권장.

---

## 0. 준비 — API 사용 설정 + 변수

```bash
gcloud services enable compute.googleapis.com secretmanager.googleapis.com storage.googleapis.com
```
변수(한 줄로 붙여넣기):
```bash
export REGION=asia-northeast3; export ZONE=asia-northeast3-a; export PROJECT=$(gcloud config get-value project); export BUCKET=${PROJECT}-openrun; echo "PROJECT=$PROJECT REGION=$REGION BUCKET=$BUCKET"
```
> 셸을 새로 열면 변수가 날아간다. "could not parse resource []" 가 보이면 이 줄을 다시 실행.

---

## 1. DB VM 생성 + MariaDB + 스키마 부트스트랩

### 1-1. DB VM 만들기 (태그 `openrun-db`)
```bash
gcloud compute instances create openrun-db --zone=$ZONE --machine-type=e2-small --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud --boot-disk-size=20GB --tags=openrun-db
```
DB의 **내부 IP** 저장:
```bash
export DB_IP=$(gcloud compute instances describe openrun-db --zone=$ZONE --format='get(networkInterfaces[0].networkIP)'); echo "DB_IP=$DB_IP"
```

### 1-2. MariaDB·Java 설치 + DB/계정 생성 (DB VM 안에서)
```bash
gcloud compute ssh openrun-db --zone=$ZONE
```
접속되면(`...@openrun-db`) 아래 실행 — `<DB_PW>` 를 본인 비번으로:
```bash
sudo apt-get update && sudo apt-get install -y mariadb-server openjdk-21-jdk git
# VPC 내부에서 접속 허용
sudo sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mariadb.conf.d/50-server.cnf
sudo systemctl restart mariadb
# DB + 계정 (원격 접속용 @'%')
sudo mysql -e "CREATE DATABASE IF NOT EXISTS openrun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'openrun'@'%' IDENTIFIED BY '<DB_PW>'; GRANT ALL ON openrun.* TO 'openrun'@'%'; FLUSH PRIVILEGES;"
```

### 1-3. 스키마 + 시드 1회 생성 (prod=validate라 최초 1회 필요)
같은 DB VM 안에서, jar를 빌드해 **한 번만** `ddl-auto=update`로 기동 → 스키마·시드 생성:
```bash
cd ~ && git clone https://github.com/albertjihyun/Hankyung-TossBank-Bootcamp.git src && cd src/hackathon/4th_Hackathon && chmod +x gradlew && ./gradlew clean bootJar && cp build/libs/openrun-*.jar ~/openrun.jar
DB_USER=openrun DB_PASSWORD='<DB_PW>' SPRING_DATASOURCE_URL='jdbc:mariadb://localhost:3306/openrun' java -jar ~/openrun.jar --spring.profiles.active=prod --spring.jpa.hibernate.ddl-auto=update
```
→ **`Started OpenrunApplication`** 보이면 `Ctrl+C`. 확인:
```bash
mysql -u openrun -p'<DB_PW>' openrun -e "SHOW TABLES;"   # member/event/reservation/SPRING_SESSION 보이면 OK
```
그다음 `exit` 로 Cloud Shell 복귀.
> 빌드 결과 `~/openrun.jar` 는 [§3 버킷 업로드](#3-배포-산출물jar--비공개-버킷)에서 다시 쓴다.

---

## 2. 비밀관리 — DB 비번을 Secret Manager에 (평문 메타데이터 금지)
```bash
printf '<DB_PW>' | gcloud secrets create openrun-db-pw --data-file=-
```
확인:
```bash
gcloud secrets versions access latest --secret=openrun-db-pw; echo
```

---

## 3. 배포 산출물(jar) — 비공개 버킷
```bash
gcloud storage buckets create gs://$BUCKET --location=$REGION --uniform-bucket-level-access
```
DB VM에 있는 jar를 Cloud Shell로 가져와 업로드(공개 설정 **안 함** = 비공개 유지):
```bash
gcloud compute scp openrun-db:~/openrun.jar ./openrun.jar --zone=$ZONE
gcloud storage cp openrun.jar gs://$BUCKET/openrun.jar
```

---

## 4. 앱 인스턴스용 서비스 계정 (최소 권한 IAM)
```bash
gcloud iam service-accounts create openrun-sa --display-name="Openrun app instances"
export SA=openrun-sa@${PROJECT}.iam.gserviceaccount.com; echo "SA=$SA"
```
비밀 읽기 + 버킷 읽기만 부여:
```bash
gcloud secrets add-iam-policy-binding openrun-db-pw --member="serviceAccount:$SA" --role=roles/secretmanager.secretAccessor
```
```bash
gcloud storage buckets add-iam-policy-binding gs://$BUCKET --member="serviceAccount:$SA" --role=roles/storage.objectViewer
```

---

## 5. 방화벽 (2개)
```bash
gcloud compute firewall-rules create openrun-allow-lb-hc --network=default --direction=INGRESS --action=ALLOW --rules=tcp:8080 --source-ranges=35.191.0.0/16,130.211.0.0/22 --target-tags=openrun-app
```
```bash
gcloud compute firewall-rules create openrun-allow-db --network=default --direction=INGRESS --action=ALLOW --rules=tcp:3306 --source-tags=openrun-app --target-tags=openrun-db
```
> 8080은 **LB·헬스체크 대역**에서만, 3306은 **앱 인스턴스에서 DB로만**. 공인망 노출 없음.

---

## 6. 헬스체크 (LB + 오토힐 공용)
```bash
gcloud compute health-checks create http openrun-hc --port=8080 --request-path=/actuator/health --check-interval=10s --timeout=5s --healthy-threshold=2 --unhealthy-threshold=3
```

---

## 7. 부팅 스크립트 + 인스턴스 템플릿

부팅 시 인스턴스가 **인증 토큰(메타데이터 서버)** 으로 비공개 버킷에서 jar를 받고, Secret Manager에서 DB 비번을 읽는다. **터미널 붙여넣기로 깨지기 쉬우니 편집기로 작성**:
```bash
cloudshell edit startup.sh
```
→ 편집기에 아래를 붙여넣고 저장(Ctrl+S):
```bash
#!/bin/bash
set -e
exec > /var/log/openrun-startup.log 2>&1
M="http://metadata.google.internal/computeMetadata/v1"
H="Metadata-Flavor: Google"
PROJECT=$(curl -s -H "$H" "$M/project/project-id")
DB_IP=$(curl -s -H "$H" "$M/instance/attributes/db-ip")
BUCKET=$(curl -s -H "$H" "$M/instance/attributes/jar-bucket")

apt-get update
apt-get install -y openjdk-21-jdk curl python3

TOKEN=$(curl -s -H "$H" "$M/instance/service-accounts/default/token" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

mkdir -p /opt/openrun
# 비공개 버킷에서 jar (인증된 Storage API)
curl -s -H "Authorization: Bearer $TOKEN" -o /opt/openrun/openrun.jar "https://storage.googleapis.com/storage/v1/b/${BUCKET}/o/openrun.jar?alt=media"
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
```
검증:
```bash
bash -n startup.sh && echo "문법 OK"
```
인스턴스 템플릿 생성 (SA 연결 + cloud-platform 스코프 → 권한은 IAM이 제어):
```bash
gcloud compute instance-templates create openrun-tpl --machine-type=e2-small --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud --tags=openrun-app --service-account=$SA --scopes=cloud-platform --metadata-from-file startup-script=startup.sh --metadata db-ip=$DB_IP,jar-bucket=$BUCKET
```
> 비번·jar를 메타데이터에 **평문으로 넣지 않는다**. 메타데이터엔 위치(DB IP·버킷명)만, 실제 비밀은 런타임에 인증으로 가져온다.

---

## 8. 리저널 MIG (멀티존 2대 + 오토힐)
한 줄씩:
```bash
gcloud compute instance-groups managed create openrun-mig --template=openrun-tpl --size=2 --region=$REGION
```
```bash
gcloud compute instance-groups managed set-named-ports openrun-mig --named-ports=http:8080 --region=$REGION
```
```bash
gcloud compute instance-groups managed update openrun-mig --region=$REGION --health-check=openrun-hc --initial-delay=120
```

---

## 9. 로드밸런서 (HTTP → 이후 TLS)
한 줄씩:
```bash
gcloud compute backend-services create openrun-bes --global --protocol=HTTP --port-name=http --health-checks=openrun-hc
```
```bash
gcloud compute backend-services add-backend openrun-bes --global --instance-group=openrun-mig --instance-group-region=$REGION
```
```bash
gcloud compute url-maps create openrun-um --default-service=openrun-bes
```
```bash
gcloud compute target-http-proxies create openrun-proxy --url-map=openrun-um
```
```bash
gcloud compute forwarding-rules create openrun-fr --global --target-http-proxy=openrun-proxy --ports=80
```
LB IP 확인:
```bash
export LB_IP=$(gcloud compute forwarding-rules describe openrun-fr --global --format='get(IPAddress)'); echo "http://$LB_IP"
```

### 9-TLS. HTTPS 붙이기 (도메인 필요)
**옵션 A — GCP 관리형 인증서(권장):**
```bash
gcloud compute ssl-certificates create openrun-cert --global --domains=YOUR_DOMAIN
gcloud compute target-https-proxies create openrun-https-proxy --url-map=openrun-um --ssl-certificates=openrun-cert
gcloud compute forwarding-rules create openrun-fr-https --global --target-https-proxy=openrun-https-proxy --ports=443
```
그다음 도메인 DNS의 **A 레코드 → `$LB_IP`**. 인증서는 도메인 검증 후 자동 발급(수 분~1시간). 상태:
```bash
gcloud compute ssl-certificates describe openrun-cert --global --format='get(managed.status)'   # ACTIVE 되면 완료
```
**옵션 B — Cloudflare:** A 레코드 → `$LB_IP`, Proxy ON, SSL=Full. (사용자↔CF는 HTTPS, CF↔LB는 80)

---

## 10. ⭐ 검증 (헬스 · 로드밸런싱 · Failover · 세션)

부팅·기동에 3~4분. 먼저 HEALTHY 대기:
```bash
gcloud compute instance-groups managed list-instances openrun-mig --region=$REGION
```
`HEALTH_STATE: HEALTHY` 2개 + 백엔드 확인:
```bash
gcloud compute backend-services get-health openrun-bes --global --format='value(status.healthStatus[].healthState)'   # HEALTHY;HEALTHY
```
브라우저에서 **`http://$LB_IP`** → 오픈런 화면(`user1/user123`).

**(A) 분산** — 한 인스턴스를 내려도 다른 쪽이 받는지로 분산/우회 확인:
```bash
for i in $(seq 1 12); do curl -s -o /dev/null -w "%{http_code} " http://$LB_IP/; done; echo   # 전부 200
```

**(B) Failover + 오토힐** — 인스턴스 하나를 통째로 삭제해도 무중단 + 자동 재생성:
```bash
NAME=$(gcloud compute instance-groups managed list-instances openrun-mig --region=$REGION --format='value(instance)' | head -1 | xargs basename)
gcloud compute instance-groups managed delete-instances openrun-mig --region=$REGION --instances=$NAME
for i in $(seq 1 15); do curl -s -o /dev/null -w "%{http_code} " http://$LB_IP/; done; echo   # 그래도 200
gcloud compute instance-groups managed list-instances openrun-mig --region=$REGION            # 잠시 뒤 다시 2개로 복구
```

**(C) 세션 유지** — `user1` 로그인 후 인스턴스 하나 삭제해도 로그인 유지(세션이 DB에 있음). DB에서 직접 확인:
```bash
gcloud compute ssh openrun-db --zone=$ZONE --command="mysql -u openrun -p'<DB_PW>' openrun -e 'SELECT COUNT(*) AS sessions FROM SPRING_SESSION;'"
```

---

## 11. 트러블슈팅

| 증상 | 원인 / 해결 |
|------|------|
| `insufficient authentication scopes` | gcloud를 **VM 안에서** 실행함 → **Cloud Shell**에서 실행 |
| `could not parse resource []` | 변수(`$REGION` 등)가 빔 → [§0 변수](#0-준비--api-사용-설정--변수) 다시 |
| 인스턴스 계속 `TIMEOUT/UNHEALTHY` (3~4분 지나도) | 새 인스턴스 SSH → `sudo cat /var/log/openrun-startup.log` |
| 로그에 `curl: Malformed URL` | `jar-bucket` 메타데이터 빔 → 템플릿 재생성(`$BUCKET` 채워서) |
| 로그에 jar 다운로드 403 | SA에 `storage.objectViewer` 미부여 / 스코프 누락 → [§4](#4-앱-인스턴스용-서비스-계정-최소-권한-iam) |
| 로그에 Secret 403 | SA에 `secretmanager.secretAccessor` 미부여 → [§4](#4-앱-인스턴스용-서비스-계정-최소-권한-iam) |
| 로그에 `Access denied for user 'openrun'` | Secret 비번 ≠ DB 계정 비번. §2 secret과 §1-2 GRANT 비번 일치 확인 |
| 로그에 `Communications link failure` | DB 네트워크 → §1-2 `bind-address=0.0.0.0` 재시작 / §5 `openrun-allow-db` 방화벽 |
| 부팅 직후 `Schema validation failed` | 스키마 미생성 → §1-3 1회 부트스트랩 안 함 |
| LB만 UNHEALTHY (인스턴스는 UP) | §5 `openrun-allow-lb-hc`(8080←HC 대역) 누락/오타 |
| Cloudflare 522 | 오리진(LB) 미응답 → LB IP로 직접 `curl` 200인지, 백엔드 HEALTHY인지 |

---

## 12. 정리 (teardown) — 비용 차단, 의존성 역순
```bash
gcloud compute forwarding-rules delete openrun-fr-https --global -q 2>/dev/null
gcloud compute target-https-proxies delete openrun-https-proxy -q 2>/dev/null
gcloud compute ssl-certificates delete openrun-cert --global -q 2>/dev/null
gcloud compute forwarding-rules delete openrun-fr --global -q
gcloud compute target-http-proxies delete openrun-proxy -q
gcloud compute url-maps delete openrun-um -q
gcloud compute backend-services delete openrun-bes --global -q
gcloud compute instance-groups managed delete openrun-mig --region=$REGION -q
gcloud compute instance-templates delete openrun-tpl -q
gcloud compute health-checks delete openrun-hc -q
gcloud compute firewall-rules delete openrun-allow-lb-hc openrun-allow-db -q
gcloud storage rm -r gs://$BUCKET
gcloud secrets delete openrun-db-pw -q
gcloud iam service-accounts delete $SA -q
gcloud compute instances delete openrun-db --zone=$ZONE -q
```

---

## 부록 A — 단순 경로 (단일 VM · systemd×2 + Nginx)

데모/면접에서 **다중화·Failover·세션 공유**를 한 VM으로 빠르게 보여줄 때. (LB·MIG 없이도 증명 가능)

```bash
# VM 1대(Ubuntu 24.04, HTTP 허용) 안에서
sudo apt-get update && sudo apt-get install -y openjdk-21-jdk mariadb-server nginx git
# DB
sudo mysql -e "CREATE DATABASE openrun CHARACTER SET utf8mb4; CREATE USER 'openrun'@'localhost' IDENTIFIED BY '<DB_PW>'; GRANT ALL ON openrun.* TO 'openrun'@'localhost'; FLUSH PRIVILEGES;"
# 빌드
git clone https://github.com/albertjihyun/Hankyung-TossBank-Bootcamp.git ~/src && cd ~/src/hackathon/4th_Hackathon && chmod +x gradlew && ./gradlew clean bootJar && mkdir -p ~/app && cp build/libs/openrun-*.jar ~/app/openrun.jar
# env
printf 'DB_USER=openrun\nDB_PASSWORD=<DB_PW>\nSPRING_PROFILES_ACTIVE=prod\n' > ~/app/openrun.env && chmod 600 ~/app/openrun.env
# 최초 스키마
cd ~/app && set -a; source openrun.env; set +a; java -jar openrun.jar --spring.jpa.hibernate.ddl-auto=update   # Started 뜨면 Ctrl+C
```
systemd 템플릿(`User`/경로는 본인 계정으로):
```ini
# /etc/systemd/system/openrun@.service
[Unit]
After=network.target mariadb.service
[Service]
User=YOUR_USER
EnvironmentFile=/home/YOUR_USER/app/openrun.env
ExecStart=/usr/bin/java -Xmx384m -jar /home/YOUR_USER/app/openrun.jar --server.port=%i
Restart=always
[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl daemon-reload && sudo systemctl enable --now openrun@8080 openrun@8081
```
Nginx LB (`add_header X-Upstream $upstream_addr;` 로 분산 가시화):
```nginx
# /etc/nginx/sites-available/openrun
upstream openrun { server 127.0.0.1:8080 max_fails=2 fail_timeout=5s; server 127.0.0.1:8081 max_fails=2 fail_timeout=5s; }
server { listen 80; server_name _;
  location / { proxy_pass http://openrun; proxy_set_header Host $host; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; add_header X-Upstream $upstream_addr; } }
```
```bash
sudo ln -sf /etc/nginx/sites-available/openrun /etc/nginx/sites-enabled/openrun && sudo rm -f /etc/nginx/sites-enabled/default && sudo nginx -t && sudo systemctl restart nginx
```
검증:
```bash
for i in $(seq 1 6); do curl -sI localhost/ | grep -i x-upstream; done   # 8080/8081 번갈아 = 분산
sudo systemctl stop openrun@8080                                          # Failover
for i in $(seq 1 8); do curl -s -o /dev/null -w "%{http_code} " localhost/; done; echo   # 전부 200
sudo systemctl start openrun@8080
```
> 단일 VM은 **그 VM이 SPOF**다. 진짜 다중화는 본문(매니지드 LB + 멀티존 MIG). 둘의 비교가 [ARCHITECTURE.md §3](ARCHITECTURE.md).

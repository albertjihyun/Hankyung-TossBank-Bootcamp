#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# 오픈런 전체 인프라 프로비저닝 (GCP, 매니지드 LB + 멀티존 MIG + MariaDB VM)
# ARCHITECTURE.md §3 "이번" 열을 코드로 박제 — clone 한 번으로 어디서든 재현.
#
# 사용 (Cloud Shell):
#   export DB_PW='강한비밀번호'          # 비밀은 git에 없음 → 실행 시 주입(필수)
#   ./deploy/provision.sh
# 선택 환경변수: REGION(기본 asia-northeast3) / REPO_URL / BUCKET
#
# 멱등: 이미 있는 리소스는 건너뜀(재실행 안전). 깨끗한 상태에서 돌리는 걸 권장.
# 전제(어디서 pull하든 필요): GCP 프로젝트+결제, gcloud 인증, DB_PW 주입.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

PROJECT="${PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-asia-northeast3}"
ZONE="${ZONE:-${REGION}-a}"
REPO_URL="${REPO_URL:-https://github.com/albertjihyun/Hankyung-TossBank-Bootcamp.git}"
BUCKET="${BUCKET:-${PROJECT}-openrun-jar}"
SA="openrun-sa@${PROJECT}.iam.gserviceaccount.com"
HERE="$(cd "$(dirname "$0")" && pwd)"
: "${DB_PW:?DB_PW 환경변수를 설정하세요  예) export DB_PW='강한비번'}"

echo "==> [0/9] APIs 활성화"
gcloud services enable compute.googleapis.com secretmanager.googleapis.com storage.googleapis.com

echo "==> [1/9] Secret Manager (DB 비번)"
gcloud secrets describe openrun-db-pw >/dev/null 2>&1 \
  || printf '%s' "$DB_PW" | gcloud secrets create openrun-db-pw --data-file=-

echo "==> [2/9] 서비스 계정 + 최소권한 IAM"
if ! gcloud iam service-accounts describe "$SA" >/dev/null 2>&1; then
  gcloud iam service-accounts create openrun-sa --display-name="Openrun app + db"
  echo "    새 SA 전파 대기..."; sleep 15   # IAM eventual consistency: 바인딩 전 전파 대기
fi
# 바인딩(전파 지연으로 'does not exist' 날 수 있어 재시도)
bind_retry() {  # $1=명령 설명, 나머지=gcloud 인자
  local desc="$1"; shift
  for i in 1 2 3 4 5 6; do
    "$@" >/dev/null 2>&1 && return 0
    echo "    ($desc 전파 대기, 재시도 $i/6)"; sleep 10
  done
  echo "    ($desc 최종 실패)"; return 1
}
bind_retry "secret 권한" gcloud secrets add-iam-policy-binding openrun-db-pw --member="serviceAccount:$SA" --role=roles/secretmanager.secretAccessor
# DB VM의 cloudflared가 LB(openrun-fr) IP를 읽을 수 있도록 읽기 권한 (compute.viewer)
bind_retry "compute.viewer" gcloud projects add-iam-policy-binding "$PROJECT" --member="serviceAccount:$SA" --role=roles/compute.viewer

echo "==> [3/9] 비공개 버킷 (jar)"
gcloud storage buckets describe "gs://$BUCKET" >/dev/null 2>&1 \
  || gcloud storage buckets create "gs://$BUCKET" --location="$REGION" --uniform-bucket-level-access
# objectAdmin: DB VM이 업로드(create) + 앱 인스턴스가 읽기(read)
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" --member="serviceAccount:$SA" --role=roles/storage.objectAdmin >/dev/null

echo "==> [4/9] 방화벽 (LB/HC→8080, 앱→DB 3306)"
gcloud compute firewall-rules describe openrun-allow-lb-hc >/dev/null 2>&1 \
  || gcloud compute firewall-rules create openrun-allow-lb-hc --direction=INGRESS --action=ALLOW --rules=tcp:8080 --source-ranges=35.191.0.0/16,130.211.0.0/22 --target-tags=openrun-app
gcloud compute firewall-rules describe openrun-allow-db >/dev/null 2>&1 \
  || gcloud compute firewall-rules create openrun-allow-db --direction=INGRESS --action=ALLOW --rules=tcp:3306 --source-tags=openrun-app --target-tags=openrun-db

echo "==> [5/9] 헬스체크"
gcloud compute health-checks describe openrun-hc >/dev/null 2>&1 \
  || gcloud compute health-checks create http openrun-hc --port=8080 --request-path=/actuator/health --check-interval=10s --timeout=5s --healthy-threshold=2 --unhealthy-threshold=3

echo "==> [6/9] DB VM (MariaDB+스키마+시드, jar 빌드·업로드까지 자동)"
gcloud compute instances describe openrun-db --zone="$ZONE" >/dev/null 2>&1 \
  || gcloud compute instances create openrun-db --zone="$ZONE" --machine-type=e2-small \
       --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud --boot-disk-size=20GB \
       --tags=openrun-db --service-account="$SA" --scopes=cloud-platform \
       --metadata "jar-bucket=$BUCKET,repo-url=$REPO_URL" \
       --metadata-from-file "startup-script=$HERE/db-startup.sh"
DB_IP=$(gcloud compute instances describe openrun-db --zone="$ZONE" --format='get(networkInterfaces[0].networkIP)')
echo "    DB_IP=$DB_IP"

echo "==> [7/9] 앱 인스턴스 템플릿"
gcloud compute instance-templates describe openrun-tpl >/dev/null 2>&1 \
  || gcloud compute instance-templates create openrun-tpl --machine-type=e2-small \
       --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
       --tags=openrun-app --service-account="$SA" --scopes=cloud-platform \
       --metadata "db-ip=$DB_IP,jar-bucket=$BUCKET" \
       --metadata-from-file "startup-script=$HERE/startup.sh"

echo "==> [8/9] 리저널 MIG (멀티존 2대 + 오토힐)"
gcloud compute instance-groups managed describe openrun-mig --region="$REGION" >/dev/null 2>&1 \
  || gcloud compute instance-groups managed create openrun-mig --template=openrun-tpl --size=2 --region="$REGION"
gcloud compute instance-groups managed set-named-ports openrun-mig --named-ports=http:8080 --region="$REGION"
gcloud compute instance-groups managed update openrun-mig --region="$REGION" --health-check=openrun-hc --initial-delay=180

echo "==> [9/9] 글로벌 HTTP 로드밸런서"
gcloud compute backend-services describe openrun-bes --global >/dev/null 2>&1 \
  || gcloud compute backend-services create openrun-bes --global --protocol=HTTP --port-name=http --health-checks=openrun-hc
gcloud compute backend-services add-backend openrun-bes --global --instance-group=openrun-mig --instance-group-region="$REGION" 2>/dev/null || true
gcloud compute url-maps describe openrun-um >/dev/null 2>&1 \
  || gcloud compute url-maps create openrun-um --default-service=openrun-bes
gcloud compute target-http-proxies describe openrun-proxy >/dev/null 2>&1 \
  || gcloud compute target-http-proxies create openrun-proxy --url-map=openrun-um
gcloud compute forwarding-rules describe openrun-fr --global >/dev/null 2>&1 \
  || gcloud compute forwarding-rules create openrun-fr --global --target-http-proxy=openrun-proxy --ports=80

LB_IP=$(gcloud compute forwarding-rules describe openrun-fr --global --format='get(IPAddress)')
echo ""
echo "✅ 프로비저닝 명령 완료. DB 빌드+인스턴스 기동+터널 연결에 5~8분 걸립니다."
echo "   접속(직접):    http://$LB_IP"
echo "   공개 HTTPS URL: gcloud compute ssh openrun-db --zone=$ZONE --command=\"sudo journalctl -u cloudflared --no-pager | grep -oE 'https://[a-z0-9-]+\\.trycloudflare\\.com' | tail -1\""
echo "   상태:          gcloud compute backend-services get-health openrun-bes --global --format='value(status.healthStatus[].healthState)'"
echo "   DB로그:        gcloud compute ssh openrun-db --zone=$ZONE --command='sudo tail -n 30 /var/log/openrun-db-startup.log'"

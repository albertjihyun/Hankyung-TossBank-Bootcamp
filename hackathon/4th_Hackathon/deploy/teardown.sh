#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# 오픈런 인프라 전체 삭제 (의존성 역순). 비용 차단용.
# 사용: ./deploy/teardown.sh   (선택: REGION 환경변수)
# 없는 리소스는 무시하고 진행.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
PROJECT="${PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
REGION="${REGION:-asia-northeast3}"
ZONE="${ZONE:-${REGION}-a}"
BUCKET="${BUCKET:-${PROJECT}-openrun-jar}"
SA="openrun-sa@${PROJECT}.iam.gserviceaccount.com"

echo "==> 로드밸런서"
gcloud compute forwarding-rules delete openrun-fr-https --global -q 2>/dev/null || true
gcloud compute target-https-proxies delete openrun-https-proxy -q 2>/dev/null || true
gcloud compute ssl-certificates delete openrun-cert --global -q 2>/dev/null || true
gcloud compute forwarding-rules delete openrun-fr --global -q 2>/dev/null || true
gcloud compute target-http-proxies delete openrun-proxy -q 2>/dev/null || true
gcloud compute url-maps delete openrun-um -q 2>/dev/null || true
gcloud compute backend-services delete openrun-bes --global -q 2>/dev/null || true

echo "==> MIG + 템플릿"
gcloud compute instance-groups managed delete openrun-mig --region="$REGION" -q 2>/dev/null || true
gcloud compute instance-templates delete openrun-tpl -q 2>/dev/null || true

echo "==> 헬스체크 + 방화벽 + DB VM"
gcloud compute health-checks delete openrun-hc -q 2>/dev/null || true
gcloud compute firewall-rules delete openrun-allow-lb-hc openrun-allow-db -q 2>/dev/null || true
gcloud compute instances delete openrun-db --zone="$ZONE" -q 2>/dev/null || true

echo "==> 버킷 + 비밀 + 서비스계정"
gcloud storage rm -r "gs://$BUCKET" 2>/dev/null || true
gcloud secrets delete openrun-db-pw -q 2>/dev/null || true
gcloud iam service-accounts delete "$SA" -q 2>/dev/null || true

echo "✅ teardown 완료. (남은 게 있는지: gcloud compute instances list)"

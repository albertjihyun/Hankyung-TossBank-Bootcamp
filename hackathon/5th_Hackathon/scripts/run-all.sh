#!/usr/bin/env bash
# OLIVE — 백엔드 + 프런트 + cloudflared 터널을 한 번에 띄우고 공개 URL을 출력 (macOS/Linux).
# 사전조건: SETUP.md Phase 1~4 완료(JDK/Node/cloudflared, seed-products.csv + public/products 준비).
# 사용:  bash scripts/run-all.sh        (Ctrl+C 로 전체 종료)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
TUNNEL_LOG="$(mktemp)"
PIDS=()

cleanup() {
  echo ""
  echo "종료 중..."
  for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
  exit 0
}
trap cleanup INT TERM

[ -f "$BACKEND/seed-products.csv" ] || { echo "seed-products.csv 없음. SETUP.md Phase 4 먼저 실행."; exit 1; }

free_port() { lsof -ti:"$1" 2>/dev/null | xargs -r kill -9 2>/dev/null || true; }
free_port 8080; free_port 3000

echo "[1/3] Spring Boot (8080) 기동..."
( cd "$BACKEND" && ./gradlew bootRun ) >/tmp/olive-backend.log 2>&1 &
PIDS+=($!)
for i in $(seq 1 60); do
  if curl -sf -o /dev/null "http://localhost:8080/api/products?size=1"; then echo "    백엔드 OK"; break; fi
  sleep 3
  [ "$i" = 60 ] && { echo "백엔드 기동 실패. /tmp/olive-backend.log 확인"; exit 1; }
done

echo "[2/3] Next.js (3000) 기동..."
[ -d "$FRONTEND/node_modules" ] || ( cd "$FRONTEND" && npm install )
( cd "$FRONTEND" && API_BASE=http://localhost:8080 npm run dev ) >/tmp/olive-frontend.log 2>&1 &
PIDS+=($!)
for i in $(seq 1 40); do
  if curl -sf -o /dev/null "http://localhost:3000/"; then echo "    프런트 OK"; break; fi
  sleep 2
  [ "$i" = 40 ] && { echo "프런트 기동 실패. /tmp/olive-frontend.log 확인"; exit 1; }
done

echo "[3/3] cloudflared 터널 기동..."
cloudflared tunnel --url http://localhost:3000 >"$TUNNEL_LOG" 2>&1 &
PIDS+=($!)
URL=""
for i in $(seq 1 30); do
  URL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$TUNNEL_LOG" | head -1 || true)
  [ -n "$URL" ] && break
  sleep 2
done

echo ""
echo "=============================================="
[ -n "$URL" ] && echo " 공개 URL:  $URL" || echo " 터널 URL 못 찾음. 로그: $TUNNEL_LOG"
echo " 로컬:      http://localhost:3000"
echo " 종료:      이 셸에서 Ctrl+C"
echo "=============================================="

# 프로세스 유지 (Ctrl+C 까지 대기)
wait

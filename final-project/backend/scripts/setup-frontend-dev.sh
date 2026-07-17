#!/usr/bin/env bash
# 프론트엔드 개발자용 원샷 셋업 (Git Bash 전용):
#   Docker(MariaDB+Redis) 기동 → 설정 파일·시크릿 자동 생성 → 스키마+시드 적용
# 사용: bash backend/scripts/setup-frontend-dev.sh   (리포 어디서 실행해도 됨)
# 다시 실행해도 안전 — 이미 만든 설정 파일/데이터는 건드리지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."   # → backend/

say()  { echo "[setup] $*"; }
fail() { echo "[setup][오류] $*" >&2; exit 1; }

# ── 0) 필수 도구 확인 ──────────────────────────────────────────
# docker가 PATH에 없으면 표준 설치 경로에서 찾아본다 (설치 직후 터미널이 PATH 갱신 전인 경우)
if ! command -v docker >/dev/null 2>&1; then
  DOCKER_BIN="/c/Program Files/Docker/Docker/resources/bin"
  if [ -x "$DOCKER_BIN/docker.exe" ]; then
    export PATH="$PATH:$DOCKER_BIN"
    say "docker를 PATH에서 못 찾아 표준 경로 사용: $DOCKER_BIN"
  fi
fi
command -v docker >/dev/null 2>&1 \
  || fail "Docker Desktop이 필요합니다. 설치: winget install Docker.DockerDesktop → 설치 후 Docker Desktop 앱을 한 번 실행"
docker info >/dev/null 2>&1 \
  || fail "Docker가 실행 중이 아닙니다 — Docker Desktop을 켠 뒤 다시 실행하세요."
command -v openssl >/dev/null 2>&1 \
  || fail "openssl이 없습니다. Git Bash에서 실행했는지 확인하세요 (PowerShell/cmd 불가)."

# ── 1) DB 포트 결정: 기존 .env가 있으면 그대로, 없으면 3306 점유 여부로 자동 선택 ──
if [ -f .env ]; then
  DB_PORT=$(grep -E '^DB_PORT=' .env | head -1 | cut -d= -f2 | awk '{print $1}')
  DB_PORT=${DB_PORT:-3306}
  say ".env 이미 존재 — DB_PORT=${DB_PORT} 재사용"
else
  DB_PORT=3306
  if (exec 3<>/dev/tcp/127.0.0.1/3306) 2>/dev/null; then
    exec 3>&- 3<&- || true
    if ! docker ps --format '{{.Names}}' | grep -q '^jarvis-mariadb$'; then
      DB_PORT=3307
      say "3306 포트를 다른 MySQL/MariaDB가 쓰고 있음 → 컨테이너는 ${DB_PORT} 사용"
    fi
  fi
  sed -e "s/^DB_PORT=3306/DB_PORT=${DB_PORT}/" \
      -e "s#^DB_URL=jdbc:mariadb://localhost:3306/jarvis#DB_URL=jdbc:mariadb://localhost:${DB_PORT}/jarvis#" \
      .env.example > .env
  say ".env 생성 (DB_PORT=${DB_PORT})"
fi

# ── 2) application-local.yml 생성 + 시크릿 4종 자동 생성 ──────────
#     항목 구성은 application-local.yml.example과 동일하게 유지할 것 (03 §5)
LOCAL_YML=src/main/resources/application-local.yml
if [ -f "$LOCAL_YML" ]; then
  say "application-local.yml 이미 존재 — 유지"
else
  JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
  STREAM_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null \
               | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n')
  INTERNAL_TOKEN=$(openssl rand -hex 32)
  KID="jarvis-local-$(date +%Y-%m)"
  cat > "$LOCAL_YML" <<EOF
# setup-frontend-dev.sh가 생성한 로컬 설정 (gitignore — 03 §5). 시크릿은 이 머신 전용 랜덤 값.
spring:
  datasource:
    url: jdbc:mariadb://localhost:${DB_PORT}/jarvis
    username: jarvis
    password: jarvis-local
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: ${JWT_SECRET}

app:
  stream-ticket:
    private-key: ${STREAM_KEY}
    kid: ${KID}
  internal:
    token: ${INTERNAL_TOKEN}
  llm:
    base-url: http://localhost:8000 # mock-fastapi 미기동이어도 무해 (통지 실패는 warn 로그만)
    sse-url: http://localhost:8000  # CH-1 응답 llmSseUrl — FE가 직결할 SSE URL
EOF
  say "application-local.yml 생성 (JWT/스트림키/내부토큰 자동 생성)"
fi

# ── 3) MariaDB + Redis 컨테이너 기동 ──────────────────────────
say "MariaDB(${DB_PORT})·Redis(6379) 컨테이너 기동 중..."
docker compose up -d --wait

# ── 4) 스키마 적용 (이미 적용됐으면 건너뜀 — schema.sql은 재실행 불가 DDL) ──
HAS_MEMBER=$(docker exec jarvis-mariadb mariadb -ujarvis -pjarvis-local -N -e "SHOW TABLES LIKE 'member'" jarvis)
if [ -z "$HAS_MEMBER" ]; then
  say "스키마 적용 중 (docs/backend/schema.sql)"
  docker exec -i jarvis-mariadb mariadb -ujarvis -pjarvis-local jarvis < ../docs/backend/schema.sql
else
  say "스키마 이미 적용됨 — 건너뜀"
fi

# ── 5) 시드 적용 (전부 재실행 무해: INSERT IGNORE / NOT EXISTS) ──
for f in scripts/seed-phase1.sql scripts/seed-phase2.sql scripts/seed-phase6.sql; do
  say "시드 적용: $f"
  docker exec -i jarvis-mariadb mariadb -ujarvis -pjarvis-local jarvis < "$f"
done
say "(선택) seed-phase4.sql은 user@jarvis.shop 가입 후 수동 적용 — 파일 헤더 참조"

echo ""
say "✅ 셋업 완료! 다음 단계:"
say "  1. 백엔드 실행:   bash backend/scripts/start-backend.sh"
say "  2. 동작 확인:     http://localhost:8080/actuator/health → {\"status\":\"UP\"}"
say "  3. API 문서:      http://localhost:8080/swagger-ui/index.html"
say "  테스트 계정: seller@jarvis.shop / buyer1~5@jarvis.shop (비밀번호 모두 seller1234)"

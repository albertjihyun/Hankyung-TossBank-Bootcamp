#!/usr/bin/env bash
# 행동 이벤트 DLT 재처리 (08 D11) — DLT에 쌓인 레코드를 원래 토픽으로 되돌려 다시 적재시킨다.
#
# 언제: DLT 경보(BehaviorEventDltListener의 ERROR 로그)를 보고 원인(DB 장애·스키마 불일치 등)을 고친 뒤.
#       원인을 고치기 전에 돌리면 같은 레코드가 3회 재시도 후 DLT로 다시 돌아올 뿐이다(손실은 없다).
#
# 안전한 이유:
#   - 적재가 멱등이다. behavior_events.client_event_id가 UNIQUE이고 컨슈머가 이미 저장된 id를 거른다(02 D35).
#     그래서 같은 레코드를 두 번 되돌려도 행은 하나다.
#   - 전용 컨슈머 그룹(<prefix>dlt-replayer)이 어디까지 되돌렸는지 기억한다. 다시 돌리면 지난번 이후에
#     DLT로 들어온 것만 옮긴다. 처음 돌리면 DLT 보존 기간(7일) 안의 전부를 옮긴다.
#   - DLT 원본은 지우지 않는다(토픽은 보존 기간까지 그대로).
#
# 사용 (브로커가 도는 호스트에서):
#   bash scripts/replay-behavior-dlt.sh            # 운영 (접두어 없음)
#   PREFIX=dev- bash scripts/replay-behavior-dlt.sh  # dev
#   DRY_RUN=1 bash scripts/replay-behavior-dlt.sh  # 옮기지 않고 대기 건수만 본다
#
# 환경 변수:
#   PREFIX     app.kafka.prefix와 같은 값 (기본 빈 값 = 운영)
#   BOOTSTRAP  브로커 주소 (기본 localhost:9092 — 컨테이너 안에서 본 주소)
#   KAFKA_CMD  카프카 CLI를 부르는 앞부분 (기본: docker exec -i jarvis-kafka /opt/kafka/bin)
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash(Windows)가 컨테이너 안 경로(/opt/...)를 윈도 경로로 바꾸지 않게

PREFIX=${PREFIX:-}
BOOTSTRAP=${BOOTSTRAP:-localhost:9092}
KAFKA_CMD=${KAFKA_CMD:-"docker exec -i jarvis-kafka /opt/kafka/bin"}
TOPIC="${PREFIX}behavior-events"
DLT="${TOPIC}-dlt"
GROUP="${PREFIX}dlt-replayer"
SEP=$'\t'

kafka() { local tool=$1; shift; $KAFKA_CMD/"$tool".sh "$@"; }

# 처음 돌리는 그룹은 커밋된 오프셋이 없어 lag가 안 나온다 — 그때는 DLT 전체 크기를 대기 건수로 본다
pending() {
  local lag
  lag=$(kafka kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --describe --group "$GROUP" 2>/dev/null \
        | awk -v t="$DLT" '$2==t && $6 ~ /^[0-9]+$/ {s+=$6} END {print s+0}')
  if [ "$lag" = "0" ] && ! kafka kafka-consumer-groups --bootstrap-server "$BOOTSTRAP" --list 2>/dev/null | grep -qx "$GROUP"; then
    lag=$(kafka kafka-get-offsets --bootstrap-server "$BOOTSTRAP" --topic "$DLT" 2>/dev/null \
          | awk -F: '{s+=$3} END {print s+0}')
  fi
  echo "$lag"
}

echo "[dlt-replay] $DLT → $TOPIC (그룹 $GROUP)"
COUNT=$(pending)
echo "[dlt-replay] 되돌릴 레코드: $COUNT건"
if [ "$COUNT" = "0" ]; then
  exit 0
fi
if [ -n "${DRY_RUN:-}" ]; then
  echo "[dlt-replay] DRY_RUN — 옮기지 않고 끝낸다"
  exit 0
fi

# 키를 살려 옮긴다 — 같은 세션의 이벤트가 같은 파티션으로 가야 순서가 유지된다.
# 값은 한 줄 JSON이다(프로듀서가 JsonSerializer로 보낸다). 10초 동안 새 레코드가 없으면 끝난다.
kafka kafka-console-consumer --bootstrap-server "$BOOTSTRAP" --topic "$DLT" --group "$GROUP" \
    --from-beginning --timeout-ms 10000 \
    --property print.key=true --property key.separator="$SEP" 2>/dev/null \
  | kafka kafka-console-producer --bootstrap-server "$BOOTSTRAP" --topic "$TOPIC" \
    --property parse.key=true --property key.separator="$SEP"

echo "[dlt-replay] 완료 — 남은 대기: $(pending)건. 앱 로그에서 'DLT 적치'가 다시 늘지 않는지 확인한다."

# 아키텍처 결정 기록 (ADR)

Narvis(구 Jarvis) 최종 프로젝트에서 내린 주요 아키텍처 결정의 큐레이션. 원본 결정 로그는 각 서브 레포의 스펙 문서 안에 D-번호로 흩어져 있으며(설계 단계 git 히스토리 2026-07-06~07-18 포함 총 40여 건), 여기에는 그중 문제 → 선택지 → 기준 → 트레이드오프가 가장 선명한 12건을 요약했다. 각 ADR이 원문 경로를 가리키므로 상세는 원문에서 확인한다.

| 번호 | 결정 | 결정일 |
|---|---|---|
| [0001](0001-rds-multi-az-no-read-replica.md) | DB 고가용성은 Multi-AZ로, read replica는 두지 않는다 | 2026-07-08 |
| [0002](0002-llm-no-direct-db-access.md) | LLM 서버는 DB에 직접 접근하지 않는다 — 읽기 전용도 | 2026-07-09 |
| [0003](0003-fastapi-separate-tier-single-instance.md) | FastAPI는 별도 티어로 분리하되 의도적으로 1대만 둔다 | 2026-07-09 |
| [0004](0004-seller-hitl-draft.md) | 판매자 에이전트의 쓰기는 드래프트 + 사람 승인(HITL)으로만 | 2026-07-13~17 |
| [0005](0005-sse-direct-connect.md) | 채팅 SSE를 Spring 패스스루에서 FE↔FastAPI 직결로 전환 | 2026-07-16 |
| [0006](0006-mysql-to-mariadb.md) | 기준 DB를 MySQL 8.x에서 MariaDB 11.x로 전환 | 2026-07-16 |
| [0007](0007-behavior-events-fe-collection.md) | user_event 폐기 — 행동 이벤트는 FE가 수집한다 | 2026-07-17 |
| [0008](0008-stock-modeling-reversals.md) | 재고: 모델링 안 함 → 결제 트랜잭션 내 차감 → 옵션 단위 (2회 번복) | 2026-07-17 / 08-09 |
| [0009](0009-guest-logs-immutable.md) | 게스트 로그는 다시 쓰지 않는다 — 신원 회전과 연결 기록 | 2026-07-31 |
| [0010](0010-access-token-httponly-cookie.md) | AT를 HttpOnly 쿠키로 이전 + 토큰 에포크 킬스위치 | 2026-08-04 |
| [0011](0011-cache-expansion-after-measurement.md) | 캐시는 측정 후에 — 부하테스트가 뒤집은 "캐시 불필요" 판정 | 2026-08-10 |
| [0012](0012-kafka-single-use-case.md) | Kafka는 후보 7개 중 1개에만 — 기각의 기록 | 2026-08-10 |

작성 원칙: 서브 레포(`jarvis-backend/`, `jarvis-ai/`, `jarvis-frontend/`)는 팀 산출물 보존본이므로 수정하지 않고, 이 디렉터리가 요약·색인 층을 맡는다.

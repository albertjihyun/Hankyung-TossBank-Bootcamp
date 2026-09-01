# ADR-0001: DB 고가용성은 Multi-AZ로, read replica는 두지 않는다

- 결정일: 2026-07-08
- 상태: 채택 (데모는 단일 인스턴스, 프로덕션 전환 시 토글 — 코드 영향 0)
- 근거 문서: `jarvis-backend/docs/backend/03-architecture.md` D-분산2

## 맥락

분산 배포 형상을 설계하면서 DB 계층의 확장·가용성 방식을 정해야 했다. 통상적 선택지는 primary + read replica 구성.

## 결정

RDS **Multi-AZ**(장애 조치용 대기 복제본, 단일 DNS 엔드포인트)를 쓰고, **read replica는 두지 않는다**. Redis도 같은 논리로 ElastiCache primary + replica(자동 failover, 단일 엔드포인트).

## 근거와 기각된 대안

- 이 서비스의 병목은 DB 읽기가 아니라 **외부 LLM 대기(FastAPI)** 다. read replica의 존재 이유인 "읽기 부하 분산"이 성립하지 않으므로 도입 기준을 통과하지 못한다.
- HA의 목적을 "읽기 확장"이 아니라 **failover**로 명확히 하면 Multi-AZ가 정확한 도구다.
- 부수 이득: 단일 엔드포인트라 읽기/쓰기 분리가 없고, 복제 지연·read-your-own-writes 문제를 구조적으로 회피한다.

## 트레이드오프·결과

- 읽기 부하가 실제로 커지면 그때 replica를 추가한다 — 앱은 단일 URL만 보므로 코드 변경 없음.
- 이후 부하테스트에서 실제 병목은 read replica가 아니라 캐시로 해소되는 종류(같은 계산의 반복)로 확인됐다 ([ADR-0011](0011-cache-expansion-after-measurement.md)).

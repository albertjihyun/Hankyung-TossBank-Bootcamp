# ADR-0006: 기준 DB를 MySQL 8.x에서 MariaDB 11.x로 전환

- 결정일: 2026-07-16
- 상태: 채택 (문서·방언·커넥터·docker-compose 일괄 전환)
- 근거 문서: 설계 단계 커밋 `a0c9239`; `jarvis-backend/README.md` 기술 스택 표; `docs/backend/02-data-model.md`

## 맥락

초기 설계 문서는 전부 MySQL 8.x 기준이었다 (팀 노션의 시스템 설계도에도 MySQL로 기록되어 남아 있다 — 이 전환이 반영되지 않은 것). 배포 대상 환경에 맞춰 기준 DB를 확정해야 했다.

## 결정

**MariaDB 11.x**로 전환한다. 스키마 설계 변경은 없음을 명시 — 포트 3306, utf8mb4, InnoDB, RDS 지원이 동일하다. 유의점은 MariaDB가 `JSON` 타입을 `LONGTEXT` 별칭으로 다룬다는 것 하나.

## 트레이드오프·결과

- **이득이 현실화된 지점**: 생성 컬럼(generated column) + UNIQUE 조합으로 "기본 배송지는 정확히 1개" 같은 단일 행 제약을 앱 코드가 아닌 **DB가 강제**하게 됐다 (2026-08-11, D44).
- **비용이 현실화된 지점**: MariaDB 예약어 목록이 MySQL과 달라, 네이티브 쿼리 별칭 `lines`가 배포 환경에서만 문법 오류를 내는 장애가 있었다 (2026-08-08, `docs/troubleshooting.md` 1번 사례). 방언 전환은 예약어·함수 차이의 전수 확인이 따라와야 한다는 교훈.

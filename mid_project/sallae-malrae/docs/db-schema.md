# DB 스키마 설계

살래말래의 DB 스키마 설계 근거를 정리한 문서다. 실행 가능한 DDL은 리포 루트의 [schema.sql](../schema.sql)에 있으며, 이 문서는 각 결정의 "왜"를 설명한다. 관련 결정의 전체 맥락은 [adr.md](./adr.md) 참조.

## 핵심 설계 철학

> **결정(decision) 이벤트는 도메인 상태를 바꾸는 핵심 이벤트다. 모든 파생 데이터를 같은 트랜잭션에 묶어 정합성과 응답 성능을 함께 확보한다.**

이 한 문장으로 트랜잭션 설계, 쓰기 시점 갱신(반정규화/계산값 캐시), 인덱스, CHECK/UNIQUE 제약을 모두 묶어 설명할 수 있다.

## 테이블 개요

| 테이블 | 역할 | 주요 제약 |
|---|---|---|
| `users` | 회원 정보 + 인증 상태 + 공유 토큰 + 현재 레벨(계산값 캐시) | email/nickname/share_token UNIQUE. level은 코드 상수와 매핑(FK 없음) |
| `categories` | 카테고리 분류 (6개 시드 고정) | name UNIQUE |
| `items` | 쿨링오프 항목 (도메인 핵심) | CHECK(status ↔ decided_at) 외 2개, 복합 인덱스 3개 |
| `email_logs` | 이메일 발송 시도 기록 | UNIQUE(item_id, type) — 재시도 없음 |
| `user_monthly_stats` | 월별 집계 반정규화 테이블 | UNIQUE(user_id, year, month) — 결정 트랜잭션 내 UPSERT |

```
users (1) ──< (N) items >── (N) (1) categories
items (1) ──< (N) email_logs
users (1) ──< (N) user_monthly_stats
```

`levels` 테이블은 없다(ADR-014). `users.level`은 `/lib/level.js`의 `LEVEL_THRESHOLDS` 상수와 매핑되는 계산값 캐시 컬럼이다.

## 컬럼명 규칙

- PK는 항상 `id` — 테이블 안에서는 단순한 식별자.
- FK는 `<참조테이블 단수>_id` — 다른 테이블의 행을 참조한다는 관행적 신호 (`user_id`, `item_id`, `category_id`).
- `_id` 접미사가 FK가 아닐 때는 쓰지 않는다 — `users.level`이 그 예 (분류값 그 자체, ADR-014).

## 테이블별 주요 결정

### users

- `nickname`만 사용 (별도 name 컬럼 없음).
- `refresh_token`은 평문 저장 금지, bcrypt 해시로 저장 (ADR-005).
- `token_version`을 증가시키면 해당 유저의 모든 RT가 즉시 무효화 — 탈취 감지 시 강제 로그아웃 (ADR-005).
- `share_token`은 `crypto.randomBytes(8).toString('hex')`로 생성한 16자 hex 문자열. Node 내장 모듈이라 외부 의존성이 없고, 64비트 엔트로피로 순차 추측이 불가능하다. 컬럼은 VARCHAR(32)로 여유를 두었다.
- `level`은 단순 분류값 — `LEVEL_THRESHOLDS` 상수의 키 (ADR-014).

### categories

- 6개 고정 시드: 패션/뷰티, 전자기기, 가전/가구, 음식/배달, 취미/여행, 기타.
- 6개 고정이지만 ENUM이 아닌 테이블로 분리 — 추가/이름 변경 시 ALTER TABLE을 피하고, `items.category_id` FK 무결성과 통계 JOIN에 활용.
- 조회용 API 라우트는 없다 — 서버 컴포넌트가 직접 DB 조회 후 props로 전달 (ADR-001 결정 A).

### items (도메인 핵심)

**CHECK 제약** (ADR-004)

```sql
CHECK (
  (status = 'waiting'            AND decided_at IS NULL    ) OR
  (status IN ('bought','passed') AND decided_at IS NOT NULL)
)
```

애플리케이션 버그로 status만 바꾸고 decided_at을 누락하면 DB가 차단한다. `price > 0`, `impulse_score BETWEEN 1 AND 10` CHECK도 함께 둔다.

**인덱스** (ADR-008, 012)

| 인덱스 | 용도 |
|---|---|
| `idx_items_user_status_expire (user_id, status, expire_at, id)` | 위시리스트 목록 조회 + 탭별 카운트 |
| `idx_items_user_decided (user_id, decided_at)` | 월별 집계 쿼리, 구매함/아낌 탭 정렬 |
| `idx_items_expire_status (expire_at, status)` | 알림 스케줄러의 정각 매칭 쿼리 |

**주요 결정**

- `expire_at`은 정각 단위로 저장 — 스케줄러의 매 정각 매칭이 SQL 한 줄로 끝난다 (ADR-006 연계).
- `expire_at` 불변 원칙 — 상태 변경 시 덮어쓰지 않는다. 원본 데이터 보존.
- `image`는 VARCHAR(512) — 오브젝트 스토리지 URL 대응 여유.
- `impulse_score`는 등록 시점 1회만 입력 (MVP 단순화. 확장 시 initial/final 분리 가능한 구조).
- 상태값은 DB에서 영문 ENUM, UI에서 한국어 매핑 — 관심사 분리.

### email_logs

- `UNIQUE (item_id, type)` — 중복 발송을 DB 레벨에서 차단. 코드 로직만으로 막으면 스케줄러 중복 실행(서버 재시작, 배포 등) 시 동시성 이슈로 깨질 수 있다.
- 컬럼명은 `sent_at`이 아닌 `attempted_at` — 성공 여부와 무관하게 시도 시각을 기록한다는 의미.
- 재시도 정책 없음 — 실패 시 status='fail' + error_msg 기록 후 수동 처리. 이 테이블은 "중복 차단 + 디버깅 로그" 두 역할을 동시에 수행한다.
- `ON DELETE CASCADE` — WAITING 항목 삭제 시 발송 로그도 자동 정리 (FK 에러 방지).

### user_monthly_stats

- `UNIQUE (user_id, year, month)` — 결정 트랜잭션 내 `INSERT ... ON DUPLICATE KEY UPDATE`의 UPSERT 키.
- 월별 집계 기준은 `decided_at` — 4월 30일 만료 항목을 5월 1일에 결정하면 5월 통계에 포함된다. 사용자 행동 시점 기준이 직관적이다.
- `saved_amount`, `spent_amount`는 BIGINT UNSIGNED — 누적 금액 오버플로 방지.
- "월별 초기화"가 아닌 "월별 분리 저장" — 매월 새 row가 생기고 이전 row는 유지된다. 전체 누적은 `SUM(전체 row)`, 특정 월은 `WHERE year=? AND month=?`.

## 결정 트랜잭션 통합 처리 (ADR-003)

`PATCH /api/items/:id/status` 한 번의 호출 안에서 세 작업이 하나의 트랜잭션으로 처리된다. 중간 실패 시 전체 롤백.

```sql
BEGIN;

-- 1. items 상태 변경 (FOR UPDATE 행 락 후)
UPDATE items SET status = ?, decided_at = NOW()
WHERE id = ? AND user_id = ?;

-- 2. user_monthly_stats UPSERT (year/month는 decided_at 기준)
INSERT INTO user_monthly_stats (...) VALUES (...)
ON DUPLICATE KEY UPDATE
  passed_count = passed_count + ?, bought_count = bought_count + ?,
  saved_amount = saved_amount + ?, spent_amount = spent_amount + ?;

-- 3. users.level 재계산 (calculateLevel() 호출 후)
UPDATE users SET level = ? WHERE id = ?;

COMMIT;
```

**다층 방어선** (ADR-004): 트랜잭션은 의도된 다중 쿼리의 원자성, CHECK는 코드 버그로 한쪽만 바뀌는 경우 방어, UNIQUE는 동시성·중복 방어. 각자 다른 책임이다.

## 레벨 상수 (DB 시드 아님 — /lib/level.js)

| 레벨 | 이름 | 참은 횟수 | 절약 금액 |
|---|---|---|---|
| 1 | 충동 새싹 | 0회 | 0원 |
| 2 | 절약 입문자 | 5회 | 25,000원 |
| 3 | 참을 인 달인 | 15회 | 75,000원 |
| 4 | 짠돌이 | 30회 | 150,000원 |
| 5 | 전설의 짠돌이 | 50회 | 250,000원 |

두 임계값을 **모두** 만족해야 레벨업(AND 조건). PASSED만 레벨에 기여하고 BOUGHT는 무관하다. 근거는 ADR-014.

## 설계 Q&A

**Q. user_monthly_stats는 정규화 위반 아닌가?**
의도적 반정규화다. 대시보드·공유 페이지 진입마다 월별 집계 조회가 발생하는데, 데이터 누적 시 매번 items 풀스캔 + GROUP BY는 부담이다. 결정은 사용자당 하루 1~2회, 조회는 그보다 훨씬 빈번하므로 쓰기 시점 비용(UPSERT 1회)을 받아들이는 게 합리적이다. 정합성 리스크는 결정 트랜잭션에 UPSERT를 묶어 해소했다.

**Q. users.level도 파생값인데 왜 컬럼으로 저장했나?**
엄밀히는 반정규화가 아닌 계산값 캐시다. levels 테이블 제거(ADR-014) 후에는 애플리케이션 함수(`calculateLevel`)의 계산 결과를 미리 저장한 캐시다. 근거는 동일하다 — 쓰기 시점에 비용을 지불하고 읽기 성능을 확보. 트랜잭션이 깨지면 전체 롤백되므로 stats와 level이 함께 갱신된다.

**Q. 트랜잭션이 무거워지지 않나?**
결정은 단발성 이벤트고, 트랜잭션 내부는 PK 또는 UNIQUE 키 단일 행 갱신 3개라 락 범위가 최소다. 같은 user_id에 동시 결정이 발생할 가능성도 UX상 거의 없다.

**Q. CHECK 제약을 굳이 거는 이유는? 트랜잭션으로 충분하지 않나?**
트랜잭션은 "의도된 로직"을 묶어주지만 "애플리케이션 버그"는 막지 못한다. 코드에서 status='bought'로 바꾸면서 decided_at 세팅을 잊으면 CHECK가 막는다. 트랜잭션은 원자성, CHECK는 도메인 무결성 — 책임이 다르다.

**Q. email_logs에 UNIQUE(item_id, type)을 건 이유는?**
중복 발송을 코드 로직만으로 막으면 동시성 이슈로 깨질 수 있다. 스케줄러가 매 정각 실행되는데 단일 인스턴스 가정이 깨지면(서버 재시작, 배포) 동일 정각 작업이 중복 트리거될 수 있다. DB UNIQUE 제약은 동시성과 무관하게 차단한다.

**Q. 단기 쿨링오프(24시간 이하)에서 before_24h가 안 가는데 의도된 동작인가?**
의도된 정책이다. 스케줄러 쿼리 조건상 24시간 이내 등록 시 발송 시점이 이미 과거라 자연스럽게 매칭되지 않는다. 야식 1시간 참기 같은 단기 사용자에게 등록 직후 알림은 부자연스럽고, expire 알림 1회로 충분하다 (ADR-006).

**Q. levels 테이블이 아예 없는 이유는?**
레벨은 5개 고정, 사용자별로 다르지 않고 추가·제거 불가한 완전한 정적 메타데이터다. DB 테이블을 두면 "레벨 5개"라는 사실이 DB와 코드 두 곳에 존재해 어긋날 리스크가 생긴다. 코드 상수가 단일 진실 원천이다 (ADR-014).

**Q. share_token rotation 시 기존 URL은?**
단순 덮어쓰기로 기존 토큰은 즉시 무효화된다. 무효 토큰 접근은 404, 프론트에서 안내 화면으로 연결한다. 만료 토큰을 보관하지 않아 보안적으로 단순하고 명확하다 (ADR-009).

**Q. categories를 ENUM 대신 테이블로 분리한 이유는?**
카테고리 추가/이름 변경 시 ENUM은 ALTER TABLE(스키마 변경)이 필요하지만 테이블이면 INSERT로 끝난다. 카테고리별 통계 쿼리에서 FK 관계가 더 명확하고, 어드민 확장 시 자연스럽다.

**Q. share_token을 nanoid가 아닌 crypto.randomBytes로 생성한 이유는?**
"팀이 학습한 범위 안에서 견고하게 구현하고 설명 가능한 제어"라는 원칙(ADR-005) 기준이다. `crypto`는 Node 내장 모듈로 외부 의존성이 없고, JWT Secret 생성에도 같은 도구를 쓰므로 같은 종류의 작업을 같은 도구로 처리한다. 16자 hex라 모바일에서 복사·공유에도 부담이 없다.

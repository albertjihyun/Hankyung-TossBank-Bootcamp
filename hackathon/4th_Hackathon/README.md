# 오픈런 (Openrun) — 한정 자리, 먼저 누른 사람이 임자

> 구글폼·인스타DM으로 선착순을 돌려막던 소규모·인디 호스트를 위한 **공정·투명·선착순 예약 도구.**
> 핵심 가설: *Spring의 진짜 가치는 한정 공유 자원에 대한 안전한 동시 접근 제어이며, 그 진가는 인스턴스가 여러 대인 운영 환경에서 드러난다.*

4th Hackathon 산출물. 기획은 [`../4th_Hackathon_기획서.md`](../4th_Hackathon_기획서.md), 기술 명세는 [`../TECH_SPEC.md`](../TECH_SPEC.md)를 단일 진실로 삼았다.

---

## 1. 프로젝트 개요 / 사용 기술

| 구분 | 내용 |
|------|------|
| 주제 | 선착순 한정 행사(팝업·클래스·공연·마켓)의 **공정한 예약·대기·승계** |
| 차별점 | ① 실시간 잔여석 **투명** ② 비관적 락으로 동시 폭주에도 **정확히 정원만** ③ 취소 시 대기자 **자동 승계** |
| 언어/런타임 | Java 21 (LTS) |
| 프레임워크 | Spring Boot 3.3.5 (Web, Security, Data JPA, Validation, Actuator) |
| 뷰 | Thymeleaf (서버사이드 렌더링) + 정적 JS 폴링 |
| DB | MariaDB 10.6+ (운영) / H2 (테스트) |
| 세션 | **Spring Session JDBC** — 다중 인스턴스 세션 공유 |
| 빌드 | Gradle (Groovy DSL), `bootJar` |
| 배포 | GCP VM · systemd ×2 · Nginx 리버스 프록시 · Cloudflare SSL ([DEPLOY.md](DEPLOY.md)) |

### 실행 (로컬)
```bash
# MariaDB 준비 (DB/계정: openrun/openrun) 후
export DB_USER=openrun DB_PASSWORD=openrun
./gradlew bootRun
# http://localhost:8080  — 시드 계정: user1/user123, host1/host123, admin/admin123
```

### 빌드 & 테스트
```bash
./gradlew clean build      # 동시성 통합 테스트 포함
```

---

## 2. 아키텍처 & 계층 구조

레이어드 아키텍처. **Controller → Service → Repository 단방향**, 비즈니스 로직과 트랜잭션 경계는 Service에만.

```
com.hackathon.openrun
├─ domain/       엔티티 + enum (Member, Event, Reservation / Role, Category, EventStatus, ReservationStatus)
├─ repository/   Spring Data JPA (EventRepository.findByIdForUpdate = SELECT … FOR UPDATE)
├─ service/      AuthService, EventService, ReservationService(동시성 핵심), EventScheduler
├─ controller/   Home/Auth/Event/Reservation/Host/Admin + ApiController(폴링 JSON)
├─ dto/          SignupRequest, EventCreateRequest, SeatStatusResponse
├─ security/     MemberPrincipal, CustomUserDetailsService
├─ config/       SecurityConfig, DataInitializer(시드)
└─ exception/    도메인 예외 + GlobalExceptionHandler(@ControllerAdvice)
```

```
[브라우저] ──HTTP──> Controller ──> Service(@Transactional) ──> Repository ──> MariaDB
     ▲  폴링(AJAX, 3s)                         │  비관적 락(FOR UPDATE)
     └── /api/events/{id}/seats (JSON) ────────┘
```

### URL / 권한 라우팅

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/` | all | 행사 피드 (tab·category 필터) |
| GET | `/login`, `/signup` | all | 로그인/가입 뷰 |
| POST | `/signup` | all | 가입 (role=USER/HOST) |
| POST | `/login`, `/logout` | — | Security 폼 로그인/로그아웃 |
| GET | `/events/{id}` | all | 상세 + 카운트다운 + 잔여석 폴링 |
| POST | `/events/{id}/reserve` | USER | **선착순 예약** |
| POST | `/reservations/{id}/cancel` | USER | 취소(+대기 승계) |
| GET | `/me/reservations` | USER | 내 예약/대기 현황 |
| GET | `/host/events`, `/host/events/new`, `POST /host/events` | HOST | 행사 목록/등록 |
| GET | `/host/events/{id}` | HOST | 참가자 현황 |
| GET | `/admin`, `POST /admin/events/{id}/close` | ADMIN | 전체 관리/강제 마감 |
| GET | `/api/events/{id}/seats` | all | 잔여석 JSON (폴링) |

---

## 3. Spring Security 인증/인가

- **인증:** 폼 로그인(`/login`) + 커스텀 `UserDetailsService`(`Member` 기반) + **BCrypt** 비밀번호 해시.
- **세션:** Spring Session JDBC — 세션을 MariaDB `SPRING_SESSION` 테이블에 저장 → 인스턴스 A/B 어디로 라우팅돼도 로그인 유지(추가 인프라 0).
- **인가 매트릭스:**
  - permitAll: `/`, `GET /events/**`, `/login`, `/signup`, `/api/**`, `/css/**`, `/js/**`, `/actuator/health`
  - `authenticated`: `/me/**`, `POST /events/*/reserve`, `/reservations/**`
  - `hasRole('HOST')`: `/host/**` · `hasRole('ADMIN')`: `/admin/**`
- **권한 거부 → 커스텀 403 페이지**(`accessDeniedPage`). CSRF는 기본 활성(폼은 Thymeleaf가 토큰 자동 삽입, `/api/**`는 GET 전용이라 무관).

---

## 4. DB & SQL

### 테이블 (요약 DDL — 전체는 [TECH_SPEC §2](../TECH_SPEC.md))
- `member(id, username UNIQUE, password, nickname, role, created_at)`
- `event(id, host_id FK, title, category, capacity, **reserved_count**, open_at, status, **version**, …)`
- `reservation(id, event_id FK, member_id FK, status, waiting_seq, created_at, **UNIQUE(event_id, member_id)**)`

> **2중 방어선:** 중복 예약은 `reservation` 의 `UNIQUE(event_id, member_id)`, 정원 초과는 아래 비관적 락으로 막는다.

### 핵심 쿼리 — 비관적 락 (`SELECT … FOR UPDATE`)
선착순 정합성의 직렬화 지점. Repository 메서드:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select e from Event e where e.id = :id")
Optional<Event> findByIdForUpdate(@Param("id") Long id);
```
실행되는 SQL:
```sql
SELECT e.* FROM event e WHERE e.id = ? FOR UPDATE;
-- 이 행에 대한 다른 트랜잭션은 커밋까지 대기 → read-modify-write 가 직렬화됨
```

대기 승계(취소 시)에서 사용하는 조회:
```sql
-- 대기열 선두 1명 (가장 작은 waiting_seq)
SELECT * FROM reservation
 WHERE event_id = ? AND status = 'WAITING'
 ORDER BY waiting_seq ASC LIMIT 1;
```

---

## 5. 동시성 제어 실험 (차별화 핵심)

### 시나리오
정원 **100**인 행사에 **동시 예약 300건**. 목표: *정확히 100건 RESERVED, 나머지 200건 WAITING, oversell 0.*

### 락 전략 3단 비교

| 단계 | 방식 | 결과 | 교훈 |
|------|------|------|------|
| **0. 무방비** | 락 없이 `reserved_count++` | **oversell 발생** (100 초과 예약) | 경합은 실재한다 |
| **1. 낙관적 락** | `@Version` + 재시도 | 정합성 OK, 경합 극심 시 재시도 폭증 | 충돌 빈도 낮은 곳에 적합 |
| **2. 비관적 락** | `@Lock(PESSIMISTIC_WRITE)` = `SELECT … FOR UPDATE` | **정합성 OK, 직렬화로 안정** | **선착순엔 비관적 락이 정답** ✅ |

> `Event` 엔티티는 `@Version`(낙관적 락 메타데이터)을 보유하지만, **운영 경로(`ReservationService.reserve`)는 비관적 락**을 채택한다. 선착순은 동일 행에 충돌이 집중되는 전형적 고경합 상황이라, 충돌→롤백→재시도를 반복하는 낙관적 락보다 한 줄로 세우는 비관적 락이 유리하기 때문이다.

### 측정 (자동화 테스트 — [`ReservationConcurrencyTest`](src/test/java/com/hackathon/openrun/service/ReservationConcurrencyTest.java))
`ExecutorService` + `CountDownLatch`로 300스레드를 **동시 출발**시켜 `reserve()`를 호출:

| 검증 | 0단계(무방비) | 2단계(비관적 락, 운영) |
|------|--------------|----------------------|
| `event.reserved_count` | **> 100 (oversell)** | **= 100** |
| RESERVED 행 수 | > 100 | **= 100** |
| WAITING 행 수 | — | **= 200** |
| 최종 상태 | 정합성 붕괴 | `CLOSED`, 정합성 유지 |

```bash
./gradlew test --tests '*ReservationConcurrencyTest'
#  ✅ pessimisticLock_noOversell   — reserved_count == capacity == 100
#  ✅ noLock_oversells             — reserved_count > 100 (락의 필요성 입증)
```
HTTP 레벨 부하는 `ab -n 1000 -c 100`(로그인 세션/CSRF 처리)으로 재현 가능.

### 취소 → 대기 자동 승계
취소도 같은 비관적 락 위에서 처리한다. RESERVED 취소로 좌석이 비면 `WAITING` 선두를 `RESERVED`로 승계 + `reserved_count` 보정 — 좌석이 비는 순간과 신규 예약/승계가 겹쳐도 트랜잭션+락으로 정합성을 보장한다.

---

## 6. 확장 설계 ("실제 대규모라면")

현재는 단일 DB 비관적 락으로 정합성을 단일 지점에 집중시켰다. 트래픽이 더 커지면:

1. **읽기 복제본(Read Replica):** 잔여석 폴링·피드 조회 등 읽기를 복제본으로 분리. 쓰기(예약)는 프라이머리에만. `@Transactional(readOnly=true)` 경로를 라우팅.
2. **큐 기반 비동기 예약:** 예약 요청을 Kafka/SQS에 적재하고 단일(또는 파티션별) 컨슈머가 순차 확정 → DB 락 경합 자체를 제거하고 처리량을 평탄화. 사용자는 "처리 중→확정/대기" 상태를 폴링.
3. **Redis 분산락 이관:** 행사별 `SETNX` 분산락으로 DB 락 점유 시간을 단축. 단, 정합성의 최종 권위는 여전히 DB 제약(UNIQUE)에 둬 락 유실 시에도 oversell 불가.
4. **재고 카운터 분리:** `reserved_count`를 Redis 원자 카운터(`DECR`)로 선차감 후 DB 비동기 반영 — 핫 이벤트의 단일 행 경합 해소.
5. **스케줄러 단일화:** 다중 인스턴스에서 오픈 전이 스케줄러는 ShedLock 등으로 리더 1대만 실행.

---

## 7. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| **Oversell(정원 초과 예약)** | read-check-write 사이 경합 창 | `findByIdForUpdate`로 행 비관적 락 → 직렬화. 테스트로 0단계 재현 후 2단계로 해결 |
| 다중 인스턴스에서 로그아웃됨 | in-memory 세션은 인스턴스마다 별개 | **Spring Session JDBC**로 세션을 공유 DB에 저장(ADR-2) |
| `synchronized`가 안 먹힘 | JVM 락은 인스턴스 내부 한정 | 정합성의 단일 진실인 **DB에 락**(ADR-1) |
| H2 테스트에서 lock timeout | 단일 행 고경합 | 테스트 JDBC URL에 `LOCK_TIMEOUT=20000`, 커넥션 풀 충분히 확보 |
| 취소했는데 대기자 승계 안 됨 | 취소-승계가 별도 트랜잭션이면 경합 | 한 트랜잭션 + 비관적 락 안에서 `cancel`→`promote` 처리 |
| 권한 거부 시 흰 에러 | 기본 403 | `accessDeniedPage("/error/403")` 커스텀 페이지 |
| Cloudflare 522 / failover | 업스트림 다운 | Nginx `max_fails`/`fail_timeout`으로 다른 인스턴스로 우회 ([DEPLOY.md](DEPLOY.md) §8) |

---

### 부록 — 시드 데이터
`DataInitializer`(멱등): `admin/admin123`(ADMIN), `host1·host2/host123`(HOST), `user1~3/user123`(USER), 행사 8개(OPEN 4 + SCHEDULED 4, capacity 5/10/30/100). 이미 데이터가 있으면 스킵.

# 오픈런 (Openrun) — 한정 자리, 먼저 누른 사람이 임자

> 구글폼·인스타DM으로 선착순을 돌려막던 소규모·인디 호스트를 위한 **공정·투명·선착순 예약 도구.**
> 핵심 가설: *Spring의 진짜 가치는 한정 공유 자원에 대한 안전한 동시 접근 제어이며, 그 진가는 인스턴스가 여러 대인 운영 환경에서 드러난다.*

## 제출 정보
- **참가자:** albertjihyun
- **프로젝트:** 오픈런(Openrun) — 선착순 한정 행사 예약·대기열·자동 승계 웹 서비스
- **배포 URL:** `http://<배포-LB-IP>`  *(GCP 글로벌 LB / cloudflared HTTPS — 배포 완료 후 기입)*
- **GitHub:** https://github.com/albertjihyun/Hankyung-TossBank-Bootcamp
- **개발 보고서:** 본 README · **인프라 설계서:** [ARCHITECTURE.md](ARCHITECTURE.md) · **배포 가이드:** [DEPLOY.md](DEPLOY.md)

---

## 1. 프로젝트 개요

- **선택 주제:** 선착순 한정 행사(팝업·클래스·공연·마켓)의 **공정한 예약 · 대기열 · 취소 시 자동 승계**
- **사용 기술:** Java 21 · Spring Boot 3.3 · Spring Security · Spring Data JPA · Thymeleaf · HTML/CSS/JS · Spring Session JDBC · MariaDB · Gradle · GCP(VM·MIG·LB) · Cloudflare
- **차별점:** ① 실시간 잔여석 **투명** ② 비관적 락으로 동시 폭주에도 **정확히 정원만** ③ 취소 시 대기자 **자동 승계**

### 실행 & 빌드
```bash
# 로컬 (MariaDB 준비 후)
export DB_USER=openrun DB_PASSWORD=openrun
./gradlew bootRun        # http://localhost:8080 — 시드: user1/user123, host1/host123, admin/admin123

./gradlew clean build    # 동시성 통합 테스트 포함
```

---

## 2. 스프링 아키텍처 및 서비스 구조

표준 레이어드 아키텍처. **Controller → Service → Repository 단방향**, 비즈니스 로직과 트랜잭션 경계는 **Service에만** 둔다.

```
com.hackathon.openrun
├─ controller/   HTTP 수신 + 뷰 반환 (로직 없음)     Home/Auth/Event/Reservation/Host/Admin/Api
├─ service/      트랜잭션 경계 + 비즈니스 규칙 + 동시성  Auth/Event/Reservation(핵심)/Scheduler
├─ repository/   Spring Data JPA                    Member/Event/Reservation (findByIdForUpdate)
├─ domain/       엔티티 + enum                       Member/Event/Reservation, Role/Category/Status
├─ dto/ · security/ · config/ · exception/
```

### 계층 간 데이터 흐름
```
[브라우저] ──HTTP──▶ Controller ──▶ Service(@Transactional) ──▶ Repository ──▶ MariaDB
     ▲ 폴링(AJAX 3s)                       │ 비관적 락(SELECT … FOR UPDATE)
     └── GET /api/events/{id}/seats (JSON) ┘
```
- **Controller**: 요청/응답·뷰 위임만. 비즈니스 로직·트랜잭션 금지.
- **Service**: `@Transactional` 경계. 예약/취소 등 상태 변경과 동시성 제어가 여기에 집중.
- **Repository**: Spring Data JPA. 비관적 락 쿼리(`findByIdForUpdate`) 포함.
- **Domain**: 상태 변경은 의미 있는 메서드로만(`event.increaseReserved()`, `reservation.promote()`). Setter 금지.

### 주요 URL 및 권한 라우팅
| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/` | 전체 | 행사 피드 (tab·category 필터) |
| GET | `/login`, `/signup` · POST `/signup` | 전체 | 로그인/회원가입 |
| GET | `/events/{id}` | 전체 | 상세 + 카운트다운 + 잔여석 폴링 |
| POST | `/events/{id}/reserve` | **USER** | 선착순 예약 |
| POST | `/reservations/{id}/cancel` | **USER** | 취소(+대기 승계) |
| GET | `/me/reservations` | **USER** | 내 예약/대기 현황 |
| GET/POST | `/host/events**` | **HOST** | 행사 목록/등록/참가자 현황 |
| GET/POST | `/admin/**` | **ADMIN** | 전체 관리·강제 마감 |
| GET | `/api/events/{id}/seats` | 전체 | 잔여석 JSON (폴링) |

---

## 3. Spring Security 인증/인가 설정

- **인증:** 커스텀 `/login` **폼 로그인** + `UserDetailsService`(`Member` 기반) + **BCrypt** 비밀번호 해시.
- **세션 관리:** **Spring Session JDBC** — 세션을 MariaDB `SPRING_SESSION` 테이블에 저장. 인스턴스가 여러 대여도 어디로 라우팅되든 로그인 유지(추가 인프라 0).
- **권한별 접근 제어(인가 매트릭스):**
  - `permitAll`: `/`, `GET /events/**`, `/login`, `/signup`, `/api/**`, `/css/**`, `/js/**`, `/actuator/health`
  - `authenticated`: `/me/**`, `POST /events/*/reserve`, `/reservations/**`
  - `hasRole('HOST')`: `/host/**` · `hasRole('ADMIN')`: `/admin/**`
- 권한 거부 시 **커스텀 403 페이지**(`accessDeniedPage`). 폼은 Thymeleaf가 CSRF 토큰 자동 삽입, `/api/**`는 GET 전용이라 무관.

---

## 4. 데이터베이스 및 SQL 활용

### 사용 테이블 (MariaDB)
| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `member` | id, **username UNIQUE**, password(BCrypt), nickname, role, created_at |
| `event` | id, host_id(FK), title, category, capacity, **reserved_count**, open_at, status, **version**(@Version) |
| `reservation` | id, event_id(FK), member_id(FK), status, waiting_seq, created_at, **UNIQUE(event_id, member_id)** |

> **2중 방어선:** 중복 예약은 `reservation` 의 `UNIQUE(event_id, member_id)` 제약으로, 정원 초과는 아래 비관적 락으로 막는다.

### 주요 SQL — 비관적 락 (`SELECT … FOR UPDATE`)
선착순 정합성의 직렬화 지점. 레이어를 거쳐 CRUD를 처리하는 핵심 쿼리.
```java
// repository — 행을 비관적 쓰기 락으로 조회
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select e from Event e where e.id = :id")
Optional<Event> findByIdForUpdate(@Param("id") Long id);
```
```sql
-- 실제 실행 SQL: 이 행에 대한 다른 트랜잭션은 커밋까지 대기 → read-modify-write 직렬화
SELECT e.* FROM event e WHERE e.id = ? FOR UPDATE;

-- 취소 시 대기열 선두 승계 대상 조회
SELECT * FROM reservation
 WHERE event_id = ? AND status = 'WAITING'
 ORDER BY waiting_seq ASC LIMIT 1;
```
예약/취소/조회(CRUD)는 모두 Service의 트랜잭션 안에서 위 쿼리들과 함께 처리되어 정합성을 보장한다.

---

## 5. 트러블슈팅 (문제 해결 기록)

| 증상 | 원인 | 해결 |
|------|------|------|
| **Oversell(정원 초과 예약)** | read-check-write 사이 경합 창 | `findByIdForUpdate`로 행 비관적 락 → 직렬화. 테스트로 "락 없음→oversell" 재현 후 해결 입증 |
| Thymeleaf 정적 리소스/프래그먼트 오류 | 프래그먼트명 `head`가 `<head>` 태그와 충돌 | 프래그먼트명을 `pagehead`로 변경(태그명 회피) |
| 뷰에서 `LazyInitializationException` | `open-in-view=false`인데 뷰가 `host.nickname` 참조 | 뷰가 쓰는 연관관계를 `@ManyToOne(EAGER)`로 |
| 다중 인스턴스에서 로그아웃됨 | in-memory 세션은 인스턴스마다 별개 | **Spring Session JDBC**로 세션을 공유 DB에 저장 |
| `synchronized`가 안 먹힘 | JVM 락은 인스턴스 내부 한정 | 정합성의 단일 진실인 **DB에 락** |
| 취소했는데 대기자 승계 안 됨 | 취소-승계가 별도 트랜잭션이면 경합 | 한 트랜잭션 + 비관적 락 안에서 `cancel`→`promote` 처리 |
| GCP MIG 인스턴스가 계속 UNHEALTHY | 부팅 미완 / startup 실패 | `/var/log/openrun-startup.log` 확인 — 보통 jar 권한·DB 연결([DEPLOY.md](DEPLOY.md)) |
| 세션 유지(배포 후) | 인스턴스 교체 시 로그인 유지 확인 | `SPRING_SESSION` 테이블에 세션 저장 → 인스턴스 내려도 유지 |

---

## 6. (차별화 ①) 동시성 제어 실험 — 락 3단 비교

**시나리오:** 정원 **100**인 행사에 **동시 예약 300건**. 목표 — *정확히 100건 RESERVED, 나머지 WAITING, oversell 0.*

| 단계 | 방식 | 결과 | 교훈 |
|------|------|------|------|
| **0. 무방비** | 락 없이 `reserved_count++` | **oversell 발생** | 경합은 실재한다 |
| **1. 낙관적 락** | `@Version` + 재시도 | 정합성 OK, 고경합 시 재시도 폭증 | 충돌 빈도 낮은 곳에 적합 |
| **2. 비관적 락** | `@Lock(PESSIMISTIC_WRITE)` = `SELECT … FOR UPDATE` | **정합성 OK, 직렬화로 안정** | **선착순엔 비관적 락이 정답** ✅ |

**측정** — `ExecutorService` + `CountDownLatch`로 300스레드 동시 출발([`ReservationConcurrencyTest`](src/test/java/com/hackathon/openrun/service/ReservationConcurrencyTest.java)):

| 검증 | 0단계(무방비) | 2단계(비관적 락, 운영) |
|------|--------------|----------------------|
| `event.reserved_count` | **> 100 (oversell)** | **= 100** |
| RESERVED 행 수 / WAITING 행 수 | > 100 / — | **= 100 / = 200** |
| 최종 상태 | 정합성 붕괴 | `CLOSED`, 정합성 유지 |

```bash
./gradlew test --tests '*ReservationConcurrencyTest'
#  ✅ pessimisticLock_noOversell  — reserved_count == capacity == 100
#  ✅ noLock_oversells            — reserved_count > 100 (락의 필요성 입증)
```
**취소 → 대기 자동 승계:** RESERVED 취소로 좌석이 비면 같은 비관적 락 위에서 `WAITING` 선두를 `RESERVED`로 승계 + 카운트 보정.

---

## 7. (차별화 ②) 다중화 배포 & 확장 설계

상태(세션·정합성)를 공유 DB로 빼내 앱을 **무상태**로 만들어, 앱 계층을 **다중화(HA)** 했다. 자세한 설계 철학·의사결정은 [ARCHITECTURE.md](ARCHITECTURE.md), 배포 절차는 [DEPLOY.md](DEPLOY.md).

- **배포 토폴로지:** GCP 글로벌 LB → 리저널 MIG(멀티존 앱 인스턴스 2대 + 오토힐) → 공유 MariaDB VM(데이터 + 세션). 인프라는 코드로 박제([`deploy/`](deploy/)): `clone → export DB_PW → ./deploy/provision.sh`.
- **검증:** 로드밸런싱 · **Failover**(인스턴스 1대 삭제해도 무중단) · **세션 유지**(인스턴스 교체에도 로그인 유지).
- **확장 설계 (Now → Later):**

| 계층 | 이번 (제약 하) | 나중 (이상향) |
|------|----------------|----------------|
| 앱 | MIG 멀티존 2대 + 오토힐 | + 오토스케일, **커스텀 이미지**로 콜드스타트 단축 |
| 세션 | Spring Session JDBC(MariaDB) | **Redis** 세션 스토어 |
| DB | 단일 MariaDB VM + 백업 | **Cloud SQL HA + 읽기 레플리카** *(Cloud SQL은 MariaDB 미지원 → MySQL로 이관 시)* |
| 동시성 | 비관적 락 | + 원자적 조건부 UPDATE / Redis 카운터 |
| 배포 | 인스턴스 재생성 | 롤링 업데이트(무중단) |

> 마지막 SPOF인 **단일 DB**의 위치를 정확히 인지하고 다음 단계를 설계로 남긴 것이 핵심.

---

### 부록 — 시드 데이터
`DataInitializer`(멱등): `admin/admin123`(ADMIN), `host1·host2/host123`(HOST), `user1~3/user123`(USER), 행사 8개(OPEN 4 + SCHEDULED 4, capacity 5/10/30/100). 이미 데이터가 있으면 스킵.

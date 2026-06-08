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
| 배포 | GCP 멀티존 MIG + 매니지드 글로벌 LB · Secret Manager · TLS (단순 경로: 단일 VM systemd×2+Nginx) — [DEPLOY.md](DEPLOY.md) |

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

## 6. 핵심 설계 철학 (이 프로젝트가 진짜 증명하려는 것)

> 전체 설계서는 [ARCHITECTURE.md](ARCHITECTURE.md). 한 줄: **"동작하는 앱을 넘어, 대규모 서비스의 가용성을 처음부터 설계한다."**

1. **상태를 어디 두느냐가 확장성을 정한다.** 세션·카운터를 앱 메모리에서 떼어 공유 계층(DB)으로 빼내 앱을 **무상태**로 만들면, 그 순간 다중화·자동복구·오토스케일이 전부 열린다.
2. **정합성은 단일 집중인 공유 DB에 둔다.** 동시성 제어도 DB 레벨(비관적 락). JVM 락은 인스턴스가 여러 대면 무력하므로.
3. **체인은 가장 약한 고리만큼만 강하다.** 가용성은 SPOF가 결정한다 → SPOF를 계층별(LB→앱→DB)로 하나씩 제거하는 것이 HA의 전부.
4. **앱 서버는 가축(cattle), 상태는 보배.** 앱 인스턴스는 언제든 죽고 새로 태어나도 되는 교체 가능한 존재여야 한다.

이 철학의 검증이 §5(동시성 실험)와 [DEPLOY.md](DEPLOY.md)의 멀티존 MIG + Failover 시연이다 — 인스턴스를 죽여도 서비스가 살아남고, 인스턴스가 몇 개든 선착순 정합성이 깨지지 않는다.

---

## 7. 확장 설계 — Now vs Later (제약 하 구현 vs 이상향)

현재는 **앱 계층을 HA로 만들고**(멀티존 MIG + 매니지드 LB), 정합성·세션을 **공유 DB에 집중**시켰다. 남은 약점(단일 DB)을 *정확히 인지*하고 다음 단계를 설계로 남긴다. (근거: [ARCHITECTURE.md §3·§4](ARCHITECTURE.md))

| 계층 | 이번 (제약 하) | 나중 (이상향) | 근거 |
|------|----------------|----------------|------|
| LB | 매니지드 LB(헬스체크 게이팅) / 단일 VM은 Nginx | 트래픽 따라 정책 고도화 | 단일 리전엔 충분 |
| 앱 다중화 | **MIG 멀티존 2대 + 오토힐** | + 오토스케일 정책 | 가용성 핵심은 충족 |
| 콜드스타트 | 스타트업 스크립트 | **커스텀 이미지**로 부팅 단축 | 오픈 순간 폭주에 스케일이 제때 붙으려면 |
| 세션 | **Spring Session JDBC(MariaDB)** | **Redis** 세션 스토어 | 스택 제약 → DB에 안전 수용 |
| DB HA | 단일 MariaDB VM + 백업 | **Cloud SQL HA + 읽기 레플리카** | 마지막 SPOF, 알려진 약점 |
| 동시성 | **비관적 락(SELECT FOR UPDATE)** | + 원자적 조건부 UPDATE / Redis 카운터 | 명확·정확. 처리량 개선은 다음 |
| 비밀관리 | **Secret Manager** / 환경변수 | Secret Manager 정착 | 평문 금지 |
| 산출물 | **비공개 버킷 + 서비스 계정** | 동일 | 공개 URL 금지 |
| 배포 | 인스턴스 재생성 | **롤링 업데이트(무중단)** | 무중단은 다음 단계 |
| 리전 | 단일 리전 멀티존 | 필요 시 멀티리전(읽기 레플리카) | 강한 일관성 ↔ 광역복제 트레이드오프 |

**대표 의사결정 근거 (면접/발표용):**
- *왜 DB 락?* 인스턴스가 여러 대면 JVM 락은 각 JVM에 갇혀 무력 → 정합성 단일 집중인 DB에서 잠가야 인스턴스 수와 무관하게 직렬화.
- *왜 멀티존이고 멀티리전 아님?* 멀티리전은 광역 DB 복제 문제(동기=지연↑ / 비동기=유실·충돌)를 부르고 비관적 락 기반 강한 일관성과 상충. 선착순엔 "단일 리전 멀티존 + HA DB"가 스윗스팟.
- *단일 DB 약점을 알면서 감수한 이유?* 시간·스택 제약 + 학습 우선순위가 "앱 계층 HA 직접 설계". 약점 위치를 인지하고 다음 단계(Cloud SQL HA)를 설계로 남긴 것이 핵심.

---

## 8. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| **Oversell(정원 초과 예약)** | read-check-write 사이 경합 창 | `findByIdForUpdate`로 행 비관적 락 → 직렬화. 테스트로 0단계 재현 후 2단계로 해결 |
| 다중 인스턴스에서 로그아웃됨 | in-memory 세션은 인스턴스마다 별개 | **Spring Session JDBC**로 세션을 공유 DB에 저장 |
| `synchronized`가 안 먹힘 | JVM 락은 인스턴스 내부 한정 | 정합성의 단일 진실인 **DB에 락** |
| H2 테스트에서 lock timeout | 단일 행 고경합 | 테스트 JDBC URL에 `LOCK_TIMEOUT=20000`, 커넥션 풀 충분히 확보 |
| 취소했는데 대기자 승계 안 됨 | 취소-승계가 별도 트랜잭션이면 경합 | 한 트랜잭션 + 비관적 락 안에서 `cancel`→`promote` 처리 |
| 권한 거부 시 흰 에러 | 기본 403 | `accessDeniedPage("/error/403")` 커스텀 페이지 |
| MIG 인스턴스가 계속 UNHEALTHY/TIMEOUT | 부팅 미완(3~4분) 또는 startup 실패 | `/var/log/openrun-startup.log` 확인 — 보통 jar 다운로드 권한 / DB 연결([DEPLOY.md](DEPLOY.md) 트러블슈팅) |
| 앱이 DB 접속 실패 | `bind-address` 미개방 / 방화벽 3306 / 계정 `@'%'` 누락 | DB VM 개방 + `openrun-allow-db` 방화벽 + Secret Manager 비번 일치 |
| LB만 UNHEALTHY (인스턴스는 UP) | 헬스체크 방화벽 누락 | `35.191.0.0/16,130.211.0.0/22 → :8080` 허용 |

---

### 부록 — 시드 데이터
`DataInitializer`(멱등): `admin/admin123`(ADMIN), `host1·host2/host123`(HOST), `user1~3/user123`(USER), 행사 8개(OPEN 4 + SCHEDULED 4, capacity 5/10/30/100). 이미 데이터가 있으면 스킵.

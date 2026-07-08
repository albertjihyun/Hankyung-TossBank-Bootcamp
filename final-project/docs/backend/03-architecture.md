# 03. 아키텍처 & 컨벤션 명세

> 이 문서는 "어떻게 짤지"를 재결정하지 않게 하는 문서다. 구현 세션은 여기 적힌 구조·규약을 그대로 따르고, 바꾸고 싶으면 코드가 아니라 이 문서를 먼저 고친다.

## 1. 시스템 구성

```
브라우저 ── Next.js(FE) ── Spring Boot(BE, 단일 진입점) ──┬── MySQL
                                │                        ├── Redis (채팅 세션 TTL)
                                │  SSE 프록시              │
                                └──────▶ FastAPI(LLM팀) ──┘
                                ◀── /internal/* 콜백 (서비스 토큰)
```

- FE는 BE만 호출한다. FastAPI 직접 호출 금지 — 인증/권한 검증 지점을 BE 하나로 단일화하기 위함.
- LLM의 쓰기 작업(장바구니 담기, 문의 접수)은 전부 BE의 `/internal/*` API 콜백으로만 일어난다.
- 대화 내용은 어디에도 저장하지 않는다(기능 정의 확정). BE는 세션 ID 발급/TTL만 관리.

### 1-1. 배포 형상 — 단일 서버 단계 (2026-07-08 스터디 결정. 분산 형상은 §1-2)

EC2 1대 + docker-compose, 컨테이너 6개(nginx/next/spring/fastapi/mysql/redis).

```
인터넷 ──:80/443──▶ [nginx] ── /          ─▶ next:3000
                            ── /api/**    ─▶ spring:8080
                            ── /internal/** ─▶ 404 (라우팅하지 않음)
docker 내부망:  spring ─▶ mysql:3306, redis:6379 / spring ◀▶ fastapi:8000 / next(서버측) ─▶ spring:8080
```

- **외부에 publish되는 포트는 nginx의 80/443뿐.** spring/next/fastapi/mysql/redis는 내부망 `expose`만 — 배포 compose에 `ports:` publish를 적는 순간(특히 spring 8080) nginx를 우회하는 뒷문이 생긴다.
- **`/internal` 3중 방어**: ① nginx가 라우팅하지 않음(경로 차단) ② spring 포트 미노출(네트워크 차단) ③ 서비스 토큰 필터(애플리케이션 검증). ①②는 네트워크 수준이라 토큰이 유출돼도 외부에선 쓸 곳이 없다.
- MySQL 데이터는 named volume으로 컨테이너 생명주기와 분리. RDS 전환은 분산 단계에서 검토.
- **FE base URL이 2개** (FE 팀 공유 필요): 브라우저 발 호출은 `NEXT_PUBLIC_API_URL`(도메인, nginx 경유), Next 서버 컴포넌트 발 호출은 `API_URL`(`http://spring:8080`, 내부망 직행).

### 1-2. 배포 형상 — 분산 단계 (2026-07-08 스터디 결정)

> §1-1(단일 서버)은 모든 계층이 단일 = 전부 SPOF다. 부하 관리·무중단 배포·failover를 위해 **무상태 계층만 복제**하고 **상태는 외부화**한다. 가르는 기준 한 단어: **상태(stateful vs stateless)**.

```
                    ┌── [AWS ALB] ──┐            ← 층 1: 인스턴스 간 분배 (인스턴스 바깥, AWS 관리형)
        ┌───────────┼───────────────┼───────────┐
        ▼           ▼               ▼
  ┌──EC2-1───┐ ┌──EC2-2───┐ ┌──EC2-3───┐
  │ nginx    │ │ nginx    │ │ nginx    │         ← 층 2: 인스턴스 안 라우팅 + 외부 차단 (§1-1 그대로)
  │  ├ next  │ │  ├ next  │ │  ├ next  │
  │  ├ spring│ │  ├ spring│ │  ├ spring│
  │  └fastapi│ │  └fastapi│ │  └fastapi│
  └──────────┘ └──────────┘ └──────────┘
        └───────── RDS(MySQL) Multi-AZ · ElastiCache(Redis) — 공유 상태 ─────────┘
```

**D-분산1. 복제/공유 경계 = 상태 외부화.** next·spring·fastapi는 무상태 → 3대 복제. mysql·redis는 상태를 들고 있어 복제하면 세계가 갈라짐 → 공유. spring이 무상태 칸에 들어갈 수 있는 건 이미 (a) 인증을 JWT로 해 로그인 상태를 메모리에 안 두고, (b) 채팅 세션을 Redis TTL로 외부화했기 때문. **분산 전환 시 spring 코드 변경 없음.**

**D-분산2. DB 이중화 = RDS Multi-AZ, read replica는 두지 않는다.** 이중화 이유는 읽기 부하가 아니라 failover(죽으면 전체가 죽음)다. JARVIS 트래픽의 병목은 DB가 아니라 LLM 대기(FastAPI)라 read replica의 근거가 미달. Multi-AZ는 물리 2대(서로 다른 AZ)·동기 복제·자동 승격을 엔드포인트 DNS 하나 뒤로 추상화 → 앱은 단일 URL만 보고 읽기/쓰기 분리도 없음. **부수 효과로 복제 지연·read-your-own-writes 문제를 애초에 회피**(대기 서버는 읽지 않으므로). *비용(상시 2배)상 데모 기간엔 단일 인스턴스로 두고 "프로덕션이면 Multi-AZ 토글 ON"으로 운영 가능 — 코드 영향 없음.*

**D-분산3. Redis = ElastiCache primary+replica.** 자동 failover, 앱은 단일 엔드포인트. mysql과 동형.

**D-분산4. 로드밸런서는 2층.** 층 1(인스턴스 간 분배)은 **반드시 인스턴스 바깥**에 있어야 한다 — 특정 EC2 안에 두면 그 EC2가 죽을 때 분배기도 같이 죽어 SPOF가 그대로 이동. AWS ALB(관리형)를 쓰면 LB 자신의 이중화를 AWS가 떠안음(nginx를 별도 EC2에 직접 두면 그 EC2가 다시 SPOF). 층 2(인스턴스 안 nginx)는 §1-1 역할 유지: next/spring/fastapi 라우팅 + spring 8080 외부 미노출. **`/internal` 3중 방어는 그대로 유지**되고 앞단에 ALB가 한 겹 더 붙는 셈(보안그룹으로 각 EC2 nginx 포트는 ALB에서 온 것만 허용).

**D-분산5. 스케줄러는 분산 안전하게 — 조건부 UPDATE + Redis 분산 락.** 상세는 [01 §6](01-order-state-machine.md#6-스케줄러-명세). 다인스턴스에서 같은 잡이 중복 실행되므로 (a) 전이 쿼리는 `WHERE status=<이전>` 조건부 UPDATE(정합성 최종 방어선), (b) 잡 레벨은 Redis 분산 락(ShedLock)으로 매 틱 1대만 실행(중복 부수효과 차단). 01 §6의 "인스턴스 1대 전제" 폐기.

**D-분산6. 채팅 SSE의 분산 대응.** 채팅은 SSE(장수명 HTTP 연결)라 짧은 요청을 가정한 중간 장비(ALB·nginx)와 충돌한다. 세 가지:
- **self-pinning**: 스트림 하나는 TCP 연결 하나 = 처리 중 한 인스턴스에 자동 고정(관리 불필요). 분산 단위는 "토큰 조각"이 아니라 "스트림(요청) 전체" — LB는 요청 단위로 나눈다. **다음 턴은 아무 인스턴스로 가도 되고**(세션이 Redis에 있으므로), 따라서 **sticky session 불필요**.
- **idle timeout**: LLM이 뜸 들이는 침묵 구간(>ALB idle timeout, 기본 60s)에 연결이 끊긴다 → 주기적 하트비트(`: ping` 주석) 전송 + ALB/nginx idle·read timeout을 넉넉히(예: 300s).
- **버퍼링**: 인스턴스 nginx가 응답을 모았다 한 번에 넘기면 SSE의 실시간성이 죽는다(로딩만 돌다 답이 팍) → 스트리밍 경로에 `proxy_buffering off`(+ `proxy_http_version 1.1`), 또는 spring이 `X-Accel-Buffering: no` 헤더 전송. 전역이 아니라 `/api/chat` 등 스트리밍 경로에만.

## 2. 결정 로그

### D1. 패키지 구조는 도메인 우선(package-by-feature)

- **선택지**: (A) 레이어 우선(controller/, service/, repository/에 전 도메인 혼재) (B) 도메인 우선(order/ 안에 controller+service+repository)
- **기준**: 이 프로젝트는 도메인이 12개 이상 — 레이어 우선이면 controller 패키지에 파일 15개가 쌓여 탐색 비용이 커진다. 구현 세션(Opus)이 "주문 기능"을 만질 때 order/ 폴더만 열면 되게.
- **선택**: (B).
- **트레이드오프**: 공통 코드의 위치가 모호해질 수 있음 → `global/`에 격리(아래 §3).

### D2. 응답은 공통 envelope로 감싼다

- **기준**: FE가 성공/실패를 HTTP 상태 + 일관된 body 구조로 판별할 수 있어야 하고, LLM 콜백도 같은 규약을 쓰면 계약 문서가 얇아진다.
- **형식**:
```json
// 성공
{ "success": true, "data": { ... } }
// 실패
{ "success": false, "error": { "code": "ORDER_INVALID_TRANSITION", "message": "배송중인 상품은 취소할 수 없습니다." } }
```
- 에러 `code`는 `<도메인>_<사유>` 대문자 스네이크. message는 사용자 노출 가능한 한국어 문장.
- HTTP 상태: 400(검증/전이 위반) 401(미인증) 403(권한) 404(없음) 409(중복: 이메일, 재신청 등) 500.

### D3. 인증은 JWT AT(30분) + RT(14일, DB 저장)

- 일반(이메일) 로그인만. **OAuth는 MVP 제외**(2026-07-07 팀 결정, 고도화 후보) — 도입 시 Spring Security OAuth2 Client를 같은 JWT 발급 구조 위에 얹는다(토큰 체계 변경 없음).
- AT는 `Authorization: Bearer`, RT는 HttpOnly 쿠키. 재발급: `POST /api/auth/refresh`.
- Spring Security 필터 체인: JWT 검증 필터 → 권한(Role) 검사. `/api/auth/**`, 상품 조회 계열, `POST /api/chat`(게스트 허용)은 permitAll.
- 게스트: `guest_id` HttpOnly 쿠키(UUID). 없으면 첫 채팅 요청 시 발급.

### D4. internal API는 고정 서비스 토큰 헤더로 인증한다

- **선택지**: (A) 고정 토큰 헤더 (B) 서비스 간 JWT (C) mTLS/네트워크 격리
- **기준**: 데모 규모에서 "FE 경유로는 절대 호출 불가"만 보장하면 됨.
- **선택**: (A). `X-Internal-Token: <env>` 헤더를 검증하는 필터를 `/internal/**`에만 적용. 토큰은 양쪽 `.env`로 공유, 코드/레포에 하드코딩 금지.
- **트레이드오프**: 토큰 유출 시 전체 노출 — 데모 환경 감수. 배포 시 `/internal/**`은 외부 라우팅에서 제외하면 이중 방어.

### D5. 채팅 프록시는 SSE 패스스루

- FE `POST /api/chat` → BE가 세션 검증 후 FastAPI로 스트리밍 요청 → 응답 SSE 이벤트를 그대로 FE에 중계. (게스트 횟수 제한 없음 — 2026-07-07 회의로 폐지)
- 구현: Spring WebFlux 전면 도입 대신 **MVC + `SseEmitter` + WebClient**(FastAPI 호출용)만 사용. 이유: 나머지 API가 전부 동기 CRUD라 전면 리액티브는 과함.
- 타임아웃: FastAPI 연결 5초 / 전체 응답 60초. 초과·오류 시 SSE로 `error` 이벤트 전송 후 종료. 재시도는 FE 버튼(자동 재시도 없음 — LLM 호출 중복 비용 방지).

### D6. user_event 적재는 Spring 이벤트 + @Async

- 서비스 레이어에서 `ApplicationEventPublisher.publishEvent()` → `@Async @EventListener`가 INSERT. 본 트랜잭션과 분리(로그 실패가 주문을 굴리는 트랜잭션을 깨면 안 됨).

## 3. 패키지 구조

```
com.jarvis
├── global
│   ├── config/          # Security, Redis, WebClient, Async, Scheduling
│   ├── auth/            # JWT provider·필터, OAuth 핸들러, 게스트 쿠키
│   ├── response/        # ApiResponse envelope, ErrorCode enum, GlobalExceptionHandler
│   └── event/           # user_event 발행·리스너
├── member    ├── brand     ├── category  ├── product
├── cart      ├── order     ├── claim     ├── review
├── wishlist  ├── address   ├── inquiry
├── chat      # 채팅 프록시(세션, 게스트 카운트, SSE)
├── internal  # /internal/* 컨트롤러 (LLM 콜백 전용)
└── seller    # 판매자 지표 조회
```

각 도메인 패키지 내부: `XxxController` / `XxxService` / `XxxRepository` / `dto/` / (필요시) `Xxx` 엔티티. 컨트롤러에 비즈니스 로직 금지, 엔티티를 API로 노출 금지 (CLAUDE.md).

- **internal 컨트롤러는 자체 로직을 갖지 않고 도메인 서비스를 재사용한다.** 같은 행위(예: 담기)는 같은 서비스 메서드 하나로 — `/api`와 `/internal`은 신뢰 모델이 다른 입구일 뿐, 검증·처리 로직은 서비스 레이어에서 공유. (검증이 컨트롤러에 있으면 입구를 낼 때마다 복붙된다 — 01 체크리스트와 같은 맥락)

## 4. 기술 스택 & 버전

| 항목 | 선택 | 근거 |
|---|---|---|
| Java | 21 (Microsoft OpenJDK, JAVA_HOME 명시 필수) | 로컬 환경 제약 (CLAUDE.md) |
| Spring Boot | 3.5.x | Java 21 대응 최신 안정 |
| 빌드 | Gradle wrapper (`./gradlew`) | |
| DB | MySQL 8.x (로컬 docker-compose) | |
| ORM | Spring Data JPA + Hibernate. 복잡 집계(판매자 지표)만 JdbcTemplate 네이티브 쿼리 허용 | QueryDSL 미도입 — 동적 쿼리가 검색 1곳뿐이라 도입 비용>효용 |
| Redis | spring-data-redis (채팅 세션 TTL 전용) | |
| 인증 | spring-security + jjwt (OAuth 제외로 oauth2-client 미도입) | |
| 문서화 | springdoc-openapi (Swagger UI) — 04 문서와 이중화 방지 위해 코드 어노테이션은 최소, 04 문서가 원본 | |

## 5. 설정/환경변수 규약

`application.yml`(공통) + `application-local.yml`(gitignore) 프로파일. 시크릿은 전부 환경변수 참조:

| 키 | 용도 |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `JWT_SECRET` | AT/RT 서명 |
| `LLM_BASE_URL` | FastAPI 주소 |
| `INTERNAL_TOKEN` | internal API 서비스 토큰 (FastAPI와 공유) |
| `app.mock.shipping-minutes` 등 | mock 배송 간격 (환경변수 아님, yml 기본값 5/5) |

`.gitignore`에 `application-local.yml`, `.env` 포함 확인. 어떤 시크릿도 커밋 금지.

## 6. 공통 규약 요약 (구현 세션 체크용)

- [ ] 모든 컨트롤러 응답이 `ApiResponse<T>` envelope인가
- [ ] 예외는 도메인별 커스텀 예외 → `GlobalExceptionHandler`에서 ErrorCode 매핑인가 (컨트롤러 try-catch 금지)
- [ ] 상태 전이·자격 검증(01 문서 매트릭스)이 서비스 레이어에 있는가
- [ ] `/internal/**`에 서비스 토큰 필터가 걸려 있고, FE 경로에서 접근 불가한가
- [ ] FastAPI 호출에 타임아웃이 설정돼 있는가 (커넥션 5s / 응답 60s)
- [ ] 시크릿이 코드·yml에 리터럴로 없는가
- [ ] 배포 compose에서 nginx만 `ports:` publish이고 나머지는 `expose`인가 (spring 8080 외부 노출 = nginx 우회 뒷문)
- [ ] internal 컨트롤러가 도메인 서비스를 재사용하는가 (로직 복제 금지)

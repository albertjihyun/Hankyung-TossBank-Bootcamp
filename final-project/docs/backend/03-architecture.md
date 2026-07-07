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

- 일반 로그인과 OAuth 모두 최종적으로 같은 JWT를 발급한다(발급 경로만 다름).
- AT는 `Authorization: Bearer`, RT는 HttpOnly 쿠키. 재발급: `POST /api/auth/refresh`.
- Spring Security 필터 체인: JWT 검증 필터 → 권한(Role) 검사. `/api/auth/**`, 상품 조회 계열, `POST /api/chat`(게스트 허용)은 permitAll.
- OAuth: Spring Security OAuth2 Client. 성공 핸들러에서 member upsert(이메일 기준) 후 JWT 발급 → FE 콜백 URL로 리다이렉트.
- 게스트: `guest_id` HttpOnly 쿠키(UUID). 없으면 첫 채팅 요청 시 발급.

### D4. internal API는 고정 서비스 토큰 헤더로 인증한다

- **선택지**: (A) 고정 토큰 헤더 (B) 서비스 간 JWT (C) mTLS/네트워크 격리
- **기준**: 데모 규모에서 "FE 경유로는 절대 호출 불가"만 보장하면 됨.
- **선택**: (A). `X-Internal-Token: <env>` 헤더를 검증하는 필터를 `/internal/**`에만 적용. 토큰은 양쪽 `.env`로 공유, 코드/레포에 하드코딩 금지.
- **트레이드오프**: 토큰 유출 시 전체 노출 — 데모 환경 감수. 배포 시 `/internal/**`은 외부 라우팅에서 제외하면 이중 방어.

### D5. 채팅 프록시는 SSE 패스스루

- FE `POST /api/chat` → BE가 게스트 카운트/세션 검증 후 FastAPI로 스트리밍 요청 → 응답 SSE 이벤트를 그대로 FE에 중계.
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

## 4. 기술 스택 & 버전

| 항목 | 선택 | 근거 |
|---|---|---|
| Java | 21 (Microsoft OpenJDK, JAVA_HOME 명시 필수) | 로컬 환경 제약 (CLAUDE.md) |
| Spring Boot | 3.5.x | Java 21 대응 최신 안정 |
| 빌드 | Gradle wrapper (`./gradlew`) | |
| DB | MySQL 8.x (로컬 docker-compose) | |
| ORM | Spring Data JPA + Hibernate. 복잡 집계(판매자 지표)만 JdbcTemplate 네이티브 쿼리 허용 | QueryDSL 미도입 — 동적 쿼리가 검색 1곳뿐이라 도입 비용>효용 |
| Redis | spring-data-redis (채팅 세션 TTL 전용) | |
| 인증 | spring-security + oauth2-client + jjwt | |
| 문서화 | springdoc-openapi (Swagger UI) — 04 문서와 이중화 방지 위해 코드 어노테이션은 최소, 04 문서가 원본 | |

## 5. 설정/환경변수 규약

`application.yml`(공통) + `application-local.yml`(gitignore) 프로파일. 시크릿은 전부 환경변수 참조:

| 키 | 용도 |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `JWT_SECRET` | AT/RT 서명 |
| `OAUTH_CLIENT_ID` / `OAUTH_CLIENT_SECRET` | OAuth 제공자 |
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

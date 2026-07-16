# 06. 구현 순서 계획stap

> 각 단계는 "완료 조건(검증 방법)"이 충족돼야 다음으로 넘어간다. 구현 세션은 단계마다 브랜치를 따고(feature-workflow), 끝나면 ship-it으로 PR을 만든다. 한 단계 = PR 1개가 기본.

## 진행 원칙

- **명세가 원본**: 01~05 문서와 코드가 어긋나면 문서를 먼저 고치는 PR을 낸다(코드에 몰래 맞추지 않는다).
- **세로로 얇게**: 도메인 하나를 Controller→Service→Repository→테스트까지 관통해서 끝내고 다음 도메인으로. 전 도메인의 엔티티만 먼저 깔아두는 식(가로 슬라이스) 금지 — 통합 검증이 늦어짐.
- **완료 조건은 실행 가능해야 함**: "코드 작성함"이 아니라 "curl/Swagger로 이런 응답 확인".

## Phase 0. 스캐폴딩 (0.5일)

Spring Boot 3.5 + Gradle 프로젝트 생성(`backend/`), docker-compose(MariaDB+Redis), 03 문서의 global 패키지(envelope, ErrorCode, GlobalExceptionHandler, Async/Scheduling 설정), application.yml 프로파일 구조.
- **완료**: `./gradlew bootRun`(JAVA_HOME 명시) 후 `GET /actuator/health` 200. 존재하지 않는 경로가 envelope 형식의 404를 반환.

## Phase 1. 인증 + 회원 (1일)

member/guest/refresh_token 테이블, 일반 가입/로그인/로그아웃/refresh/me(A-1~A-5), JWT 필터, Role 가드, 게스트 쿠키 발급. (OAuth는 MVP 제외 — 2026-07-07 팀 결정)
- **완료**: 가입→로그인→AT로 /me→만료 후 refresh→로그아웃 시나리오가 Swagger로 통과. SELLER 시드 계정으로 USER 전용 API 403 확인.

## Phase 2. 카탈로그 (1일)

category/brand/product(+option), P-1/P-2/P-3(빈 목록)/P-4/P-6, PRODUCT_VIEW 이벤트 적재(user_event + @Async AFTER_COMMIT 리스너 — 03 D6). **시드 데이터 1차분**(대분류 4개+소분류 12개, 상품 50개 수준 — LLM팀 협의 전 개발용 최소치).
- **완료**: 상세 조회가 이미지/옵션/평점(0건)을 포함해 응답. 조회 후 user_event에 행 증가 확인.

## Phase 3. 장바구니 + 주문 + 클레임 (1.5일)

cart(C-1~4 — 게스트 담기 + 가입 시 병합 승계, 02 D30), orders/order_item 스냅샷 생성 + mock 결제(O-1, O-2 — 아이템 PENDING→ORDERED 전이, 01 D9), 주문 조회(O-3, O-4 가능 액션 포함), 배송 전이 + 클레임 자동 승인 스케줄러(01 §6), 클레임 신청/내역(O-5, O-6).
- **완료**: 담기→주문(성공/실패 수단 각각, 실패 주문 아이템이 PENDING으로 남는지 포함)→간격 1분으로 줄인 스케줄러로 DELIVERED 도달→반품 신청→자동 승인→상태·내역 반영까지 e2e 시나리오 통과. **바로 구매(items[] 경로)도 장바구니 미접촉으로 주문됨 확인**. 01 문서 §7 체크리스트 전부 확인.

## Phase 4. 마이페이지 잔여 (1일)

review(M-1~3) + P-3 실데이터, wishlist(M-4~6), recent(M-7), address(M-8), inquiry 조회(M-9), 프로필(M-10). (관리자 AD-1~7은 MVP 제외 — 2026-07-09 팀 결정, 04 §8)
- **완료**: 후기 자격 상태(DELIVERED/EXCHANGED/CONFIRMED)에서만 작성됨(그 외 400), 후기 신고 접수·중복 신고 409 확인. 신고 처리(HIDE)·문의 답변은 고도화 — 데모용 답변 완료 문의는 시드로.

## Phase 5. 채팅 티켓 발급 + 카드 하이드레이션 + internal API (1.5일, LLM팀 병행 필요)

세션 발급(Redis TTL), **CH-1 세션+스트림 티켓(RS256) 발급 + JWKS 엔드포인트(`/.well-known/jwks.json`)**, **P-7 카드 하이드레이션**, 게스트 쿠키 발급, internal I-1~I-7 + 서비스 토큰 필터. 채팅 SSE는 FastAPI 직결이라 **Spring은 SSE를 중계하지 않는다**(03 D5) — BE는 티켓 발급·검증키·콜백만 책임. FastAPI가 아직 없으면 **mock FastAPI**(고정 SSE 반환·티켓 JWKS 검증 스텁)로 FE 직결 흐름을 먼저 검증.
- **완료**: CH-1이 유효 티켓 발급 → (mock)FastAPI가 JWKS로 검증 → SSE `products{productId,reason}` 수신 → FE가 P-7로 카드 조립. internal API가 토큰 없이 401, FE 경로로 접근 불가.
- **선행 조건**: 05 계약 v0.2 핵심(직결·티켓·2왕복)은 LLM 팀과 합의됨(2026-07-16). 잔여 OPEN(벡터DB 배치 동기화·LIMIT 기준치 등 §4)은 스텁으로 진행.

## Phase 6. 판매자 + 시드 완성 + 통합 (1일)

S-1~S-4, 시드 데이터 최종분(02 §5 규모, LLM팀 합의 형식), user_event 더미 생성 스크립트, 전 구간 통합 점검.
- **완료**: 판매자 계정으로 summary가 0이 아닌 지표 반환. 대표 데모 시나리오(추천→담아줘→주문→반품→자동 승인 확인→문의 챗봇) 리허설 1회 통과.

## 일정 감각

합계 ~7.5일. MVP까지 남은 기간과 맞물리므로 **Phase 3까지가 첫 주 목표**, Phase 5는 LLM 팀 진도와 동기화. 지연 시 자르는 순서(뒤에서부터): S-4 판매자 챗봇 → 연관 추천. (관리자 기능은 이미 MVP 제외라 자를 목록에 없음)

## 구현 세션(Claude)에게

- 시작 전에 이 폴더의 01~05를 전부 읽어라. CLAUDE.md의 빌드 규칙(JAVA_HOME 명시)을 지켜라.
- 각 Phase 완료 조건을 실제로 실행해 확인하기 전에는 완료라고 보고하지 마라.
- 명세에 없는 판단이 필요해지면: 작다면 이 문서들의 결정 로그 스타일로 문서에 추가하고 진행, 크다면(스키마 변경·계약 변경) 사용자에게 물어라.

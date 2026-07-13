# 04. REST API 명세

> 기준: 「기능 정의 - 이소희」의 페이지별 기능에서 역산. 응답은 전부 03 문서의 envelope(`{success, data|error}`), 인증 규약도 03을 따른다.
> 표기: 🔓 인증 불필요 / 🔑 로그인 필요 / 🏪 SELLER / 🛡 ADMIN / ⚙ internal(서비스 토큰). `{}`는 path variable.
> LLM 콜백(⚙ `/internal/*`)과 채팅 프록시의 상세 스키마는 [05 LLM 연동 계약](05-llm-contract.md)이 원본이고 여기서는 목록만 둔다.

## 1. auth

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| A-1 | POST | /api/auth/signup | 🔓 | 회원가입. body: email, password, nickname, gender, birthDate, agreeTerms(true 필수), agreePrivacy(true 필수), guestId? — 성공 시 자동 로그인(토큰 발급) + 게스트 승계(user_event 이관 + 장바구니 병합 — 02 D5·D30) |
| A-2 | POST | /api/auth/login | 🔓 | 일반 로그인. body: email, password, guestId? |
| A-3 | POST | /api/auth/logout | 🔓(RT쿠키) | RT 삭제 + 쿠키 만료. AT가 만료돼도 로그아웃은 가능해야 하므로 RT 쿠키 기준(없어도 성공 응답) |
| A-4 | POST | /api/auth/refresh | 🔓(RT쿠키) | AT 재발급 |
| A-5 | GET | /api/auth/me | 🔑 | 내 정보(id, email, nickname, role) — FE 라우팅 가드용 |

- OAuth는 MVP 제외(2026-07-07 팀 결정). 고도화 도입 시 `GET /oauth2/authorization/{provider}` 추가.

- A-1 검증: 이메일 형식/중복(409 `MEMBER_EMAIL_DUPLICATE`), 비밀번호 규칙(8자+, 영문+숫자), gender(MALE/FEMALE), birthDate(과거 날짜), 약관 2건 미동의 400.
- A-2 실패는 계정 존재 여부 무관하게 통일 메시지(401 `AUTH_LOGIN_FAILED`) — 기능 정의 명시.
- 이메일 중복 확인은 별도 기능 없이 A-1의 409 응답으로만 처리 (2026-07-07 회의 — "기능만 일단 돌아가도록").

## 2. product / category / brand

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| P-1 | GET | /api/categories | 🔓 | 카테고리 트리(대분류+소분류, 02 D20). 메인 해시태그는 대분류만 사용 |
| P-2 | GET | /api/products/{id} | 🔓 | 상품 상세: 대표 이미지(단일 — 02 D14), 옵션 목록, 정가/판매가, summary/attributes/description, 브랜드 요약, 평점 통계(평균·개수) — 조회 시 PRODUCT_VIEW 이벤트 적재(로그인/게스트 공통) |
| P-3 | GET | /api/products/{id}/reviews | 🔓 | 후기 목록. query: page, size, sort(latest\|rating) — status=VISIBLE만 |
| P-4 | GET | /api/products/popular | 🔓 | 인기 상품 N개(기본 12): 최근 7일 판매수(order_item×PAID 주문 집계 — ORDER_CREATED 이벤트는 주문 단위라 상품별 집계 불가, 02 §4) → 부족하면 PRODUCT_VIEW 수 → 그래도 부족하면 최신순으로 채움 (비로그인 메인·신규 회원 fallback 공용) |
| P-5 | GET | /api/products/recommended | 🔑 | "OO님을 위한 추천". LLM 프로필 기반 — 내부적으로 FastAPI 추천 API 호출(05 문서). **타임아웃 연결 2s/응답 3s**(채팅용 60s와 별도 — 메인 렌더 블로킹 방지), 실패·타임아웃·프로필 없음 시 P-4로 fallback |
| P-6 | GET | /api/brands/{id} | 🔓 | 브랜드 소개 + 상품 목록. query: category?, sort(popular\|latest\|price_asc\|price_desc), page, size |

- P-2의 평점 통계는 review 테이블 실시간 집계(파생값 저장 금지).
- 연관 추천 2종(함께 구매/대체 상품)은 상세 화면 요소지만 추천 로직이 LLM 소관이라 05 문서의 FastAPI 호출로 정의(BE는 프록시 GET `/api/products/{id}/related`).

## 3. cart

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| C-1 | GET | /api/cart | 🔓(게스트 허용) | 내(회원/게스트) 장바구니: 아이템(상품 요약, 옵션, 수량, 현재가), 합계는 FE 계산 아님 — data에 totalOriginal/totalSale/discount 포함. HIDDEN 상품 아이템은 목록에 유지하되 `purchasable=false`로 표시(합계에서 제외) — 주문 시도는 O-1이 400 |
| C-2 | POST | /api/cart/items | 🔓(게스트 허용) | 담기. body: productId, optionId?, quantity — 동일 상품+옵션 존재 시 수량 합산. CART_ADD(manual) 이벤트. 게스트는 guest_id 쿠키가 소유 주체(없으면 발급 — 02 D30) |
| C-3 | PATCH | /api/cart/items/{id} | 🔓(게스트 허용) | 수량 변경. body: quantity(≥1) |
| C-4 | DELETE | /api/cart/items/{id} | 🔓(게스트 허용) | 삭제 (복수 삭제는 FE에서 반복 호출 — 데모 규모) |

- 옵션 있는 상품에 optionId 누락 → 400 `CART_OPTION_REQUIRED`. optionId가 해당 상품의 옵션이 아니면 400 `CART_OPTION_INVALID`(02 D26 ①). 본인(회원 또는 게스트 쿠키) 아이템 아니면 403. quantity는 1~99(합산 결과 포함 — INT 오버플로·비정상 입력 방지, I-2 동일).
- 게스트 장바구니(02 D30): 소유 주체는 guest_id 쿠키. 가입/로그인(A-1/A-2 guestId) 시 회원 장바구니로 병합(동일 상품+옵션 수량 합산·상한 99). **주문(O-1)은 로그인 필수** — 게스트가 결제 진입 시 FE가 로그인 유도.
- 부분 선택 결제는 O-1의 cartItemIds[]로 지원 — 선택 항목 합계 표시는 FE 계산, 결제 금액의 진실은 O-1 서버 재계산(원칙 유지).

## 4. order / claim

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| O-1 | POST | /api/orders | 🔑 | 주문 생성+모의 결제 한 번에. body: cartItemIds[], addressId 또는 address 직접 입력, deliveryRequest?(02 D22), paymentMethod — 처리: PENDING 생성(아이템도 `PENDING` — 01 D9) → 스냅샷 복사 → mock 결제 판정 → PAID(아이템 `ORDERED` 전이·장바구니 차감·ORDER_CREATED 이벤트) 또는 PAYMENT_FAILED. 응답: orderId, orderNo, status |
| O-2 | POST | /api/orders/{id}/retry-payment | 🔑 | 실패 주문 재결제. body: paymentMethod — PENDING/PAYMENT_FAILED에서만. 성공 시 부수효과는 O-1의 PAID와 동일(아이템 `ORDERED` 전이·ORDER_CREATED 적재·장바구니에 같은 상품+옵션 행이 남아 있으면 삭제) |
| O-3 | GET | /api/orders | 🔑 | 내 주문 목록: 대표 상태(01 §4), 아이템 요약. query: page, size |
| O-4 | GET | /api/orders/{id} | 🔑 | 주문 상세: 아이템별 상태, 배송지 스냅샷, 금액, 아이템별 가능 액션(canCancel/canReturn/canExchange/canReview — 01 §3 매트릭스를 서버가 계산해 내려줌) |
| O-5 | POST | /api/order-items/{id}/claims | 🔑 | 클레임 신청. body: type(CANCEL\|RETURN\|EXCHANGE), reason? — 01 매트릭스 위반 시 400 `CLAIM_NOT_ALLOWED`, 활성 클레임 존재 시 409 |
| O-6 | GET | /api/claims | 🔑 | 내 취소·반품·교환 내역. query: page, size |

- O-1 검증: 대상 상품 전부 `status=ON_SALE`(HIDDEN 포함 시 400), 수량 아이템당 1~99, 금액은 서버가 스냅샷 가격으로 재계산(클라이언트가 보낸 금액은 신뢰하지 않음 — body에 금액 필드 자체가 없음).
- O-4가 가능 액션을 내려주는 이유: 상태 매트릭스 판단을 FE에 중복 구현하지 않기 위해(단일 진실은 서버). FE는 boolean만 보고 버튼 노출.
- 표시용 주문번호 `orderNo`는 저장하지 않고 파생: `"ORD-" + created_at(yyyyMMdd) + "-" + id` (02 D24). O-3/O-4 응답에 포함.
- O-1에서 결제까지 한 API로 묶은 이유: 모의 결제라 "생성→별도 결제 승인" 2단계로 나눌 외부 경계가 없음. 실 PG 전환 시(01 D7) 이 API를 생성/승인으로 쪼개는 게 교체 지점.

## 5. mypage (review / wishlist / recent / address / inquiry)

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| M-1 | POST | /api/reviews | 🔑 | 후기 작성. body: orderItemId, rating(1~5), content — 자격(DELIVERED/EXCHANGED/CONFIRMED + 미작성, 01 §3) 위반 400 `REVIEW_NOT_ALLOWED` |
| M-2 | GET | /api/reviews/me | 🔑 | 내가 쓴 후기 목록 |
| M-3 | POST | /api/reviews/{id}/reports | 🔑 | 후기 신고. body: reason — 중복 신고 409, 자기 후기 신고 400 `REVIEW_SELF_REPORT`(02 D29) |
| M-4 | GET | /api/wishlist | 🔑 | 찜 목록 |
| M-5 | POST | /api/wishlist | 🔑 | 찜 추가. body: productId — 중복 409. WISHLIST_ADD 이벤트 |
| M-6 | DELETE | /api/wishlist/{productId} | 🔑 | 찜 해제 |
| M-7 | GET | /api/products/recent | 🔑 | 최근 본 상품 (user_event 기반, 중복 제거 최신 20개) |
| M-8 | GET/POST/PATCH/DELETE | /api/addresses(/{id}) | 🔑 | 배송지 CRUD. is_default 지정 시 기존 기본 해제(같은 트랜잭션). 삭제: 기본 배송지는 다른 배송지가 있을 때만 가능 — 등록순 가장 오래된 주소 자동 승격(같은 트랜잭션), 유일한 배송지는 삭제 불가 400 `ADDRESS_LAST_UNDELETABLE`(02 D29) |
| M-9 | GET | /api/inquiries/me | 🔑 | 내 문의 내역(읽기 전용): 제목(02 D23), 내용, 상태, 답변 |
| M-10 | PATCH | /api/members/me | 🔑 | 프로필 수정: nickname |

- 문의 "접수"는 사용자 API가 없다 — 문의 챗봇(LLM)이 ⚙ internal 콜백으로만 생성(문의 단일 채널 원칙, 05 문서).
- 후기는 **등록만** — 본인 후기 수정·삭제 API 없음(02 D29, MVP 팀 결정).

## 6. chat (프록시 — 상세는 05 문서)

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| CH-1 | POST | /api/chat/sessions | 🔓(게스트 허용) | 세션 발급(Redis TTL 10분). "새 대화" 버튼도 이걸 다시 호출 |
| CH-2 | POST | /api/chat | 🔓(게스트 허용) | 추천 챗봇 메시지 전송, SSE 스트림 응답. 게스트도 무제한 사용(횟수 제한 폐지 — 2026-07-07 회의), 개인화만 미적용 |
| CH-3 | POST | /api/chat/cs | 🔑+🔓 | 문의 챗봇(고객센터) 메시지. 비로그인은 일반 안내만(주문 질문 시 로그인 유도 메시지는 LLM 측 처리) |

## 7. seller

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| S-1 | GET | /api/seller/summary | 🏪 | 자사 요약: 기간별 매출/주문수(order_item 집계), 상품별 조회수·담김수·판매수(user_event+order_item) query: from, to. **집계 규칙**: 매출·판매수 = PAID 주문의 order_item 중 `PENDING`/`CANCELLED`/`RETURNED` 제외(EXCHANGED·처리중 포함) — I-6도 동일 |
| S-2 | GET | /api/seller/orders | 🏪 | 자사 상품이 포함된 주문 아이템 목록 |
| S-3 | PATCH | /api/seller/products/{id} | 🏪 | 자사 상품 상세 수정: name, summary, attributes, description, price, original_price, status — 검증 `price ≤ original_price`(02 D28), 본인 브랜드 상품 아니면 403. description은 서버측 sanitize(LLM 초안 포함 모든 입력 — XSS 차단). 에이전트 초안(05 draft 이벤트)의 "적용"도 FE가 이 API를 판매자 JWT로 호출 |
| S-4 | POST | /api/chat/seller | 🏪 | 판매자 에이전트 챗봇(SSE). brandId는 JWT 검증 후 BE가 DB에서 도출(클라이언트 전송 값 무시). AI 분석(매출 이상/감소 비교/행동/이탈)은 LLM이 S-1 계열 internal 집계 콜백 사용, 상세 수정은 초안(draft)+판매자 확인 — 05 §1-3 |

## 8. admin — ⚠️ 전부 고도화 (MVP 아님)

> 2026-07-09 팀 결정: 관리자 페이지는 MVP에서 전체 제외. 클레임 완료는 자동 승인 스케줄러가 대신하고(01 D10), 문의 답변·신고 처리는 MVP 기간에 일어나지 않는다(데모에 필요한 답변 완료 건은 시드로). 아래 표는 고도화 시 구현할 명세로 유지.

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| AD-1 | GET | /api/admin/inquiries | 🛡 | 문의 목록. query: status?, page |
| AD-2 | POST | /api/admin/inquiries/{id}/answer | 🛡 | 답변 등록 → status DONE |
| AD-3 | GET | /api/admin/reports | 🛡 | 후기 신고 목록. query: status?, page |
| AD-4 | POST | /api/admin/reports/{id}/process | 🛡 | 신고 처리. body: action(HIDE\|DELETE\|DISMISS) → review.status 변경 + report DONE |
| AD-5 | GET | /api/admin/claims | 🛡 | 클레임 목록. query: status?(기본 REQUESTED), page |
| AD-6 | POST | /api/admin/claims/{id}/approve | 🛡 | 승인 → 완료 상태 전이 (01 문서) |
| AD-7 | POST | /api/admin/claims/{id}/reject | 🛡 | 거절. body: rejectReason(필수) → 신청 전 상태 복귀 |

## 9. internal (LLM 전용 — 스키마 원본은 05 문서)

| # | Method | 경로 | 설명 |
|---|---|---|---|
| I-1 | GET | /internal/products/search | 상품 검색(키워드/카테고리/가격 범위) |
| I-2 | POST | /internal/cart/items | 챗봇 장바구니 담기 |
| I-3 | GET | /internal/products/popular | 인기 상품 (무관 질문 시 카드 유지용) |
| I-4 | GET | /internal/members/{id}/orders/status | 주문 상태 요약 (문의 챗봇용) |
| I-5 | POST | /internal/inquiries | 문의 접수 |
| I-6 | GET | /internal/seller/{brandId}/stats | 판매자 집계 (판매자 에이전트용) |
| I-7 | GET | /internal/seller/{brandId}/products/{productId} | 판매자 상품 상세 (수정 초안 생성용 읽기 전용 — 소유권 403, 쓰기 문 없음) |

## 10. 공통 에러 코드 (초기 세트)

`AUTH_LOGIN_FAILED` `AUTH_TOKEN_EXPIRED` `AUTH_FORBIDDEN` `MEMBER_EMAIL_DUPLICATE` `PRODUCT_NOT_FOUND` `CART_OPTION_REQUIRED` `CART_OPTION_INVALID` `ORDER_INVALID_TRANSITION` `CLAIM_NOT_ALLOWED` `CLAIM_ALREADY_REQUESTED` `REVIEW_NOT_ALLOWED` `REVIEW_ALREADY_EXISTS` `REVIEW_SELF_REPORT` `ADDRESS_LAST_UNDELETABLE` `CHAT_SESSION_EXPIRED` `INTERNAL_TOKEN_INVALID` — 구현 중 추가 시 이 목록에 반영.

## 11. 미결(OPEN) — 구현 전 확정 필요

- [ ] P-5 개인화 추천의 응답 형태(상품 ID 목록 vs 카드 데이터) — LLM 팀과 05 계약에서 확정
- [ ] 상품 상세 "바로 구매" 지원 여부 — 현재 O-1은 cartItemIds[]만 받아 장바구니 경유가 유일한 주문 경로. 피그마에 바로구매 버튼이 있으면 O-1에 items 직접 지정 확장 필요 — FE·기획 확인
- [x] ~~최근 본 상품 "개별 삭제(X)" 여부~~ — 기능 없음 확인(2026-07-10, 02 D29 종결)

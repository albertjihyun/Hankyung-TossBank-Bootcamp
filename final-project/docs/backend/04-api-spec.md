# 04. REST API 명세

> 기준: 「기능 정의 - 이소희」의 페이지별 기능에서 역산. 응답은 전부 03 문서의 envelope(`{success, data|error}`), 인증 규약도 03을 따른다.
> 표기: 🔓 인증 불필요 / 🔑 로그인 필요 / 🏪 SELLER / 🛡 ADMIN / ⚙ internal(서비스 토큰). `{}`는 path variable.
> LLM 콜백(⚙ `/internal/*`)과 채팅 프록시의 상세 스키마는 [05 LLM 연동 계약](05-llm-contract.md)이 원본이고 여기서는 목록만 둔다.

## 1. auth

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| A-1 | POST | /api/auth/signup | 🔓 | 회원가입. body: email, password, nickname, agreeTerms(true 필수), guestId? — 성공 시 자동 로그인(토큰 발급) + 게스트 승계 |
| A-2 | POST | /api/auth/login | 🔓 | 일반 로그인. body: email, password, guestId? |
| A-3 | POST | /api/auth/logout | 🔑 | RT 삭제 |
| A-4 | POST | /api/auth/refresh | 🔓(RT쿠키) | AT 재발급 |
| A-5 | GET | /api/auth/me | 🔑 | 내 정보(id, email, nickname, role) — FE 라우팅 가드용 |

- OAuth는 MVP 제외(2026-07-07 팀 결정). 고도화 도입 시 `GET /oauth2/authorization/{provider}` 추가.

- A-1 검증: 이메일 형식/중복(409 `MEMBER_EMAIL_DUPLICATE`), 비밀번호 규칙(8자+, 영문+숫자), 약관 미동의 400.
- A-2 실패는 계정 존재 여부 무관하게 통일 메시지(401 `AUTH_LOGIN_FAILED`) — 기능 정의 명시.
- 이메일 중복 확인은 별도 기능 없이 A-1의 409 응답으로만 처리 (2026-07-07 회의 — "기능만 일단 돌아가도록").

## 2. product / category / brand

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| P-1 | GET | /api/categories | 🔓 | 카테고리 전체 (메인 해시태그용) |
| P-2 | GET | /api/products/{id} | 🔓 | 상품 상세: 이미지 목록, 옵션 목록, 정가/판매가, summary/spec/description, 브랜드 요약, 평점 통계(평균·개수) — 조회 시 PRODUCT_VIEW 이벤트 적재(로그인/게스트 공통) |
| P-3 | GET | /api/products/{id}/reviews | 🔓 | 후기 목록. query: page, size, sort(latest\|rating) — status=VISIBLE만 |
| P-4 | GET | /api/products/popular | 🔓 | 인기 상품 N개(기본 12): 최근 7일 ORDER_CREATED 수 → 부족하면 PRODUCT_VIEW 수 → 그래도 부족하면 최신순으로 채움 (비로그인 메인·신규 회원 fallback 공용) |
| P-5 | GET | /api/products/recommended | 🔑 | "OO님을 위한 추천". LLM 프로필 기반 — 내부적으로 FastAPI 추천 API 호출(05 문서), 실패·프로필 없음 시 P-4로 fallback |
| P-6 | GET | /api/brands/{id} | 🔓 | 브랜드 소개 + 상품 목록. query: category?, sort(popular\|latest\|price_asc\|price_desc), page, size |

- P-2의 평점 통계는 review 테이블 실시간 집계(파생값 저장 금지).
- 연관 추천 2종(함께 구매/대체 상품)은 상세 화면 요소지만 추천 로직이 LLM 소관이라 05 문서의 FastAPI 호출로 정의(BE는 프록시 GET `/api/products/{id}/related`).

## 3. cart

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| C-1 | GET | /api/cart | 🔑 | 내 장바구니: 아이템(상품 요약, 옵션, 수량, 현재가), 합계는 FE 계산 아님 — data에 totalOriginal/totalSale/discount 포함 |
| C-2 | POST | /api/cart/items | 🔑 | 담기. body: productId, optionId?, quantity — 동일 상품+옵션 존재 시 수량 합산. CART_ADD(manual) 이벤트 |
| C-3 | PATCH | /api/cart/items/{id} | 🔑 | 수량 변경. body: quantity(≥1) |
| C-4 | DELETE | /api/cart/items/{id} | 🔑 | 삭제 (복수 삭제는 FE에서 반복 호출 — 데모 규모) |

- 옵션 있는 상품에 optionId 누락 → 400 `CART_OPTION_REQUIRED`. 본인 아이템 아니면 403.

## 4. order / claim

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| O-1 | POST | /api/orders | 🔑 | 주문 생성+모의 결제 한 번에. body: cartItemIds[], addressId 또는 address 직접 입력, paymentMethod — 처리: PENDING 생성 → 스냅샷 복사 → mock 결제 판정 → PAID(장바구니 차감·ORDER_CREATED 이벤트) 또는 PAYMENT_FAILED. 응답: orderId, status |
| O-2 | POST | /api/orders/{id}/retry-payment | 🔑 | 실패 주문 재결제. body: paymentMethod — PENDING/PAYMENT_FAILED에서만 |
| O-3 | GET | /api/orders | 🔑 | 내 주문 목록: 대표 상태(01 §4), 아이템 요약. query: page, size |
| O-4 | GET | /api/orders/{id} | 🔑 | 주문 상세: 아이템별 상태, 배송지 스냅샷, 금액, 아이템별 가능 액션(canCancel/canReturn/canExchange/canReview — 01 §3 매트릭스를 서버가 계산해 내려줌) |
| O-5 | POST | /api/order-items/{id}/claims | 🔑 | 클레임 신청. body: type(CANCEL\|RETURN\|EXCHANGE), reason? — 01 매트릭스 위반 시 400 `CLAIM_NOT_ALLOWED`, 활성 클레임 존재 시 409 |
| O-6 | GET | /api/claims | 🔑 | 내 취소·반품·교환 내역. query: page, size |

- O-4가 가능 액션을 내려주는 이유: 상태 매트릭스 판단을 FE에 중복 구현하지 않기 위해(단일 진실은 서버). FE는 boolean만 보고 버튼 노출.
- O-1에서 결제까지 한 API로 묶은 이유: 모의 결제라 "생성→별도 결제 승인" 2단계로 나눌 외부 경계가 없음. 실 PG 전환 시(01 D7) 이 API를 생성/승인으로 쪼개는 게 교체 지점.

## 5. mypage (review / wishlist / recent / address / inquiry)

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| M-1 | POST | /api/reviews | 🔑 | 후기 작성. body: orderItemId, rating(1~5), content — 자격(DELIVERED/EXCHANGED+미작성) 위반 400 `REVIEW_NOT_ALLOWED` |
| M-2 | GET | /api/reviews/me | 🔑 | 내가 쓴 후기 목록 |
| M-3 | POST | /api/reviews/{id}/reports | 🔑 | 후기 신고. body: reason — 중복 신고 409 |
| M-4 | GET | /api/wishlist | 🔑 | 찜 목록 |
| M-5 | POST | /api/wishlist | 🔑 | 찜 추가. body: productId — 중복 409. WISHLIST_ADD 이벤트 |
| M-6 | DELETE | /api/wishlist/{productId} | 🔑 | 찜 해제 |
| M-7 | GET | /api/products/recent | 🔑 | 최근 본 상품 (user_event 기반, 중복 제거 최신 20개) |
| M-8 | GET/POST/PATCH/DELETE | /api/addresses(/{id}) | 🔑 | 배송지 CRUD. is_default 지정 시 기존 기본 해제(같은 트랜잭션) |
| M-9 | GET | /api/inquiries/me | 🔑 | 내 문의 내역(읽기 전용): 내용, 상태, 답변 |
| M-10 | PATCH | /api/members/me | 🔑 | 프로필 수정: nickname |

- 문의 "접수"는 사용자 API가 없다 — 문의 챗봇(LLM)이 ⚙ internal 콜백으로만 생성(문의 단일 채널 원칙, 05 문서).

## 6. chat (프록시 — 상세는 05 문서)

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| CH-1 | POST | /api/chat/sessions | 🔓(게스트 허용) | 세션 발급(Redis TTL 10분). "새 대화" 버튼도 이걸 다시 호출 |
| CH-2 | POST | /api/chat | 🔓(게스트 허용) | 추천 챗봇 메시지 전송, SSE 스트림 응답. 게스트도 무제한 사용(횟수 제한 폐지 — 2026-07-07 회의), 개인화만 미적용 |
| CH-3 | POST | /api/chat/cs | 🔑+🔓 | 문의 챗봇(고객센터) 메시지. 비로그인은 일반 안내만(주문 질문 시 로그인 유도 메시지는 LLM 측 처리) |

## 7. seller

| # | Method | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| S-1 | GET | /api/seller/summary | 🏪 | 자사 요약: 기간별 매출/주문수(order_item 집계), 상품별 조회수·담김수·판매수(user_event+order_item) query: from, to |
| S-2 | GET | /api/seller/orders | 🏪 | 자사 상품이 포함된 주문 아이템 목록 |
| S-3 | PATCH | /api/seller/products/{id} | 🏪 | 자사 상품 상세 수정: name, summary, spec, description, sale_price, status — 본인 브랜드 상품 아니면 403 |
| S-4 | POST | /api/chat/seller | 🏪 | 판매자 에이전트 챗봇(SSE). AI 분석(매출 이상/감소 비교/행동/이탈)은 LLM이 S-1 계열 internal 집계 콜백을 사용 — 05 문서 |

## 8. admin

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

## 10. 공통 에러 코드 (초기 세트)

`AUTH_LOGIN_FAILED` `AUTH_TOKEN_EXPIRED` `AUTH_FORBIDDEN` `MEMBER_EMAIL_DUPLICATE` `PRODUCT_NOT_FOUND` `CART_OPTION_REQUIRED` `ORDER_INVALID_TRANSITION` `CLAIM_NOT_ALLOWED` `CLAIM_ALREADY_REQUESTED` `REVIEW_NOT_ALLOWED` `REVIEW_ALREADY_EXISTS` `CHAT_SESSION_EXPIRED` `INTERNAL_TOKEN_INVALID` — 구현 중 추가 시 이 목록에 반영.

## 11. 미결(OPEN) — 구현 전 확정 필요

- [ ] P-5 개인화 추천의 응답 형태(상품 ID 목록 vs 카드 데이터) — LLM 팀과 05 계약에서 확정

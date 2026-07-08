# 05. LLM(FastAPI) 연동 계약 — 초안 v0.1

> ⚠️ **이 문서는 LLM 팀과 합의 전의 백엔드 측 제안이다.** 합의 후 버전을 올리고, 노션 API 명세서에 동기화한다. OPEN 표시 항목은 합의 대상.
> 원칙: 대화 내용은 양쪽 다 DB에 저장하지 않는다. 개인화 프로필은 LLM 팀 소유. 커머스 데이터의 쓰기는 전부 BE internal API 경유.

## 0. 왜 이 구조인가 (요약)

- **BE 단일 진입점**: FE→FastAPI 직접 호출 금지. 인증·게스트 제한·로깅을 BE 한 곳에서 처리하기 위함.
- **툴 콜백 패턴**: FastAPI가 상품 검색·담기 등이 필요하면 BE `/internal/*`을 호출. LLM이 DB에 직접 붙지 않으므로 스키마 변경이 서로를 깨지 않는다.
- **인증**: BE→FastAPI 요청과 FastAPI→BE 콜백 모두 `X-Internal-Token` 헤더(공유 시크릿, 각자 .env).

## 0-1. 권한 모델 — internal에 역할(Role) 검사가 없는 이유

- **신원 검증은 `/api` 경계에서 JWT로 딱 한 번.** internal 요청의 `userId`/`brandId`는 FastAPI가 주장하는 값이 아니라, BE가 JWT를 검증한 뒤 채팅 요청에 실어 보낸 값의 **메아리**다. FastAPI는 신원을 만들어내지 않고 되돌려줄 뿐 — 신뢰의 근원은 항상 JWT.
- **역할 검사 대신 능력 화이트리스트.** 위험 3축 — ① 금전/비가역성 ② 타인 영향(피해가 본인 계정 밖으로 나가는가) ③ 권한 변경(클레임 승인·후기 삭제 등 규칙을 바꾸는 행위) — 에 걸리는 능력은 이 표면에 존재하지 않는다. 검사할 권한이 없는 게 아니라 검사가 필요한 문 자체가 없음.
- **사용자 JWT를 FastAPI에 위임하는 것은 금지.** 위임하는 순간 LLM의 권한 상한이 "이 6개"에서 "그 사용자가 할 수 있는 전부"로 확장되고, 프롬프트 인젝션의 피해 반경이 같이 커진다.
- **3축에 걸리는 능력이 필요해지면** (예: 고도화의 "주문까지 자동화") 거절이 아니라 위험을 깎아서 문턱 아래로 내린다. 검토 순서: ① **초안 + 사용자 확인** — 준비(주문서 초안 생성)는 internal 신규 문으로, 실행은 FE 확인 UI에서 사용자 본인의 JWT로 `/api` 호출 ② 스코프·한도·시간이 박힌 일회용 권한 ③ 행위의 가역화(취소 가능 시간창). 어떤 경우에도 최종 위험 행위가 서비스 토큰만으로 실행되게 하지 않는다.

## 1. 채팅: BE → FastAPI

### 1-1. 추천 챗봇 `POST {LLM_BASE_URL}/chat`

```json
{
  "sessionId": "uuid",          // BE가 발급·TTL 관리(10분 sliding). 새 sessionId = 새 대화
  "userId": 123,                 // 로그인 사용자. 게스트면 null
  "guestId": "uuid|null",        // 게스트 식별(개인화 없이 응답)
  "channel": "SHOPPING",         // SHOPPING | CS | SELLER
  "message": "유럽여행 가는데 필요한 거 추천해줘"
}
```

- 멀티턴 맥락은 sessionId 기준으로 **FastAPI가 인메모리/자체 스토어에 유지** (BE는 메시지를 저장하지 않음). 세션 만료 시 BE가 `DELETE {LLM_BASE_URL}/sessions/{id}` 호출로 정리 통지. **(OPEN: 정리 통지 방식 vs FastAPI 자체 TTL)**
- 카테고리 진입(메인에서 카테고리 클릭)은 별도 필드 없이 message로 전달: FE가 `"[카테고리] 주방용품 보여줘"` 형태로 첫 메시지 구성. **(OPEN: 전용 필드로 분리할지)**

### 1-2. 응답: SSE 스트림

이벤트 타입 6종. BE는 그대로 FE에 패스스루한다.

```
event: token       data: {"text": "유럽여행이라면 "}          // 답변 텍스트 조각 (스트리밍)
event: conditions  data: {"items": ["기념일", "호텔 레스토랑", "우아한", "원피스"]}
                                                             // LLM이 발화에서 추출한 조건 — FE가 결과 상단에 제거 가능한 칩으로 표시
event: products    data: {"groups": [                        // 상품 카드 (그룹핑 지원)
                    {"title": "선크림", "items": [
                      {"productId": 1, "name": "…", "brandName": "더센트", "price": 12900, "originalPrice": 15000,
                       "imageUrl": "…", "rating": 4.8, "reviewCount": 2847, "reason": "지성 피부에 맞는 가벼운 제형"}
                    ]}
                  ]}
event: action      data: {"type": "CART_ADDED", "message": "무선 키보드 1개를 장바구니에 담았어요", "cartItemId": 55}
event: done        data: {"finishReason": "stop"}
event: error       data: {"code": "LLM_TIMEOUT", "message": "잠시 후 다시 시도해주세요"}
```

- `conditions`: 디자인 시안의 "조건 칩" UI 지원. **칩 X 제거 시 FE는 후속 메시지로 전달** — `message: "[조건 제거] 우아한"` 형태의 규약 문자열(같은 세션이라 LLM이 맥락 유지, 재추천 후 갱신된 conditions·products 재발행). 별도 API 없음.
- `products.groups`: 상황 기반 추천의 "카테고리별 묶음" 요구를 지원(단일 추천은 그룹 1개). `productId`는 BE 상품 ID — 카드의 상세 이동은 FE가 `/products/{id}`로.
- 카드 필드(브랜드/정가/평점 포함)는 FastAPI가 internal 검색 응답(I-1)에서 그대로 채워 반환(FE가 상품별 재조회하지 않게). 카드의 찜 버튼은 FE가 M-5(찜 추가)를 직접 호출 — LLM 무관.
- `action`: 담기 등 부수효과의 결과 통지. 실패 시 `type: "CART_ADD_FAILED"` + 사유.

### 1-3. CS/판매자 챗봇

- 같은 `/chat` 엔드포인트에 `channel: "CS"` / `"SELLER"`. 분기는 FastAPI 내부(프롬프트·툴셋 차이).
- CS: 비로그인(userId null)이면 일반 안내만, 주문 질문엔 로그인 유도 문구로 답변.
- SELLER: BE가 요청에 `brandId` 추가로 전달(판매자 인증 후). 분석 질문은 I-6 집계 콜백 사용.

## 2. 콜백: FastAPI → BE `/internal/*`

공통: `X-Internal-Token` 필수. 응답은 BE 공통 envelope. 타임아웃 권장 3s. **여기 없는 쓰기 작업은 존재하지 않는다** (주문 생성·클레임·후기는 LLM이 못 함 — 결제 자동화 범위는 "담기까지").

### I-1. 상품 검색 `GET /internal/products/search`
- query: `keyword?`(상품명+summary+spec LIKE), `categoryName?`, `minPrice?`, `maxPrice?`, `brandName?`, `size`(기본 10, 최대 30)
- 응답 item: `productId, name, salePrice, originalPrice, imageUrl, categoryName, brandName, summary, spec(JSON), rating, reviewCount, options[]`
- spec까지 반환하는 이유: LLM이 "린넨 소재만" 같은 세밀 조건을 후처리 필터링할 수 있게(서버는 후보만 좁힘 — 02 D7).

### I-2. 장바구니 담기 `POST /internal/cart/items`
- body: `{ "userId": 123, "productId": 1, "optionId": null, "quantity": 1 }`
- 게스트(userId null) 요청은 403 `CART_LOGIN_REQUIRED` — LLM은 이를 받으면 "로그인하면 담아드릴게요"로 답변.
- 옵션 필요한데 optionId 없으면 400 `CART_OPTION_REQUIRED` + options 목록 반환 → LLM이 "어떤 색상으로 담을까요?"로 되물음.
- 성공 응답에 cartItemId — action 이벤트에 사용. `CART_ADD(via: chat)` 이벤트는 BE가 적재.

### I-3. 인기 상품 `GET /internal/products/popular?size=12`
- 무관 질문 시 카드 영역 유지용. 응답 형식 I-1과 동일.

### I-4. 주문 상태 `GET /internal/members/{userId}/orders/status?recent=3`
- 응답: 주문별 `{ orderId, orderedAt, representativeStatus, items: [{ productName, status, statusText }] }`
- statusText는 한국어 표시 문자열(예: "배송중") — LLM이 그대로 인용.

### I-5. 문의 접수 `POST /internal/inquiries`
- body: `{ "userId": 123, "content": "챗봇이 요약한 문의 내용" }` — 게스트 403(문의는 로그인 필요, 기능 정의 9번).
- 문의 단일 채널 원칙: 이 API가 문의 생성의 유일한 경로.

### I-6. 판매자 집계 `GET /internal/seller/{brandId}/stats?from=&to=&groupBy=day|product`
- 응답: 매출/주문수/조회수/담김수/판매수 시계열 또는 상품별. **LLM에 raw 로그를 주지 않고 집계만 준다** — text2SQL류의 실패 모드(잘못된 쿼리, 타 판매자 데이터 접근)를 계약 수준에서 차단.

## 3. 비기능 규약

| 항목 | 값 |
|---|---|
| BE→FastAPI 타임아웃 | 연결 5s / 스트림 전체 60s |
| FastAPI→BE 콜백 타임아웃 | 3s (콜백 실패 시 LLM은 해당 기능 없이 답변 지속) |
| 재시도 | 양방향 자동 재시도 없음(중복 담기·중복 과금 방지). 실패는 사용자에게 노출하고 수동 재시도 |
| 게스트 제한 | 없음 — 횟수 제한 폐지(2026-07-07 회의). 게스트는 개인화 없이 응답 |
| 장애 시 | FastAPI 다운 → BE가 SSE error 이벤트(`LLM_UNAVAILABLE`) 반환. 상품 조회 등 비채팅 기능은 정상 동작 |

## 4. OPEN — LLM 팀 합의 필요 목록

- [ ] **프로필 추출 저장 시점** (세션 만료 시? 매 N턴?) — 기능 정의에도 미확정으로 표시됨
- [ ] 세션 만료 통지 방식 (BE→DELETE 호출 vs FastAPI 자체 TTL)
- [ ] 카테고리 진입을 message 관성으로 갈지 전용 필드로 갈지
- [ ] P-5 개인화 추천(메인) API: `GET {LLM_BASE_URL}/recommendations?userId=` 형태 제안 — 응답이 상품 ID 목록이면 BE가 카드 데이터 조립
- [ ] 상세페이지 연관 추천 2종(함께 구매/대체)의 소스: LLM 생성 vs BE 규칙 기반(같은 카테고리 인기순) — MVP는 BE 규칙 기반 제안
- [ ] SSE 이벤트 스키마 필드명 최종 확정

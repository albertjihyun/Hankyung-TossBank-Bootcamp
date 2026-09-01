# ADR-0005: 채팅 SSE를 Spring 패스스루에서 FE↔FastAPI 직결로 전환

- 결정일: 2026-07-15 방향 결정, 2026-07-16 확정
- 상태: 채택 (기존 패스스루 설계 폐기)
- 근거 문서: `jarvis-backend/docs/backend/03-architecture.md` D5, `05-llm-contract.md` §0·§1-0; `jarvis-frontend/docs/architecture-chat.md`

## 맥락

원래 설계는 채팅 스트림도 "FE는 BE만 호출" 원칙대로 Spring이 중계(`SseEmitter` + WebClient 패스스루)하는 구조였다. 그러나 패스스루는 **스트림 하나당 소켓 2개**(FE↔Spring, Spring↔FastAPI)를 릴레이해야 해서 대규모 트래픽에서 Spring이 병목이 된다.

## 결정

**채팅 SSE 읽기 경로만 FE↔FastAPI 직결**로 전환한다. 신원은 여전히 Spring이 소유한다:

- FE가 `POST /api/chat/sessions`로 세션을 받을 때 Spring이 회원 JWT 또는 게스트 쿠키를 검증하고, **단명(30~60초) RS256 서명 티켓**(`scope: chat:stream`)을 함께 발급 — 추가 왕복 없음
- FastAPI는 Spring이 공개한 **JWKS로 무상태 검증** (Redis/DB 안 봄)
- **쓰기는 불변** — 담기 등 모든 액션은 여전히 FastAPI → Spring `/internal/*` + 서비스 토큰

## 기각된 대안

- **패스스루 유지**: 소켓 릴레이 병목. 폐기.
- **액세스 토큰을 그대로 전달**: `EventSource`(GET)는 커스텀 헤더를 못 실어 AT가 쿼리스트링에 노출되고(액세스 로그·히스토리), FastAPI를 AT의 `aud`에 추가하면 토큰 분리가 무너진다.
- **1회용 티켓**: 상태 저장이 필요해짐. 짧은 TTL을 근사치로 수용.

## 트레이드오프·결과

- FastAPI가 인터넷에 공개되며 TLS·CORS·rate limit·티켓 검증·SSE 배관(`X-Accel-Buffering: no`, 하트비트, 백프레셔, **클라이언트 이탈 시 LLM 생성 취소** — 비용) 책임이 FastAPI로 이동.
- 상품 카드는 SSE에 싣지 않는다("Path B") — 스트림에는 상관 키만 흘리고, 추천 목록은 콜백(I-21)으로 DB에 영구 저장한 뒤(2026-07-30 ERD 변경) FE가 CH-5로 완전한 카드를 재조회한다. 콜백이 실패하면 `products.ready`를 내보내지 않아 FE가 존재하지 않는 listId를 조회하는 일이 없다. 표시 권위를 Spring 한 곳에 유지하는 장치.
- 게스트→회원 전환 시 세션 승계는 CH-7/I-23으로 별도 해결 ([ADR-0009](0009-guest-logs-immutable.md)).

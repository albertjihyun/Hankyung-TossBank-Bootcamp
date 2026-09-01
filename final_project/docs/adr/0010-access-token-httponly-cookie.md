# ADR-0010: AT를 HttpOnly 쿠키로 이전 + 토큰 에포크 킬스위치

- 결정일: 2026-08-04 (FE와 동시 배포)
- 상태: 채택 (헤더 방식 병행 없이 폐지)
- 근거 문서: `jarvis-backend/docs/backend/03-architecture.md` D3, `07-redis-design.md` §3-2; `jarvis-frontend/docs/architecture-auth.md`

## 맥락

액세스 토큰이 `Authorization: Bearer` 헤더 + JS 접근 가능한 저장소에 있었다. XSS에 토큰이 노출되고, SSR 첫 페인트가 로그인 상태를 알 수 없고, 로그아웃을 서버가 확정적으로 종결시킬 수 없었다.

## 결정

AT를 **HttpOnly 쿠키**로 이전하고 헤더 경로는 폐지한다(병행 없음). 함께 정한 서브 결정들:

1. **쿠키 Max-Age(14일) ≠ 토큰 수명(30분)** — 일부러 다르게 둔다. 맞추면 30분에 브라우저가 쿠키를 지워 서버가 `AUTH_TOKEN_EXPIRED`("리프레시해라") 대신 `AUTH_REQUIRED`("로그인해라")를 답하게 되고, 유효한 14일짜리 RT가 있는데도 30분마다 강제 로그아웃된다. 조용한 갱신(silent refresh)의 성립 조건.
2. **토큰 에포크 킬스위치** — Redis `auth:epoch:{memberId}`에 무효화 시각을 두고 `iat`가 그보다 이르면 거부. "탈취된 AT가 남은 30분간 유효"한 창을 닫는다. **Redis 장애 시 fail-open** — 신원은 이미 서명으로 성립해 있으므로, fail-closed면 Redis 장애가 전 사용자 로그아웃이 된다.
3. **CSRF 토큰 미도입** — `SameSite=Lax`가 크로스사이트 상태 변경을 막는다는 전제는 "상태를 바꾸는 GET이 없다"이므로, 그 전제를 `csrf.disable()` 주석에 명문화해 깨는 순간 재검토를 강제.
4. **RT는 JWT가 아니라 불투명 256비트 난수** — 진실은 어차피 DB 행이므로 자가 검증이 무의미하고, 유출될 클레임도 없다.

## 트레이드오프·결과

- FE는 "내가 로그인했는지"를 JS로 물을 수 없게 됐다 — 부팅 시 복원 흐름과 401 2종 분기(`AUTH_REQUIRED` vs `AUTH_TOKEN_EXPIRED`)로 해결 (`architecture-auth.md`).
- 401 정책이 "FE가 리프레시 후 재전송한다"를 전제하는데 이벤트 SDK에는 그 절반이 구현되지 않아, 30분 경계마다 활동 사용자의 이벤트가 유실되는 문제가 뒤에 발견됐다 — 401에만 1회 재시도 예외를 뚫고 테스트로 확장을 봉인 (`jarvis-frontend/docs/analytics-401-retry.md`).
- 관련 선행 결정(07-18): RT·guest_id 쿠키에 `Secure` 부여 — SameSite는 CSRF를 막지 수동적 네트워크 캡처를 막지 않는다.

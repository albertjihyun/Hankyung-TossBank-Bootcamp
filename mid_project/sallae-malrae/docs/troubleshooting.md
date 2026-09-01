# 트러블슈팅 기록

프로젝트 중 발생한 기술적 문제의 원인 분석과 해결 과정, 그리고 발표 시점까지 해결하지 못한 문제의 원인 진단과 해결 방향을 기록한다. 기준 코드는 2026-05-20 배포 버전.

## 1. 24시간 전 이메일 알림 미발송 (해결)

**증상** — 쿨링오프 마감 24시간 전 알림 이메일이 발송되지 않음.

**1단계: email_logs 조회**

```sql
SELECT * FROM email_logs ORDER BY attempted_at DESC;
```

`expire` 타입 로그는 정상 기록되어 있으나 `before_24h` 타입 로그가 전혀 없음 → 발송 시도 자체가 없었던 것으로 판단.

**2단계: notifier.js 분석** — before_24h 쿼리의 WHERE 조건(ADR-006의 컷오프 조건) 자체는 정상이었다. 문제는 개발 중 `dev-notify.js`로 기능 테스트를 하면서 실제 `email_logs`에 `before_24h` 레코드가 삽입됐고, 실 발송 시점에 notifier가 "이미 발송됨"으로 판단해 스킵한 것.

**3단계: 실 데이터 검증**

```sql
SELECT id, created_at, expire_at,
       expire_at - INTERVAL 24 HOUR AS notify_24h_at,
       (expire_at - INTERVAL 24 HOUR > created_at + INTERVAL 1 HOUR) AS cutoff_pass
FROM items;
```

테스트 실행 시 삽입된 로그가 실 발송을 막고 있음을 확인.

**조치** — 테스트는 반드시 `DRY_RUN=1` 옵션 사용(실제 발송·기록 없이 콘솔 출력만). 배포 전 email_logs 테이블 정리. 개발/운영 환경 분리 원칙 정립.

**설계 포인트** — `email_logs`의 `UNIQUE(item_id, type)` + `INSERT IGNORE` 구조는 중복 발송을 방어하지만, 테스트 데이터가 운영 로그를 오염시키는 부작용도 있다. 이를 DRY_RUN 모드로 방어한다.

## 2. AT/RT 인증 갱신 관련 — 인지된 미해결 이슈 4건

발표 시점 배포본에 남아 있는 이슈들이다. 각각 원인을 진단하고 해결 방향까지 설계했으나 일정상 반영하지 못했다.

### 배경: 토큰 구조

| 토큰 | 유효기간 | 저장 위치 | 역할 |
|---|---|---|---|
| Access Token (AT) | 15분 | HttpOnly 쿠키 | API 호출 인증 |
| Refresh Token (RT) | 7일 | HttpOnly 쿠키 + DB (bcrypt 해시) | AT 만료 시 재발급 |

정상 흐름: AT 만료 → RT로 새 AT+RT 재발급 → 실패 시에만 로그인 페이지 이동.

### 2-1. 클라이언트 fetch에서 AT 만료 시 RT 갱신 없이 로그인 이동

**증상** — 쿨링오프 페이지에 15분 이상 머문 뒤 등록·상태 변경·삭제 액션을 하면, RT가 유효한데도 로그인 페이지로 강제 이동.

**원인** — 미들웨어 matcher는 페이지 경로만 포함하므로 클라이언트 컴포넌트의 fetch 호출은 미들웨어를 통과하지 않는다. 각 핸들러가 UNAUTHORIZED를 받으면 RT 갱신 시도 없이 바로 로그인으로 이동한다.

| 요청 유형 | 미들웨어 개입 | AT 만료 시 |
|---|---|---|
| 페이지 이동 / 새로고침 | O | RT로 자동 갱신 후 진행 |
| 클라이언트 fetch 호출 | X | 갱신 시도 없이 UNAUTHORIZED |

대시보드는 서버 컴포넌트가 DB를 직접 조회하는 구조라 클라이언트 fetch가 없어 영향이 없다.

**해결 방향** — `lib/fetchWithAuth.js` 유틸 신설. UNAUTHORIZED 응답 시 `/api/auth/refresh` 호출 후 원래 요청을 1회 재시도하고, RT도 만료된 경우에만 로그인으로 이동. 적용 대상: `coolingoff/page.jsx`(fetchItems, handleDelete, handleStatusChange), `coolingoff/new/page.jsx`(handleSubmit), `LevelShareModal.jsx`(handleShare).

### 2-2. AT 쿠키 만료(maxAge) 시 미들웨어가 RT 갱신을 시도하지 않음

**증상** — 페이지 이동/새로고침에서도 15분 후 로그인 페이지로 이동.

**원인** — AT 쿠키의 maxAge가 15분이라 AT JWT와 동시에 만료된다. 브라우저가 만료 쿠키를 삭제하면 다음 요청에서 AT가 아예 없는 상태가 되는데, 미들웨어의 `!token` 분기는 RT 갱신 시도 없이 바로 로그인으로 리다이렉트한다. RT 갱신 로직은 "AT는 있는데 JWT가 만료된" catch 분기에만 존재한다.

**해결 방향** — `!token` 분기와 catch 분기를 통합. AT가 없거나 만료면 동일하게 RT 갱신을 시도하고, 실패 시에만 로그인 이동.

### 2-3. 클라이언트 사이드 네비게이션에서 갱신 쿠키 미반영 (빈 화면)

**증상** — AT 만료 후 Link 클릭(클라이언트 사이드 네비게이션) 시 빈 화면. 새로고침(F5)은 정상.

**원인** — 미들웨어가 갱신 쿠키를 `res.headers.append('Set-Cookie', ...)`로 이어붙이는 방식은 풀 페이지 로드에서는 동작하지만, App Router의 클라이언트 사이드 네비게이션(RSC payload fetch)에서는 브라우저 쿠키에 반영되지 않을 수 있다.

**해결 방향** — `headers.append` 대신 `res.cookies.set()` API로 교체. Set-Cookie 문자열을 파싱해 name/value/옵션(httpOnly, secure, maxAge, path, sameSite)으로 분해 후 Next.js 라우팅 레이어에서 직접 세팅.

### 2-4. 루트(/) 경로에서 로그인 상태인데 랜딩 페이지 노출

**증상** — 로그인 상태에서 루트 접근 시 대시보드로 이동하지 않고 랜딩 페이지 노출. `/dashboard` 직접 접근은 정상.

**원인** — 루트 경로 체크가 `if (isRoot && token)` 조건이라 AT 쿠키가 만료·삭제된 경우 RT가 살아 있어도 조건에서 걸러진다. 보호 경로에는 RT 갱신 로직이 있지만 루트 경로에는 없다.

**해결 방향** — 루트 경로에도 보호 경로와 동일한 RT 갱신 로직 적용. AT/RT 둘 다 없는 경우(비로그인)에만 랜딩 페이지 노출.

### 미해결 항목 요약

| 파일 | 변경 필요 내용 |
|---|---|
| `src/lib/fetchWithAuth.js` | 신규 생성 — UNAUTHORIZED 시 RT 갱신 후 재시도 |
| `src/middleware.js` | AT 없을 때 RT 갱신 시도 + 쿠키 세팅 방식 개선 + 루트 경로 RT 갱신 |
| `src/app/coolingoff/page.jsx` | fetchWithAuth 적용 |
| `src/app/coolingoff/new/page.jsx` | fetchWithAuth 적용 |
| `src/components/dashboard/LevelShareModal.jsx` | fetchWithAuth 적용 |

## 3. 그 외 인지된 한계

- **Rate Limiter가 인메모리** (`lib/rateLimit.js`) — 서버 재시작 시 카운터 초기화, 다중 인스턴스 환경에서 우회 가능. 스케일링 시 Redis 기반으로 교체 필요.
- **DB 커넥션 풀 고갈 처리 없음** (connectionLimit: 10) — 동시 요청 초과 시 구조화되지 않은 500. 쿼리 타임아웃도 미설정.
- **middleware.js 파일명** — Next.js 16에서 `middleware` 컨벤션이 deprecated되고 `proxy`가 권장된다. 파일의 실제 역할은 인증 가드라 이름 의미가 어긋나는 문제가 있어 rename을 검토했으나, 배포본에는 middleware.js로 남아 있다.

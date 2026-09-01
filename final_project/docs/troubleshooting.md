# 트러블슈팅 기록

> 팀 노션 자료실의 버그리포트를 이관한 것. 각 건의 최종 해결 상태는 현재 코드 기준으로 확인해 반영했다. (백엔드 저장소의 `docs/lessons.md` 류 기록과 같은 증상 → 원인 → 해결 → 교훈 형식.)

---

## 1. GET /api/seller/summary 전면 500 — MariaDB 예약어 `lines` + 트랜잭션 rollback-only 전파

- 발생: 2026-08-08 (배포 서버, 원인 확정은 스택트레이스 기준)
- 유입 시점: 2026-08-07 "S-1에 AI 추천 성과 블록 추가" 커밋부터

### 증상

판매자 대시보드 `GET /api/seller/summary` 가 파라미터와 무관하게 **모든 요청에서 500**. 같은 SELLER 세션에서 `/api/seller/orders`, `/api/seller/products` 는 정상 200 — 인증·권한 문제가 아니라 summary 단독 문제.

```json
{"success":false,"error":{"code":"INTERNAL_ERROR","message":"서버 내부 오류가 발생했습니다."}}
```

### 원인 1 — `lines` 는 MariaDB 예약어

`OrderItemRepository.aggregateAiAttribution` 네이티브 쿼리의 파생 테이블 별칭이 `lines` 였다. `LINES` 는 MariaDB 예약어(`LOAD DATA ... LINES TERMINATED BY` 구문용)라 백틱 없이 식별자로 쓸 수 없고, 배포 DB(MariaDB 11.4)에서 그대로 1064 문법 오류가 났다. 데이터 조건이 아닌 문법 오류이므로 어떤 브랜드·기간에도 100% 실패.

### 원인 2 — try/catch 실패 격리가 트랜잭션 안에서 무력화됨 (더 중요한 원인)

`aiAttribution()` 은 전체가 `try/catch` 로 감싸여 실패 시 `null` 을 반환하고 "이 블록만 비우고 대시보드는 내려보낸다"는 설계였고, catch 자체는 동작했다. 그런데도 500이 난 경로:

1. 서비스가 클래스 레벨 `@Transactional(readOnly = true)` — 트랜잭션 안에서 실행
2. 쿼리 실패 → `SQLSyntaxErrorException` → Spring이 `InvalidDataAccessResourceUsageException` (RuntimeException)으로 변환
3. **예외가 트랜잭션 경계를 지나는 순간 Spring이 트랜잭션을 rollback-only 로 마킹**
4. 서비스의 catch가 예외를 삼켜도 마킹은 이미 끝났고 되돌릴 수 없음
5. `summary()` 정상 반환 → 커밋 시도 → `UnexpectedRollbackException` → 전역 예외 핸들러가 `INTERNAL_ERROR` 응답

즉 **같은 트랜잭션 안에서는 리포지토리 예외를 try/catch로 잡아도 실패 격리가 성립하지 않는다.**

### 왜 로컬/CI에서 안 걸렸나

`SellerSalesServiceTest` 가 리포지토리를 Mock 처리해 실제 SQL이 DB에 파싱되지 않았다. 네이티브 쿼리 문법은 단위 테스트로는 검증되지 않는다 — 실제 DB에 한 번이라도 실행했다면 즉시 발견됐을 오류.

### 해결 (코드 반영 확인됨)

1. 별칭을 `line_amounts` 로 변경 (`OrderItemRepository.java`) — 예약어를 굳이 식별자로 쓸 이유가 없으므로 백틱 대신 이름 변경
2. 집계 호출을 `SellerAttributionService` 별도 빈으로 분리하고 `@Transactional(propagation = REQUIRES_NEW)` 적용 — 이 집계가 다른 이유(데이터 증가로 인한 타임아웃 등)로 실패해도 바깥 트랜잭션을 오염시키지 않아, "실패해도 대시보드 나머지는 내려보낸다"는 격리가 실제로 성립하게 됨

### 교훈

- 부분 실패를 격리하려면 catch만으로는 부족하고 **트랜잭션 경계를 분리**해야 한다 (REQUIRES_NEW 별도 빈)
- 네이티브 쿼리는 Mock 단위 테스트로 문법이 검증되지 않는다 — 통합 테스트(Testcontainers 등) 또는 실 DB 1회 실행이 필요
- DB 예약어는 방언(MySQL/MariaDB)마다 다르다 — 별칭·컬럼명은 예약어 목록을 피해서

---

## 2. M-12 취향 수정 전면 500 — 업스트림(AI 서버) 오류가 INTERNAL_ERROR 로 합쳐져 FE에서 구분 불가

- 발생: 2026-08-10 (로컬에서 취향 프로필 실 API 전환 테스트 중 발견)

### 증상

마이페이지 취향 프로필에서 항목 수정(`PATCH /api/profile/graph/edges/{edgeId}`, M-12)이 body 내용과 무관하게 **100% 500**. FE 요청은 계약(M-12 명세)을 전부 지키고 있음을 DevTools 전문으로 확인 — 계약 위반이면 400이어야 하므로 검증 이후 단계에서 터진 것.

### 진단 과정 — 대조군으로 원인 범위 좁히기

결정적 단서: **같은 회원 · 같은 edgeId · 같은 `If-Match` 값으로 M-13(DELETE)은 정상 200.** 이 사실이 다음을 전부 배제했다.

| 배제된 원인 | 근거 |
|---|---|
| `If-Match` 파싱 실패 | 같은 값으로 삭제 성공 |
| edgeId 조회 실패 | 삭제가 그 항목을 정확히 찾아 지움 |
| 인증·권한·경로 매핑 | 같은 경로·같은 세션으로 통과 |
| 로컬 Next 프록시 | 삭제도 같은 프록시 통과 |
| 회원 그래프 데이터 이상 | M-11 조회·M-13 삭제 모두 정상 |

남는 차이는 Method(PATCH)와 body 유무뿐. 또한 `{predicate}`만 보낸 경우와 `{object}`만 보낸 경우는 서버에서 서로 다른 코드 경로를 타는데 둘 다 동일하게 500 → 특정 값 처리가 아니라 그 이전 공통 구간.

M-12 명세의 실패 응답표에 "500 `INTERNAL_ERROR` — AI(I-33)의 503·504도 여기 포함한다"가 있어, **Spring 자체 오류인지 AI 서버(I-33) 호출 실패가 변환된 것인지 FE에서 구분이 불가능**했다. 프록시 대상별 상태(M-11→I-32 정상, M-12→I-33 실패, M-13→I-34 정상)가 "I-33만 미구현/미배포" 가설과 정확히 일치했다.

### 해결 (코드 반영 확인됨)

AI 서버에 I-33(`PATCH /internal/profile/{user_id}/graph/edges/{edge_id}`)이 구현·배포되면서 정상화 (`jarvis-ai/app/api/profile_graph.py`). 당시 500은 미배포 상태의 I-33 호출 실패가 Spring에서 `INTERNAL_ERROR` 로 변환된 것.

FE에는 이 버그와 무관하게 남는 방어를 함께 반영: 5xx일 때 서버 메시지("서버 내부 오류가 발생했습니다.")가 사용자 화면에 그대로 노출되지 않도록 동작별 화면 문구로 대체하고, 5xx에서는 자동 재시도하지 않음(앓는 서버에 요청을 얹지 않기 위해). 400·404·409는 서버가 사유를 알고 보낸 것이므로 종전대로 서버 메시지를 노출.

### 교훈

- 업스트림 오류를 하나의 500 코드로 합치면 호출자가 원인을 분리할 수 없다 — 게이트웨이 역할의 서버는 업스트림 실패를 구분 가능한 코드(`UPSTREAM_*` 등)로 노출할지 계약 단계에서 정해둘 것
- 대조군(성공하는 인접 API)을 잡고 공통 요소를 하나씩 배제하면, 서버 로그 없이도 원인 후보를 강하게 좁힐 수 있다 — 버그리포트의 품질은 재현 조건 + 배제 근거가 좌우한다
- 여러 API를 일괄 협의·배포할 때는 엔드포인트별 배포 여부를 체크리스트로 — "명세는 확정, 구현은 일부"인 구간이 이런 오류로 나타난다

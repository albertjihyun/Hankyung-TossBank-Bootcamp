# k6 부하테스트 기록

> 팀 노션 「k6 부하테스트 하는법」(2026-08-10) + 「부하테스트 시즌2」(2026-08-13)를 합쳐 이관한 것. 스크립트 원문은 주석까지 그대로 보존했고, 하드코딩돼 있던 테스트 계정 비밀번호만 환경변수로 치환했다. 측정 결과 JSON(`summary-*.json`)들은 노션 첨부 파일로 존재했다.
>
> 이 측정의 최종 결론(캐시 도입 전후 비교: 처리량 +31%, p95 지연 −26%, 병목이 앱 CPU → DB CPU → 커넥션 수로 이동)은 `jarvis-backend/README.md` 의 기술 챌린지 절에 정리돼 있다.

## 측정 이력 요약

| 시기 | 구성 | 결과 |
|---|---|---|
| 8/7~8/8 | 2대 t3.micro | 1,000 VU: 980 RPS · p50 159ms · p95 976ms · 에러 0.05% · CPU 66% |
| | | 2,500 VU: 1,288 RPS · p50 1,035ms · p95 2,080ms · 에러 7.27% · CPU 93% → **2,500에서 붕괴** (RPS는 1.3배만 오르고 응답만 2배 느려짐) |
| 8/10~ (시즌1, S-런) | 4대 t3.small · unlimited · HikariCP 7 · **캐시 없음** | vCPU 4→8, RAM 1→2GB, 크레딧 제약 해제. 붕괴 지점 탐색 — **이 측정이 "캐시 도입 전" 기준선** |
| 캐시 배포 후 | 같은 스크립트, RUN 라벨만 변경 | 전후 비교 → 처리량 +31%, p95 −26% (백엔드 README 참조) |
| 8/13 (시즌2, A-런) | 인증 사용자 여정 (읽기 전용) | 비인증 조회 2개 경로만 때리던 시즌1의 한계 보완 — 매 요청에 JWT 검증 + Redis 세션 조회가 붙는 실제 사용자 흐름 |

## 방법론에서 배운 것 (스크립트 주석에 남긴 결정들)

**시즌1 (한계 탐색) 설계**

- **상태 코드 구분으로 병목 위치를 가른다** — 이전 측정에서는 에러 7.27%의 정체를 몰랐다. 이후: 500 = 커넥션 풀 고갈(connection-timeout 5초 fail-fast), 타임아웃 = CPU·톰캣 스레드 포화, 502·503 = 인스턴스 이탈 또는 ALB 거절, 404 = 상품 ID 목록이 낡음.
- **단계마다 90초 이상 유지** — CloudWatch 기본 모니터링이 5분 평균이라, 짧게 스치면 CPU 최고점이 희석돼 판정을 못 한다.
- **임계 초과가 정상** — 무너지는 지점 이후의 거동도 봐야 하므로 abortOnFail을 쓰지 않는다.
- **연결 재사용 켬** — 매번 새로 맺으면 TLS 핸드셰이크가 병목이 되어 서버가 아니라 연결 수립을 재게 된다.
- **응답 본문 폐기 통일** — 이전에는 1000vu는 false, 2500vu는 true라 비교가 어긋났다.
- **부하 생성기(로컬 PC)의 한계 구분** — PC CPU가 100%면 서버가 아니라 PC 한계를 재는 것. 연결 단계 지표(blocked/connecting/TLS)가 크면 서버가 아니라 클라이언트·네트워크가 병목.
- CloudWatch에서 4대 CPUUtilization을 각각 기록 — 고르게 분산되는지가 로드밸런싱의 근거. RDS DatabaseConnections 최고점 28 = 풀 상한(4대 × HikariCP 7).

**시즌2 (인증 여정) 설계**

- 흐름: 홈 추천 → 상품 상세 → 리뷰 → 장바구니 → 찜 → 주문내역. **쓰기는 넣지 않아 데이터를 오염시키지 않는다.**
- **로그인을 반복문에 넣지 않은 이유** — 부하 생성기는 IP가 하나다. VU마다 로그인하면 초당 수백 건이 한 IP에서 나가 IP 요청 제한(분당 30회)에 걸린다. 실제 사용자 1,000명은 각자 다른 IP를 쓰므로 이 제한에 닿지 않는다. 제한을 켠 채로 측정하면 서버 성능이 아니라 "제한이 잘 동작하는지"를 재게 된다. → setup에서 계정 20개로 한 번만 로그인해 토큰 풀을 만들고 VU들이 나눠 쓴다. 사용자도 한 번 로그인하고 수십 번 조회하므로 실제 패턴에 더 가깝다.
- **측정하지 못하는 것을 명시** — 로그인 자체의 처리량(BCrypt 검증 비용, Redis 세션 "생성" 부하)은 이 측정에 없다. 세션 "조회" 부하는 매 요청에 포함된다.
- 토큰은 VU 번호로 배분 — 한 토큰에 몰면 Redis의 같은 키만 때린다.

**부수 사건 — 레이트리밋이 부하테스트를 막은 날 (08-13)**

시즌2 첫 실행(A3)에서 setup의 로그인 20회가 전부 `429 RATE_LIMITED`로 실패해 측정이 시작조차 못 했다. 곡선을 그리기도 전에 로그인 IP 제한이 부하 생성기를 차단한 것 — 제한 로직이 실제로 동작함을 확인한 셈이 됐고, 제한을 조정한 뒤 A4·A5 측정을 완료했다. 부하테스트 환경에서는 레이트리밋 예외 처리(허용 목록 또는 일시 완화)를 사전에 협의해야 한다는 교훈.

---

## 스크립트 1 — 시즌1: 4대 t3.small 한계 탐색 (비인증 조회)

```javascript
// 부하 테스트 — 4대 t3.small 한계 탐색
//
// 실행:
//   k6 run -e RUN=S1 k6-products-limit.js
//   k6 run -e RUN=S2 -e MAX_VU=6000 k6-products-limit.js
//
// 조절 가능한 값
//   RUN      실행 라벨. 매번 바꿀 것 (요약 파일명에 들어감)
//   MAX_VU   기본 4000
//   BASE     기본 https://narvis.shop
//
// 주의
//   · 서비스를 일부러 힘들게 하는 측정이다. 팀원이 배포 중이 아닌지 확인할 것.
//   · product_ids.txt 가 최신인지 확인 (낡으면 404 가 섞여 결과가 오염된다).
//   · 로컬 PC 도 부담을 받는다. 4,000 VU 는 PC CPU 를 확인하며 돌릴 것 —
//     PC 가 100% 면 서버가 아니라 PC 한계를 재게 된다.
//   · CloudWatch 를 열어두고 4대 CPU 를 각각 볼 것. 고르게 분산되는지가
//     로드밸런싱 근거가 된다.

import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'https://narvis.shop';
const RUN = __ENV.RUN || 'S1';
const MAX_VU = parseInt(__ENV.MAX_VU || '4000', 10);

const detailLatency = new Trend('detail_latency', true);
const reviewLatency = new Trend('review_latency', true);

// 상태 코드 구분 — 병목 위치를 가른다
const st200 = new Counter('st_200');
const st404 = new Counter('st_404');
const st5xx = new Counter('st_5xx');
const st502 = new Counter('st_502_503');
const stTimeout = new Counter('st_timeout'); // k6 는 타임아웃·네트워크 실패를 status 0 으로 준다
const stOther = new Counter('st_other');

const PRODUCT_IDS = new SharedArray('product ids', function () {
  return open('./product_ids.txt')
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => /^[0-9]+$/.test(l));
});

export const options = {
  // 단계마다 90초 이상 유지한다 — CloudWatch 기본 모니터링이 5분 평균이라
  // 짧게 스치면 CPU 최고점이 희석돼 판정을 못 한다.
  stages: [
    { duration: '30s', target: Math.round(MAX_VU * 0.25) },
    { duration: '90s', target: Math.round(MAX_VU * 0.25) },
    { duration: '30s', target: Math.round(MAX_VU * 0.5) },
    { duration: '90s', target: Math.round(MAX_VU * 0.5) },
    { duration: '30s', target: Math.round(MAX_VU * 0.75) },
    { duration: '90s', target: Math.round(MAX_VU * 0.75) },
    { duration: '30s', target: MAX_VU },
    { duration: '2m', target: MAX_VU }, // 이 구간의 CPU 를 본다
    { duration: '30s', target: 0 },
  ],

  // 임계를 넘는 것이 정상이다. 무너지는 지점 이후의 거동도 봐야 하므로
  // abortOnFail 을 쓰지 않는다.
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
  },

  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],

  // 연결을 재사용한다. 매번 새로 맺으면 TLS 핸드셰이크가 병목이 되어
  // 서버가 아니라 연결 수립을 재게 된다.
  noConnectionReuse: false,
  dns: { ttl: '30s', select: 'roundRobin' },

  // 본문을 버려 로컬 PC 메모리를 아낀다 — 상태 코드와 시간만 보면 되므로.
  discardResponseBodies: true,
};

function record(res, latency) {
  latency.add(res.timings.duration);
  const s = res.status;
  if (s === 200) st200.add(1);
  else if (s === 0) stTimeout.add(1);
  else if (s === 404) st404.add(1);
  else if (s === 502 || s === 503) st502.add(1);
  else if (s >= 500) st5xx.add(1);
  else stOther.add(1);
}

export default function () {
  const id = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];

  const detail = http.get(`${BASE}/api/products/${id}`, {
    tags: { name: 'product_detail' },
    timeout: '30s',
  });
  record(detail, detailLatency);

  const reviews = http.get(`${BASE}/api/products/${id}/reviews`, {
    tags: { name: 'product_reviews' },
    timeout: '30s',
  });
  record(reviews, reviewLatency);
}
```

측정 후 함께 기록한 것 (같은 시간대 CloudWatch, UTC 주의):

- EC2 CPUUtilization — 4대 각각의 최고점 (고르면 로드밸런싱 정상)
- EC2 CPUCreditBalance — unlimited라 마르지 않아야 함
- RDS CPUUtilization / DatabaseConnections (28이면 풀 상한 = 4대 × 7) / FreeableMemory
- 종료 직후 `docker logs` 백업 — 배포되면 로그가 사라진다

## 스크립트 2 — 시즌2: 인증 사용자 여정 (읽기 전용)

```javascript
// 부하 테스트 — 인증 사용자 여정 (읽기 전용)
//
// 기존 측정(S-런)은 로그인 없이 도는 조회 경로 두 개만 때렸다.
// 이 스크립트는 그 한계를 메운다. 인증이 붙은 상태에서 실제 사용자가
// 하는 흐름을 흉내 내되, 쓰기는 넣지 않아 데이터를 오염시키지 않는다.
//
//   홈 추천 → 상품 상세 → 리뷰 → 장바구니 → 찜 → 주문내역
//
// 기존과 다른 점 — 매 요청에 JWT 검증과 Redis 세션 조회가 붙는다.
//
// ── 로그인을 반복문에 넣지 않은 이유 ──────────────────────────
// 부하 생성기는 IP 가 하나다. VU 마다 로그인하면 초당 수백 건이 한 IP 에서
// 나가 IP 요청 제한(분당 30회)에 걸린다. 실제 사용자 1,000명은 각자 다른
// IP 를 쓰므로 이 제한에 닿지 않는다. 제한을 켠 채로 측정하면 서버 성능이
// 아니라 "제한이 잘 동작하는지"를 재게 된다.
//
// 그래서 setup 에서 계정 20개로 한 번만 로그인해 토큰을 확보하고,
// VU 들이 그 토큰을 나눠 쓴다. 20회는 제한 안에 들어간다.
// 사용자도 한 번 로그인하고 수십 번 조회하므로 실제 패턴에 더 가깝다.
//
// ⚠ 한계 — 로그인 자체의 처리량은 재지 못한다.
//   BCrypt 검증 비용과 Redis 세션 "생성" 부하는 이 측정에 없다.
//   세션 "조회" 부하는 매 요청에 포함된다.
// ────────────────────────────────────────────────────────
//
// 실행:
//   k6 run -e RUN=A3 -e MAX_VU=1000 -e PW=<시드 계정 비밀번호> k6-authed-journey.js

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE     = __ENV.BASE || 'https://narvis.shop';
const RUN      = __ENV.RUN  || 'authed';
const MAX_VU   = Number(__ENV.MAX_VU || 1000);
const PASSWORD = __ENV.PW;  // 시드 계정 비밀번호는 환경변수로만 주입

// 상품 ID 는 연속 번호다(2026-08-12 재번호). 별도 파일이 필요 없다.
const PRODUCT_MAX = Number(__ENV.PRODUCT_MAX || 6308);
// 미리 로그인해둘 계정 수. IP 제한(분당 30)보다 작게 잡는다.
const POOL_SIZE   = Number(__ENV.POOL || 20);

const homeLatency   = new Trend('t_home', true);
const detailLatency = new Trend('t_detail', true);
const reviewLatency = new Trend('t_review', true);
const cartLatency   = new Trend('t_cart', true);
const listLatency   = new Trend('t_list', true);

const st200    = new Counter('st_200');
const st401    = new Counter('st_401');
const st429    = new Counter('st_429');
const st4xx    = new Counter('st_4xx');
const st5xx    = new Counter('st_5xx');
const st502503 = new Counter('st_502_503');
const stTimeout= new Counter('st_timeout');

function tally(res) {
  const s = res.status;
  if (s === 0)                      stTimeout.add(1);
  else if (s === 200)               st200.add(1);
  else if (s === 401)               st401.add(1);
  else if (s === 429)               st429.add(1);
  else if (s === 502 || s === 503) { st502503.add(1); st5xx.add(1); }
  else if (s >= 500)                st5xx.add(1);
  else if (s >= 400)                st4xx.add(1);
}

export const options = {
  stages: [
    { duration: '30s', target: Math.round(MAX_VU * 0.2) },
    { duration: '60s', target: Math.round(MAX_VU * 0.2) },
    { duration: '30s', target: Math.round(MAX_VU * 0.5) },
    { duration: '90s', target: Math.round(MAX_VU * 0.5) },
    { duration: '30s', target: MAX_VU },
    { duration: '2m',  target: MAX_VU },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed:   ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
  },
  noConnectionReuse: false,
  dns: { ttl: '30s', select: 'roundRobin' },
  discardResponseBodies: false,
};

// setup 은 부하가 시작되기 전에 한 번만 돈다. 여기서 얻은 값은 default 로 전달된다.
export function setup() {
  const tokens = [];
  for (let i = 1; i <= POOL_SIZE; i++) {
    const email = `user${String(i).padStart(4, '0')}@narvis.shop`;
    const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({
      email, password: PASSWORD,
    }), { headers: { 'Content-Type': 'application/json' } });

    if (r.status !== 200) {
      console.error(`로그인 실패 ${email}: ${r.status}`);
      continue;
    }
    // HttpOnly 쿠키로 발급된다. 값만 뽑아 VU 들에게 넘긴다.
    const c = r.cookies['access_token'];
    if (c && c.length) tokens.push(c[0].value);
    sleep(0.3);   // 제한에 걸리지 않도록 간격을 둔다
  }
  if (tokens.length === 0) {
    throw new Error('로그인이 하나도 성공하지 못했다. 계정·비밀번호·요청 제한을 확인할 것.');
  }
  return { tokens };
}

export default function (data) {
  // VU 번호로 토큰을 배분한다. 한 토큰에 몰면 Redis 의 같은 키만 때린다.
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  http.cookieJar().set(BASE, 'access_token', token);

  // 1) 홈 — 개인화 추천. 인증이 필요한 경로다.
  group('home', () => {
    const r = http.get(`${BASE}/api/products/recommended`,
      { tags: { name: 'recommended' }, timeout: '30s' });
    homeLatency.add(r.timings.duration);
    tally(r);
    check(r, { '추천 200': (x) => x.status === 200 });
  });

  sleep(Math.random() * 0.5 + 0.3);

  // 2) 상품 상세 → 리뷰 — 사용자가 상품을 눌러 들어가는 흐름
  const pid = Math.floor(Math.random() * PRODUCT_MAX) + 1;
  group('product', () => {
    const d = http.get(`${BASE}/api/products/${pid}`,
      { tags: { name: 'detail' }, timeout: '30s' });
    detailLatency.add(d.timings.duration);
    tally(d);
    check(d, { '상세 200': (x) => x.status === 200 });

    sleep(Math.random() * 0.4 + 0.2);

    const v = http.get(`${BASE}/api/products/${pid}/reviews`,
      { tags: { name: 'reviews' }, timeout: '30s' });
    reviewLatency.add(v.timings.duration);
    tally(v);
    check(v, { '리뷰 200': (x) => x.status === 200 });
  });

  sleep(Math.random() * 0.5 + 0.3);

  // 3) 마이페이지 계열 — 전부 조회다. 쓰기가 없어 데이터가 쌓이지 않는다.
  group('mypage', () => {
    const c = http.get(`${BASE}/api/cart`, { tags: { name: 'cart' }, timeout: '30s' });
    cartLatency.add(c.timings.duration);
    tally(c);
    check(c, { '장바구니 200': (x) => x.status === 200 });

    const w = http.get(`${BASE}/api/wishlist`, { tags: { name: 'wishlist' }, timeout: '30s' });
    listLatency.add(w.timings.duration);
    tally(w);

    const o = http.get(`${BASE}/api/orders`, { tags: { name: 'orders' }, timeout: '30s' });
    listLatency.add(o.timings.duration);
    tally(o);
  });

  sleep(Math.random() * 1 + 0.5);
}
```

(두 스크립트 모두 원본에는 결과를 표 형태로 출력하는 `handleSummary` 가 붙어 있었다 — 상태 코드 분포로 병목 위치를 가르고, 연결 단계 지표로 서버 병목과 클라이언트 병목을 구분하고, Redis CacheHits/CacheMisses를 함께 기록하는 구성. 요지는 위 방법론 절에 정리돼 있어 생략.)

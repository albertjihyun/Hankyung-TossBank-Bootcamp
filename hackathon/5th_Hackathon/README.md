# 5th Hackathon — OLIVE 패션몰

**Spring Boot + H2 + Next.js** 풀스택 쇼핑몰. 상품 데이터·이미지는 **실제 Kaggle 데이터셋**
(`paramaggarwal/fashion-product-images-small`, 5,000건)을 사용하고, 회원가입/로그인(JWT+세션+블랙리스트),
장바구니, 주문/결제(데모)까지 동작한다. 이미지는 GCS 대신 Next.js 정적 폴더로 서빙한다.

```
Kaggle 데이터셋 ──prepare-seed.py──▶ seed-products.csv  +  public/products/{id}.jpg
                  (5,000건 선별/복사)          │                      │
                                               ▼                      ▼
브라우저 ──▶ Next.js (3000) ──BFF 프록시(/api/*)──▶ Spring Boot (8080) ──▶ H2
            · SSR: 홈/목록/상세 (직접 fetch)        · JWT 인증 + Spring Session
            · CSR: 로그인/장바구니/결제 (BFF)        · 토큰 블랙리스트(로그아웃)
   ▲ cloudflared (단일 터널)
```

> 브라우저는 **Next.js만** 본다. SSR 페이지는 서버에서 Spring을 직접 호출하고,
> 인증이 필요한 CSR 동작은 Next.js **BFF 프록시**(`app/api/[...path]`)가 쿠키와 함께 Spring으로 중계 →
> **cloudflared 터널 1개**로 인증까지 전부 동작.

## 인증 설계 (JWT + Spring Session + 블랙리스트)

| 요소 | 역할 |
|------|------|
| **JWT 액세스 토큰** | 스테이트리스 API 인증(30분). `jti` 부여로 개별 무효화 가능. httpOnly 쿠키. |
| **리프레시 토큰** | 14일, H2 저장. 액세스 만료 시 재발급 앵커. 로그아웃 시 삭제. |
| **Spring Session (JDBC/H2)** | 로그인 시 서버측 세션 생성·영속(`SPRING_SESSION`). 로그아웃 시 invalidate. |
| **토큰 블랙리스트** | 로그아웃 시 액세스 토큰 `jti`를 등록 → 만료 전이라도 거부(JWT 약점 보완). |

로그아웃 = ① jti 블랙리스트 ② 리프레시 토큰 삭제 ③ 세션 무효화 (3중 무효화).
**데모 계정: `demo@olive.com` / `olive1234`**

## 구성

- `scripts/prepare-seed.py` — Kaggle styles.csv에서 5,000건 선별(셔플 후 절단) + 이미지 복사 + `seed-products.csv` 생성
- `scripts/enhance-images.py` — 원본 60x80 이미지를 600x800(3:4)로 업스케일(LANCZOS+언샤프). 브라우저가 다운샘플하게 만들어 깨짐 제거
- `backend/` — Spring Boot (`com.hackathon.olive`)
  - `domain/` Product·User·CartItem·Order·OrderItem·RefreshToken·BlacklistedToken
  - `security/` JwtTokenProvider·JwtAuthenticationFilter·SecurityConfig·CookieUtil·TokenBlacklistService
  - `service/` AuthService·CartService·OrderService · `web/` Auth·Cart·Order·Product 컨트롤러
  - `seed/` ProductSeeder(CSV 적재, 가격/할인/재고 생성) · DemoUserSeeder
- `frontend/` — Next.js(App Router)
  - SSR: `/` 홈 · `/products` 목록(필터/페이지네이션) · `/products/[id]` 상세
  - CSR: `/login` · `/signup` · `/cart` · `/checkout`(결제 팝업) · `/orders` · `/mypage`
  - `app/api/[...path]` BFF 프록시 · `providers/AuthProvider` · `lib/client`(401 시 자동 리프레시)
  - `public/products/{id}.jpg` — 선별된 Kaggle 이미지(정적 서빙)

## API 요약

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/products?page=&size=&category=` | - | 목록 |
| GET | `/api/products/{id}` · `/api/categories` | - | 상세 / 카테고리 |
| POST | `/api/auth/signup` `/login` `/refresh` `/logout` | - | 인증 |
| GET | `/api/auth/me` | ✓ | 내 정보 |
| GET/POST/PATCH/DELETE | `/api/cart` `/cart/{id}` `/cart/count` | ✓ | 장바구니 |
| POST/GET | `/api/orders` `/orders/{id}` | ✓ | 주문/결제·내역 |

## 데이터 준비 (최초 1회)

```bash
# 1) Kaggle API 토큰 설정 (~/.kaggle/access_token 에 KGAT_... 저장)
# 2) 데이터셋 다운로드
kaggle datasets download -d paramaggarwal/fashion-product-images-small -p <DL> --unzip
# 3) 선별 + 이미지 복사 + CSV 생성
python scripts/prepare-seed.py <DL> frontend/public/products backend/seed-products.csv 5000
```

> 생성물(`backend/seed-products.csv`, `frontend/public/products/*.jpg`)은 용량이 커서 git에 올리지 않는다.

## 로컬 실행

1. 백엔드
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
   cd backend; .\gradlew.bat bootRun
   ```
2. 프런트엔드
   ```powershell
   cd frontend; npm install
   $env:API_BASE = "http://localhost:8080"; npm run dev   # http://localhost:3000
   ```

## 임시 공개 배포 (cloudflared)

```powershell
cloudflared tunnel --url http://localhost:3000
# 출력되는 https://<random>.trycloudflare.com 주소가 공개 URL
```

## 환경변수

- 백엔드: `seed.count`(기본 5000), `seed.enabled`(기본 true)
- 프런트: `API_BASE`(기본 `http://localhost:8080`) — Spring 주소

# OLIVE 패션몰 — 0부터 공개 URL까지 셋업 런북

> 이 문서는 **사람 + AI 어시스턴트(GPT/Claude 등)가 그대로 따라 실행**하도록 작성됐다.
> 아래 "AI 어시스턴트에게" 블록은 에이전트가 자동 적용할 때의 지침이고,
> 사람이 직접 해야 하는 단계는 **🧑 MANUAL** 로 표시했다(계정/토큰처럼 자동화 불가).
>
> **목표 결과물 한 줄:** Kaggle 패션 데이터 5,000건이 이미지와 함께 화면에 뜨고,
> `https://<랜덤>.trycloudflare.com` 공개 주소가 출력된다.

---

## 0. AI 어시스턴트에게 (먼저 읽기)

```
역할: 이 런북을 위→아래 순서로 실행한다. 각 Phase 끝의 ✅ 검증을 통과해야 다음으로 넘어간다.
원칙:
- 🧑 MANUAL 단계는 직접 못 한다. 사용자에게 "무엇을 어디서 하라"고 안내하고, 산출물(토큰/경로)을 받은 뒤 진행한다.
- 명령은 멱등하게. 이미 된 건 건너뛴다(파일/포트/프로세스 존재 확인 후 실행).
- 비밀(Kaggle 토큰)을 로그/커밋에 남기지 않는다.
- OS에 맞는 명령 블록만 쓴다(Windows=PowerShell, macOS/Linux=bash).
- 한 번에 하나의 백그라운드 프로세스를 띄우고, 헬스체크가 통과할 때까지 기다린 뒤 다음 단계로.
스택: Spring Boot(8080) + H2(인메모리) + Next.js(3000). 이미지는 Next.js 정적 폴더로 서빙(GCS 미사용).
데이터: Kaggle `paramaggarwal/fashion-product-images-small` (실데이터, 593MB).
```

---

## 1. 사전 준비물 (설치)

| 도구 | 버전 | 확인 명령 |
|------|------|-----------|
| JDK | 21 | `java -version` |
| Node.js | 18+ (권장 20/22) | `node -v` |
| Python | 3.10+ | `python --version` |
| cloudflared | 최신 | `cloudflared --version` |
| Kaggle CLI | 최신 | `kaggle --version` |
| Pillow | 최신 | `python -c "import PIL"` (이미지 업스케일용) |

### 설치 (없을 때만)

**Windows (PowerShell):**
```powershell
winget install --id Microsoft.OpenJDK.21 -e
winget install --id OpenJS.NodeJS.LTS -e
winget install --id Python.Python.3.12 -e
winget install --id Cloudflare.cloudflared -e
python -m pip install --upgrade kaggle
```

**macOS (Homebrew):**
```bash
brew install openjdk@21 node python cloudflared
pip3 install --upgrade kaggle
```

**Linux (apt):**
```bash
sudo apt update && sudo apt install -y openjdk-21-jdk nodejs npm python3 python3-pip
# cloudflared: https://github.com/cloudflare/cloudflared/releases 에서 받기
pip3 install --upgrade kaggle
```

> **Windows 주의:** `java`/`gradle`이 PATH에 없을 수 있다. JDK 설치 후
> `JAVA_HOME`을 JDK 경로로 잡고(예: `C:\Program Files\Microsoft\jdk-21.x.x-hotspot`),
> 빌드는 프로젝트의 `gradlew`(래퍼)를 쓴다.

✅ **검증:** 위 5개 `--version`이 전부 정상 출력.

---

## 2. 🧑 MANUAL — Kaggle 계정 + API 토큰

1. https://www.kaggle.com 접속 → **Register**(구글 계정으로 1분, 무료).
2. 로그인 후 우측 상단 **프로필 → Settings**.
3. **API** 섹션 → **Create New Token**.
4. 표시되는 토큰 형식에 따라:
   - **신형(`KGAT_...` 한 줄):** 화면의 안내대로 저장.
     - Windows: 메모장으로 `C:\Users\<나>\.kaggle\access_token` 파일을 만들고 토큰 문자열만 붙여넣기.
     - macOS/Linux: `mkdir -p ~/.kaggle && echo 'KGAT_xxxxx' > ~/.kaggle/access_token && chmod 600 ~/.kaggle/access_token`
   - **구형(`kaggle.json` 다운로드):** 그 파일을 `~/.kaggle/kaggle.json` 위치로 이동.
       - macOS/Linux: `chmod 600 ~/.kaggle/kaggle.json`

> 토큰은 비밀이다. 채팅/커밋에 붙여넣지 말 것. 작업이 끝나면 Settings에서 **Expire** 권장.

✅ **검증:** `kaggle datasets files paramaggarwal/fashion-product-images-small` 가 파일 목록을 출력하면 인증 성공.

> AI 어시스턴트에게: 이 검증이 `getaddrinfo failed`(DNS)로 실패하면 그건 네트워크 샌드박스 문제다.
> 인터넷이 되는 셸(예: 일반 터미널/Bash)에서 재시도하거나 샌드박스를 해제하고 실행하라.

---

## 3. 코드 가져오기

이 프로젝트 폴더(`5th_Hackathon/`)를 받는다. 구조:

```
5th_Hackathon/
├── SETUP.md              ← 이 문서
├── README.md
├── scripts/
│   ├── prepare-seed.py   ← Kaggle → 선별/이미지복사/CSV
│   ├── run-all.ps1       ← (Windows) 백엔드+프런트+터널 한 번에
│   └── run-all.sh        ← (mac/Linux) 동일
├── backend/   (Spring Boot + H2)
└── frontend/  (Next.js)
```

> 코드를 새로 만드는 게 아니라 **이미 있는 이 폴더를 그대로 쓴다.** 없으면 사용자에게 폴더를 요청한다.

✅ **검증:** `backend/build.gradle`, `frontend/package.json`, `scripts/prepare-seed.py` 가 존재.

---

## 4. 데이터 다운로드 + 선별 + 이미지 복사

작업 디렉터리: `5th_Hackathon/`

**4-1. 데이터셋 다운로드 (593MB, 몇 분 소요)**

```bash
# <DL>은 임시 폴더(예: ./_data). 한 번만 받으면 된다.
kaggle datasets download -d paramaggarwal/fashion-product-images-small -p ./_data --unzip
```

압축 풀면 `./_data/styles.csv` 와 `./_data/images/{id}.jpg`(약 44,000장)가 생긴다.

**4-2. 5,000건 선별 + 이미지 복사 + CSV 생성**

```bash
python scripts/prepare-seed.py ./_data frontend/public/products backend/seed-products.csv 5000
```

**4-3. 이미지 업스케일 (필수 — 원본이 60x80 초저해상도라 그대로 쓰면 깨짐)**

```bash
python -m pip install pillow   # 최초 1회
# 원본(60x80) → 600x800 LANCZOS + 언샤프. frontend/public/products 의 파일을 기준으로 처리.
python scripts/enhance-images.py ./_data/images frontend/public/products 600 800
```

> 60x80을 600x800으로 키워두면 브라우저가 '다운샘플'(선명)하게 되어 카드/상세가 또렷해진다.
> (없는 디테일을 만들진 못하지만 픽셀 깨짐·계단현상이 사라진다.) 카드 비율은 3:4로 맞춰져 있다.

이 스크립트가 하는 일(기획서 3-[1]/[2]):
- `masterCategory ∈ {Apparel, Footwear, Accessories}` & 이미지 실존하는 후보만 추림
- **셔플 후 앞 5,000개** 선별(단순 앞 5,000행 자르기 ❌)
- 선별 이미지를 `frontend/public/products/{id}.jpg`로 복사
- 메타데이터를 `backend/seed-products.csv`로 기록

> AI 어시스턴트에게: 44,000개 파일 폴더에서 `ls | wc -l` 같은 전수 나열은 Windows에서 느려 타임아웃 난다. 스크립트는 파일별 존재 확인만 하므로 문제없다. 디버깅 시에도 전수 나열을 피하라.

✅ **검증:**
```bash
# CSV 5001줄(헤더 포함), 이미지 5000장
wc -l < backend/seed-products.csv          # → 5001
ls frontend/public/products | wc -l        # → 5000  (느리면 생략 가능)
head -2 backend/seed-products.csv          # 실제 브랜드명 보이면 OK
```

---

## 5. 백엔드 실행 (Spring Boot + H2)

작업 디렉터리: `5th_Hackathon/backend/`

부팅 시 `seed-products.csv`를 읽어 H2에 5,000건 적재한다(가격/할인/재고는 id 기반 규칙으로 생성).

**Windows (PowerShell):**
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"  # 실제 설치 경로로
cd backend
.\gradlew.bat bootRun
```

**macOS/Linux:**
```bash
cd backend
./gradlew bootRun
```

✅ **검증(다른 셸에서):**
```bash
curl -s "http://localhost:8080/api/products?size=1"
# → {"totalElements":5000, ... "name":"Nike ...","imageUrl":"/products/xxxx.jpg"}
curl -s "http://localhost:8080/api/categories"
# → ["Accessories","Apparel","Footwear"]
```

> 포트 8080 충돌 시: 기존 프로세스를 끄고 재시작.
> Windows: `Get-NetTCPConnection -LocalPort 8080 -State Listen | %{ Stop-Process -Id $_.OwningProcess -Force }`
> macOS/Linux: `lsof -ti:8080 | xargs kill -9`

---

## 6. 프런트엔드 실행 (Next.js)

작업 디렉터리: `5th_Hackathon/frontend/`

브라우저는 **Next.js만** 보고, Next.js가 **서버사이드로** Spring(8080)을 호출한다.
→ 그래서 cloudflared 터널은 **3000 하나면 충분**하다.

```bash
cd frontend
npm install
# API_BASE: Spring 주소. 기본값 http://localhost:8080
#   Windows PowerShell:  $env:API_BASE="http://localhost:8080"; npm run dev
#   macOS/Linux:         API_BASE=http://localhost:8080 npm run dev
npm run dev
```

✅ **검증:**
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3000/                 # 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3000/products         # 200
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" http://localhost:3000/products/<위CSV의 id>.jpg  # 200 image/jpeg
```

---

## 7. 공개 URL (cloudflared 임시 터널)

```bash
cloudflared tunnel --url http://localhost:3000
```

출력 로그에서 `https://<랜덤>.trycloudflare.com` 주소를 찾는다 → **그게 공개 URL.**

> AI 어시스턴트에게: 백그라운드로 띄우고, 출력에서 정규식 `https://[a-z0-9-]+\.trycloudflare\.com` 으로 URL을 추출해 사용자에게 보고하라.

✅ **최종 검증:**
```bash
U="https://<랜덤>.trycloudflare.com"
curl -s -o /dev/null -w "%{http_code}\n" "$U/"                      # 200
curl -s "$U/" | grep -o "Nike[^<\"]*" | head -1                    # 실제 상품명
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" "$U/products/<id>.jpg"  # 200 image/jpeg
```

---

## 8. 한 방에 실행 (스크립트)

Phase 4까지(데이터 준비) 끝났다면, 5~7번은 스크립트 하나로 띄울 수 있다.

**Windows:**
```powershell
.\scripts\run-all.ps1
```
**macOS/Linux:**
```bash
bash scripts/run-all.sh
```

스크립트가 백엔드→프런트→터널을 순서대로 띄우고, 마지막에 **공개 URL을 출력**한다.
끄려면 그 셸에서 `Ctrl+C` (또는 `scripts/stop-all`).

---

## 9. 자주 나는 문제 (Troubleshooting)

| 증상 | 원인 / 해결 |
|------|-------------|
| `kaggle ... getaddrinfo failed` | 네트워크 샌드박스/DNS. 인터넷 되는 일반 터미널에서 실행. |
| `kaggle 401/403` | 토큰 만료/오타. Settings에서 새 토큰 발급 후 `~/.kaggle/access_token` 갱신. |
| Spring 부팅 후 상품 0건 | `seed-products.csv` 경로 문제. `backend/`에서 실행했는지 확인(상대경로 `seed-products.csv`). |
| 이미지 404 | `frontend/public/products/`에 해당 id.jpg 없음. Phase 4-2 재실행. |
| 포트 충돌(8080/3000) | 기존 프로세스 종료(5번 박스 참고) 후 재시작. |
| 터널 URL 매번 바뀜 | 무료 quick tunnel 특성. 고정 도메인 필요하면 cloudflared named tunnel 설정. |
| H2라 재시작 시 데이터 사라짐 | 정상. CSV+이미지가 디스크에 남아 재부팅 시 자동 재적재(재다운로드 불필요). |

---

## 부록: 데이터/구성 요약

- 상품 스키마: `id, name, gender, master_category, sub_category, article_type, base_colour, season, price, discount_rate, stock, image_url`
- 가격/할인/재고만 생성(데이터셋에 없음): 카테고리별 가격대 + 70% 할인 0 + 일부 품절.
- 이미지 URL: `/products/{id}.jpg` (Next.js 정적). 운영 전환 시 이 값만 GCS URL로 바꾸면 된다.
- API: `GET /api/products?page=&size=&category=`, `GET /api/products/{id}`, `GET /api/categories`

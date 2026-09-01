# 살래말래

> "사기 전에 N일만 기다려봐" — 충동적인 당신을 위한 **충동구매 브레이크 서비스**

사고 싶은 물건을 바로 사는 대신 등록해두고, 스스로 정한 쿨링오프 기간이 지난 뒤 다시 결정(살래/말래)하는 서비스입니다. 행동경제학의 쿨링오프(Cooling-off) 원리를 UX에 녹여 "욕구 발생 → 즉시 결제"의 사이클에 브레이크를 겁니다. 참은 횟수와 절약 금액이 누적되고, 5단계 레벨(충동 새싹 → 전설의 짠돌이)과 공유 페이지로 성취감을 제공합니다.

- 한경 × 토스뱅크 풀스택 3기 중간 프로젝트 (2026.05.08 ~ 05.21, 4인 팀)
- 원본 팀 저장소: [Hankyung-Toss-delta/sallae-malrae](https://github.com/Hankyung-Toss-delta/sallae-malrae) (개발 과정의 브랜치·PR·리뷰 이력은 원본 저장소 참고)

## 팀 & 담당

| 이름 | 담당 |
| --- | --- |
| **유지현** (팀장) | **백엔드·DB 설계 주담당** — API 설계·구현, DB 스키마·트랜잭션·인덱스 설계, 인증(JWT Rotation), 알림 스케줄러, GCP 배포. 프론트엔드 일부 관여 — 서버/클라이언트 컴포넌트(SSR/CSR) 구분 설계, 컴포넌트 분리 |
| 이소희 | 프론트엔드 |
| 홍예린 | 프론트엔드 |
| 정성모 | 프론트엔드 |

## 핵심 기능

- **쿨링오프 등록** — 상품명·가격·카테고리·기간(1시간~30일, 정각 단위)·충동 지수(1~10)·메모·이미지(sharp 리사이즈 + WebP 변환 후 GCS 업로드)
- **결정 (살래/말래)** — 상태 변경 + 월별 통계 UPSERT + 레벨 재계산을 **하나의 트랜잭션**으로 원자 처리, 멱등 API
- **이메일 알림** — 만료 24시간 전 / 만료 시점, systemd timer가 매 정각 oneshot 실행, DB UNIQUE 제약으로 중복 발송 차단
- **대시보드** — 절약 통계·레벨·만료 알림. 서버 컴포넌트가 DB 직접 조회 (별도 API 없음)
- **활동 공유** — 추측 불가능한 토큰 기반 퍼블릭 URL, `generateMetadata()`로 OG 태그 지원 (SNS 미리보기)
- **인증** — JWT AT(15분) + RT(7일), HttpOnly + SameSite=Strict 쿠키, Refresh Token Rotation, 재사용 감지 시 전 세션 강제 만료

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| 프레임워크 | Next.js (App Router, Server Components + API Routes 풀스택) |
| DB | MariaDB — ORM 없이 raw SQL + 커넥션 풀 |
| 인증 | jsonwebtoken(Node) + jose(Edge 미들웨어), bcrypt |
| 이미지 | sharp, Google Cloud Storage |
| 메일 | nodemailer (SMTP) |
| 스타일 | Tailwind CSS |
| 배포 | GCP VM + systemd (웹 서비스 + 알림 타이머), Cloudflare Tunnel |

## 아키텍처

```
인터넷 → Cloudflare Tunnel → Next.js (페이지 + API Routes)
                               ├── MariaDB (raw SQL, 커넥션 풀)
                               ├── GCS (이미지 저장)
                               └── systemd timer ─ 매 정각 oneshot → 알림 스크립트
```

- 사용자 요청 처리는 Next.js 풀스택으로 **통합**, 시간 트리거는 systemd timer로 **분리** — "분리할 이유가 진짜 있을 때만 분리한다"는 원칙을 반대 결과로 적용한 두 결정 ([ADR-001](./docs/adr.md))
- 웹 프로세스와 알림 스크립트는 직접 통신하지 않고 DB를 공동의 진실 원천으로 삼음
- 정합성은 트랜잭션(원자성) + CHECK(도메인 무결성) + UNIQUE(동시성)의 다층 방어선으로 보장 ([ADR-004](./docs/adr.md))

## 설계 문서

설계 결정의 근거와 트레이드오프를 문서로 남겼습니다. 코드 주석의 `ADR-XXX` 표기가 이 문서들을 참조합니다.

| 문서 | 내용 |
| --- | --- |
| [docs/adr.md](./docs/adr.md) | 설계 의사결정 기록 ADR-001~014 — 아키텍처, 반정규화, 트랜잭션, 인증, 멱등성 등 (대안 비교·트레이드오프 포함) |
| [docs/db-schema.md](./docs/db-schema.md) | DB 스키마 설계 근거 + 결정 트랜잭션 + 설계 Q&A |
| [docs/troubleshooting.md](./docs/troubleshooting.md) | 이메일 알림 미발송 해결 사례, AT/RT 인증 관련 인지된 이슈와 해결 방향 |
| [docs/planning.md](./docs/planning.md) | 기획 배경 — 문제 정의, 해결책, 차별점, MVP 범위 |

## 실행 방법

```bash
# 1. 의존성 설치
npm install

# 2. 환경변수 설정 — .env.example 참고
cp .env.example .env.local   # 값 채우기

# 3. DB 스키마 적용 (MariaDB)
mysql -u <user> -p <database> < schema.sql

# 4. 개발 서버
npm run dev
```

- 이메일 알림 로컬 테스트: `DRY_RUN=1 node scripts/dev-notify.js` (실제 발송 없이 콘솔 출력)
- VM 배포(systemd 유닛 설치): [deploy/README.md](./deploy/README.md)

## 페이지 구조

| 경로 | 역할 |
| --- | --- |
| `/` | 랜딩 |
| `/auth/login` · `/auth/signup` | 인증 |
| `/dashboard` | 활동 요약 (서버 컴포넌트 DB 직접 조회) |
| `/coolingoff` · `/coolingoff/new` | 쿨링오프 목록 · 등록 |
| `/share/:shareToken` | 공유 페이지 (인증 불필요, OG 태그 지원) |

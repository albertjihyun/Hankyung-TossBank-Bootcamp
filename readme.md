# 한경 × 토스뱅크 풀스택 부트캠프

**기간:** 2026.02.13 – 2026.08.13 · **트랙:** 풀스택

한국경제신문·토스뱅크 부트캠프에서 진행한 해커톤 5회, 미드 프로젝트, 파이널 프로젝트를 모은 저장소입니다.
단순 기능 구현이 아니라 **시스템이 어떻게 동작하는지** — 인증, 동시성 제어, 아키텍처, 배포 — 를 이해하는 데 초점을 두고 학습했습니다.

---

## 저장소 구조

```
hackathon/        해커톤 5회 (개인)
mid_project/      미드 팀 프로젝트 — 살래말래 (담당: 백엔드·DB)
final_project/    파이널 팀 프로젝트 — Jarvis (담당: 백엔드)
```

각 프로젝트 폴더에 상세 README가 있습니다.

---

## 해커톤 (5회)

기획부터 배포까지 하루(8시간) 안에 완성하는 개인 해커톤입니다. 회차가 진행될수록 바닐라 JS → Express + DB → Next.js → Spring Boot → Spring + Next.js 통합으로 기술 스택을 확장했습니다.

| 회차 | 프로젝트 | 한 줄 소개 | 기술 스택 |
| --- | --- | --- | --- |
| [1차](./hackathon/1st_Hackathon) | 투자 도우미 | 초보 투자자를 위한 시장 인사이트 + 투자 MBTI 기반 상품 추천. 기능 나열이 아닌 **유저 플로우 설계** 중심 | Node.js · Vanilla JS |
| [2차](./hackathon/2nd_Hackathon) | DevQ | 개발자 Q&A 커뮤니티. DB 설계부터 클라우드 배포까지 엔드투엔드 구현 | Node.js · Express · MariaDB · JWT |
| [3차](./hackathon/3rd_Hackathon) | StockBattle | 최대 4인이 3분간 가상 주식으로 수익률을 겨루는 실시간 방 기반 모의투자 배틀 | Next.js · TypeScript · React Query · Zustand · MariaDB |
| [4차](./hackathon/4th_Hackathon) | 오픈런 | 선착순 한정 행사의 예약·대기열·취소 자동 승계. **비관적 락으로 멀티 인스턴스 환경에서 동시성 제어** | Java 21 · Spring Boot · JPA · MariaDB · GCP(MIG·LB) |
| [5차](./hackathon/5th_Hackathon) | OLIVE 패션몰 | 실제 Kaggle 데이터셋 5,000건으로 만든 쇼핑몰. JWT + 세션 + 토큰 블랙리스트 인증 설계, Next.js BFF 패턴 | Spring Boot · H2 · Next.js(BFF) |

---

## 미드 프로젝트 — [살래말래](./mid_project)

충동구매 브레이크 서비스. 풀스택 트랙 팀 프로젝트로 진행했습니다.

**담당: 백엔드·DB** — API·스키마·트랜잭션·인증(JWT Rotation) 설계와 구현, GCP 배포. 프론트는 SSR/CSR 구분 설계와 컴포넌트 분리에 관여.

- 원본 팀 저장소: [Hankyung-Toss-delta/sallae-malrae](https://github.com/Hankyung-Toss-delta/sallae-malrae)
- 설계 문서(ADR·DB 스키마·트러블슈팅): [mid_project/sallae-malrae/docs](./mid_project/sallae-malrae/docs)

---

## 파이널 프로젝트 — [Jarvis](./final_project)

대화로 상품을 찾고 담고 사는 **에이전틱 커머스(Agentic Commerce)** 플랫폼. LLM 트랙과 협업한 크로스팀 프로젝트로, AI · 프론트엔드 · 백엔드 3개 저장소로 구성됩니다.

**담당: 백엔드** — 커머스 트랜잭션(회원·상품·주문)을 소유하고, AI 에이전트의 모든 행위를 검증·기록하는 API 서버

| 파트 | 코드 | 원본 팀 저장소 |
| --- | --- | --- |
| 백엔드 (담당) | [jarvis-backend/](./final_project/jarvis-backend) | [toss-delta-final/jarvis-backend](https://github.com/toss-delta-final/jarvis-backend) |
| 프론트엔드 | [jarvis-frontend/](./final_project/jarvis-frontend) | [toss-delta-final/jarvis-frontend](https://github.com/toss-delta-final/jarvis-frontend) |
| AI | [jarvis-ai/](./final_project/jarvis-ai) | [toss-delta-final/jarvis-ai](https://github.com/toss-delta-final/jarvis-ai) |

기술 스택: Java 21 · Spring Boot 3.5 · MariaDB · Redis · Kafka

> 팀 프로젝트 코드는 보존용으로 복사해온 것으로, 커밋 히스토리는 각 원본 저장소에서 확인할 수 있습니다.

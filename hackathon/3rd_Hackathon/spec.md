# StockBattle 개발 명세서

## 1. 프로젝트 개요

### 1.1 서비스 설명
StockBattle은 방 기반 실시간 모의 주식 투자 대결 플랫폼이다.
사용자는 방을 만들거나 참가해 가상 종목으로 매수/매도를 반복하며 제한 시간 안에 총 자산 수익률로 순위를 겨룬다.

### 1.2 기술 스택
- **프레임워크**: Next.js (App Router, TypeScript)
- **스타일**: Tailwind CSS
- **상태 관리**: Zustand (클라이언트 로컬 상태)
- **서버 상태**: React Query / TanStack Query (폴링, 데이터 페칭)
- **DB**: MariaDB + mysql2
- **인증**: JWT (jose 라이브러리)
- **배포**: GCP VM + Cloudflare (SSL)
- **프로세스 관리**: PM2

### 1.3 게임 규칙
- 게임 시간: 3분 (180초)
- 초기 자금: 1,000,000원
- 가상 종목: 4개 (변동성 등급별)
- 가격 변동 주기: 10초마다
- 최대 참가 인원: 4명
- 최소 시작 인원: 2명

---

## 2. 사용자 플로우

### 2.1 인증

#### 회원가입
```
이메일 + 닉네임 + 비밀번호 입력
→ 이메일 중복 체크
→ 닉네임 중복 체크
→ 성공 시 JWT 발급 → 자동 로그인 후 /lobby 이동
→ 실패 시 에러 메시지 (이메일 중복 / 닉네임 중복)
```

#### 로그인
```
이메일 + 비밀번호 입력
→ 성공 시 JWT 발급 → /lobby 이동
→ 실패 시 에러 메시지 (이메일/비밀번호 불일치)
```

#### 이미 로그인된 상태에서 / 접근
```
JWT 유효하면 → /lobby로 자동 리다이렉트
```

#### 로그아웃
```
JWT 삭제 → / 로 이동
```

### 2.2 로비

#### 진입
```
JWT 없으면 → / 로 리다이렉트
JWT 있으면 → 현재 참가 중인 방 확인
  → 배틀 중이면 /battle/[id]로 리다이렉트
  → 대기 중이면 /room/[id]로 리다이렉트
  → 결과 화면이면 /result/[id]로 리다이렉트
  → 없으면 방 목록 로드 + Long Polling 시작
```

#### 방 만들기
```
방 만들기 버튼 → 방 이름 입력 모달 → 확인
→ 이미 참가 중인 방 있으면 에러
→ DB에 방 생성 + 본인 자동 참가 (room_members)
→ /room/[id]로 이동
```

#### 방 참가
```
참가 버튼 클릭
→ 서버에서 재확인:
  - 자리 있는지 (동시 참가 race condition 방지)
  - status=waiting인지
  - 이미 참가 중인 방 없는지
→ 성공 시 /room/[id]로 이동
→ 실패 시 에러 토스트 (꽉 참 / 이미 시작됨 / 이미 다른 방 참가 중)
```

#### Long Polling 연결 끊김 대응
```
폴링 실패 시 React Query retry 3회
→ 실패 지속 시 "연결이 끊겼어요. 재연결 중..." 토스트
→ 재연결 성공 시 자동 재개
```

### 2.3 대기실

#### 진입
```
해당 방의 room_member인지 JWT로 검증
→ 아니면 /lobby로 리다이렉트
→ 이미 배틀 시작된 상태면 /battle/[id]로 리다이렉트
→ 참가자 목록 로드 + Short Polling 시작 (2초)
```

#### 4명 자동 시작
```
4번째 참가자 입장 감지 (서버)
→ battle 생성 + room status=active + ends_at = NOW() + 3분
→ 폴링 응답에 battleStarted: true + battleId 포함
→ 모든 참가자 /battle/[id]로 이동
```

#### 방장 수동 시작 (2~3명)
```
시작 버튼 클릭 (방장에게만 표시)
→ 서버: 방장 여부 + 인원 2명 이상 + 중복 배틀 없는지 확인
→ battle 생성 + room status=active
→ 폴링 응답으로 모든 참가자 /battle/[id]로 이동
```

#### 방장 나가기
```
방장이 나가기 누름
→ room status=closed + room_members 전체 삭제
→ 나머지 참가자 폴링에서 roomClosed: true 감지
→ "방장이 나가서 방이 닫혔어요" 토스트 + /lobby 이동
```

#### 일반 참가자 나가기
```
나가기 누름
→ room_members에서 본인 제거
→ 나머지 참가자 폴링에서 슬롯 빈칸으로 갱신
```

#### 창 강제 이탈 대응
```
beforeunload 이벤트 → /api/rooms/[id]/leave 호출
방장이면 방 닫힘, 일반이면 슬롯 제거
```

### 2.4 배틀

#### 진입
```
해당 battle 참가자인지 검증
→ 아니면 /lobby로 리다이렉트
→ 포트폴리오 로드 (없으면 자동 생성: 초기 자금 1,000,000원)
→ 주가 폴링 시작 (10초)
→ 순위 폴링 시작 (10초)
→ 타이머: 서버 ends_at 기준으로 남은 시간 계산
```

#### 배틀 중 새로고침
```
/battle/[id] 재접근
→ 참가자 검증
→ 기존 포트폴리오 로드 (소지금, 보유 종목 유지)
→ 타이머 서버 ends_at 기준으로 재계산
→ 자연스럽게 배틀 재합류
```

#### 매수
```
수량 설정 후 매수 클릭
→ 수량 0이면 버튼 비활성
→ 클라이언트: 소지금 충분한지 1차 검증
→ Zustand 낙관적 업데이트 (소지금 차감, 보유 즉시 반영)
→ 서버: SELECT FOR UPDATE로 소지금 재확인 + ends_at 지났으면 거절
→ 성공: Zustand 서버 응답값으로 확정
→ 실패: Zustand 롤백 + 에러 토스트
```

#### 매도
```
수량 설정 후 매도 클릭
→ 수량 0이거나 보유량 초과 시 버튼 비활성
→ 클라이언트: 보유 수량 충분한지 1차 검증
→ Zustand 낙관적 업데이트 (소지금 증가, 보유 차감)
→ 서버: 보유 수량 재확인 + ends_at 확인
→ 성공: Zustand 확정
→ 실패: Zustand 롤백 + 에러 토스트
```

#### 배틀 종료
```
폴링 응답에 battleEnded: true 포함
→ 매수/매도 버튼 즉시 비활성화
→ "배틀 종료! 3초 후 결과 화면으로 이동합니다" 표시
→ 3초 후 /result/[id]로 이동
```

#### 배틀 중 이탈
```
별도 처리 없음
→ 포트폴리오 마지막 상태 그대로 유지
→ 순위와 결과에 반영
→ 재접속하면 배틀 재합류 가능 (남은 시간 있으면)
```

#### 타이머 동기화
```
클라이언트 타이머는 표시용 (1초마다 감소)
폴링마다 서버 ends_at과 동기화
실제 종료 판단은 서버 기준
```

### 2.5 결과

#### 진입
```
해당 battle 참가자인지 검증
→ 아니면 /lobby로 리다이렉트
→ 배틀 아직 진행 중이면 /battle/[id]로 리다이렉트
→ 최종 순위 + 자산 + 수익률 로드
→ 1등 하이라이트 (황금 배경 + 왕관)
```

#### 다시하기
```
다시하기 클릭
→ 기존 room으로 대기실 재진입 시도
→ room status=waiting → room_members 재추가 → /room/[id]
→ room status=active → /battle/[id]로 바로 이동
→ room status=closed → "방이 닫혔어요" 토스트 + /lobby
```

#### 로비로
```
로비로 클릭
→ 방장이면 room status=closed
→ /lobby로 이동
```

---

## 3. 시스템 구조

### 3.1 전체 아키텍처
```
Cloudflare (DNS + SSL)
    ↓ HTTPS
GCP VM (Ubuntu)
  ├── Next.js 앱 (포트 3000)
  │     ├── App Router (페이지)
  │     └── Route Handlers (API)
  ├── MariaDB (포트 3306, 내부 전용)
  └── PM2 (프로세스 관리)
```

### 3.2 클라이언트 상태 관리

#### Zustand (로컬 상태 — 즉각 반응 필요한 것)
```typescript
// authStore
{ userId, nickname, token }

// battleStore
{
  cashBalance: number,
  holdings: { stockId, quantity, avgBuyPrice }[],
  timer: { endsAt, remaining },
  tradeQty: { [stockId]: number }
}
```

#### React Query (서버 상태 — 주기적으로 받아오는 것)
```
rooms        → Long Polling (최대 30초 대기)
roomMembers  → Short Polling (2초)
stockPrices  → Short Polling (10초)
rankings     → Short Polling (10초)
```

### 3.3 낙관적 업데이트 흐름
```
매수 클릭
→ Zustand: cashBalance 차감, holdings 즉시 반영
→ POST /api/battles/[id]/trade 전송
→ 성공: Zustand 서버 응답값으로 확정
→ 실패: Zustand 이전 상태로 롤백 + 에러 토스트
```

### 3.4 주가 변동 생성 방식 (요청 driven)
```
Next.js Route Handler에서 setInterval 유지가 어려우므로
"요청 driven" 방식으로 구현한다.

GET /api/battles/[id]/prices 요청이 올 때:
1. stock_prices에서 해당 배틀의 마지막 기록 시간 확인
2. 10초 이상 지났으면 새 가격 생성 후 insert
3. 최신 가격 반환

별도 백그라운드 프로세스 불필요.
```

변동률 계산:
```
새 가격 = 현재가 × (1 + (Math.random() - 0.5) × 2 × volatility)
```

### 3.5 순위 계산 로직
```
각 참가자별:
소지금 + SUM(보유수량 × 현재가) = 총 자산
총 자산 내림차순 정렬
```

### 3.6 Long Polling 동작 (로비)
```
GET /api/rooms/poll 요청 도착
→ 서버가 현재 방 목록 스냅샷 저장
→ 최대 30초 대기 루프 (1초마다 DB 조회)
→ 변화 감지되면 즉시 응답
→ 30초 경과해도 변화 없으면 현재 상태 그대로 응답
→ 클라이언트 응답 받는 즉시 다음 요청 전송
```

### 3.7 리다이렉트 가드 (middleware.ts)
```
비로그인 → /lobby, /room/*, /battle/*, /result/* 접근 시
  → / 리다이렉트

로그인 상태 → / 접근 시
  → /lobby 리다이렉트
```

---

## 4. DB 스키마

### 4.1 users
```sql
CREATE TABLE users (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  nickname      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 4.2 rooms
```sql
CREATE TABLE rooms (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(100) NOT NULL,
  host_id     INT UNSIGNED NOT NULL,
  status      ENUM('waiting', 'active', 'closed') NOT NULL DEFAULT 'waiting',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (host_id) REFERENCES users(id)
);
```

### 4.3 room_members
```sql
CREATE TABLE room_members (
  id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  room_id    INT UNSIGNED NOT NULL,
  user_id    INT UNSIGNED NOT NULL,
  joined_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_room_user (room_id, user_id),
  FOREIGN KEY (room_id) REFERENCES rooms(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);
```
- 나가면 row DELETE
- UNIQUE KEY로 같은 사람 같은 방 중복 참가 방지

### 4.4 stocks
```sql
CREATE TABLE stocks (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  symbol        VARCHAR(20)  NOT NULL UNIQUE,
  name          VARCHAR(50)  NOT NULL,
  volatility    DECIMAL(5,4) NOT NULL,
  initial_price INT UNSIGNED NOT NULL
);
```

시드 데이터 (서버 초기화 시 삽입):
```sql
INSERT INTO stocks (symbol, name, volatility, initial_price) VALUES
  ('SAFE-X', '세이프엑스', 0.0050, 50000),
  ('GROW-X', '그로우엑스', 0.0150, 100000),
  ('VOLT-X', '볼트엑스',   0.0300, 80000),
  ('MOON-X', '문엑스',     0.0500, 30000);
```
- 변동성(volatility): SAFE-X ±0.5%, GROW-X ±1.5%, VOLT-X ±3%, MOON-X ±5%

### 4.5 battles
```sql
CREATE TABLE battles (
  id           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  room_id      INT UNSIGNED NOT NULL UNIQUE,
  status       ENUM('active', 'ended') NOT NULL DEFAULT 'active',
  initial_cash INT UNSIGNED NOT NULL DEFAULT 1000000,
  started_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ends_at      DATETIME     NOT NULL,
  FOREIGN KEY (room_id) REFERENCES rooms(id)
);
```
- room_id UNIQUE: 방 하나당 배틀 하나
- ends_at: started_at + 3분으로 계산하여 insert

### 4.6 stock_prices
```sql
CREATE TABLE stock_prices (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  battle_id   INT UNSIGNED NOT NULL,
  stock_id    INT UNSIGNED NOT NULL,
  price       INT UNSIGNED NOT NULL,
  recorded_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_battle_stock_time (battle_id, stock_id, recorded_at),
  FOREIGN KEY (battle_id) REFERENCES battles(id),
  FOREIGN KEY (stock_id)  REFERENCES stocks(id)
);
```
- 10초마다 4개 종목 insert → 3분 배틀 시 최대 4 × 18 = 72개 row

### 4.7 portfolios
```sql
CREATE TABLE portfolios (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  battle_id     INT UNSIGNED NOT NULL,
  user_id       INT UNSIGNED NOT NULL,
  cash_balance  INT UNSIGNED NOT NULL,
  UNIQUE KEY uq_battle_user (battle_id, user_id),
  FOREIGN KEY (battle_id) REFERENCES battles(id),
  FOREIGN KEY (user_id)   REFERENCES users(id)
);
```
- 배틀 시작 시 initial_cash(1,000,000) 값으로 생성

### 4.8 holdings
```sql
CREATE TABLE holdings (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  portfolio_id  INT UNSIGNED NOT NULL,
  stock_id      INT UNSIGNED NOT NULL,
  quantity      INT UNSIGNED NOT NULL,
  avg_buy_price INT UNSIGNED NOT NULL,
  UNIQUE KEY uq_portfolio_stock (portfolio_id, stock_id),
  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
  FOREIGN KEY (stock_id)     REFERENCES stocks(id)
);
```
- 매수 시 ON DUPLICATE KEY UPDATE로 평균단가 갱신
- 평균단가 = (기존 avg × 기존 qty + 매수가 × 매수qty) / (기존 qty + 매수qty)
- 전량 매도 시 row DELETE

### 4.9 transactions
```sql
CREATE TABLE transactions (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  portfolio_id  INT UNSIGNED NOT NULL,
  stock_id      INT UNSIGNED NOT NULL,
  type          ENUM('buy', 'sell') NOT NULL,
  quantity      INT UNSIGNED NOT NULL,
  price         INT UNSIGNED NOT NULL,
  executed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_portfolio_time (portfolio_id, executed_at),
  FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
  FOREIGN KEY (stock_id)     REFERENCES stocks(id)
);
```
- append only, 절대 수정/삭제 안 함

---

## 5. API 명세

### 5.0 공통 응답 구조
```typescript
// 성공
{ success: true, data: { ... } }

// 실패
{ success: false, error: { code: string, message: string } }
```

모든 API는 이 구조를 따른다.

### 5.0.1 에러 코드 체계
```
AUTH_EMAIL_EXISTS - 이메일 중복
AUTH_NICK_EXISTS  - 닉네임 중복
AUTH_INVALID      - 이메일/비밀번호 불일치
AUTH_001          - 토큰 없음
AUTH_002          - 토큰 만료
AUTH_003          - 권한 없음
ROOM_001          - 방 없음
ROOM_002          - 방 꽉 참
ROOM_003          - 이미 참가 중인 방 있음
ROOM_004          - 이미 시작된 방
ROOM_005          - 인원 부족 (2명 미만)
BATTLE_001        - 배틀 없음
BATTLE_002        - 배틀 종료됨
BATTLE_003        - 소지금 부족
BATTLE_004        - 보유 수량 부족
BATTLE_EXISTS     - 이미 배틀 생성됨
```

### 5.1 인증 API

#### POST /api/auth/register
```typescript
Request Body:
{ email: string, nickname: string, password: string }

Response:
{ token: string, user: { id: number, email: string, nickname: string } }
```

#### POST /api/auth/login
```typescript
Request Body:
{ email: string, password: string }

Response:
{ token: string, user: { id: number, email: string, nickname: string } }
```

#### GET /api/auth/me
```typescript
Headers: Authorization: Bearer {token}

Response:
{
  user: { id, email, nickname },
  activeRoom: {
    roomId: number,
    battleId: number | null,
    status: 'waiting' | 'active' | 'ended'
  } | null
}
```
- activeRoom이 있으면 클라이언트가 적절한 페이지로 리다이렉트

### 5.2 로비 API

#### GET /api/rooms
```typescript
Response:
{
  rooms: [{
    id: number,
    title: string,
    hostNickname: string,
    memberCount: number,
    maxMembers: 4,
    status: 'waiting'
  }]
}
```
- status=waiting인 방만 반환

#### GET /api/rooms/poll
```typescript
Response:
{
  rooms: [...],
  timestamp: number
}
```
- 변화 감지 시 또는 최대 30초 후 응답

#### POST /api/rooms
```typescript
Request Body:
{ title: string }

Response:
{ roomId: number }

에러: ROOM_003 (이미 참가 중인 방 있음)
```

#### POST /api/rooms/[id]/join
```typescript
Response:
{ roomId: number }

에러: ROOM_001, ROOM_002, ROOM_003, ROOM_004
```

### 5.3 대기실 API

#### GET /api/rooms/[id]
```typescript
Response:
{
  id: number,
  title: string,
  hostId: number,
  status: string,
  members: [{ userId: number, nickname: string, isHost: boolean }],
  battleStarted: boolean,
  battleId: number | null
}
```

#### POST /api/rooms/[id]/start
```typescript
Response:
{ battleId: number }

에러: AUTH_003 (방장 아님), ROOM_005 (인원 부족), BATTLE_EXISTS (중복 방지)
```

#### POST /api/rooms/[id]/leave
```typescript
Response:
{ success: true }

서버 처리:
- 방장이면 → room status=closed, room_members 전체 삭제
- 일반이면 → 본인 room_members row 삭제
```

### 5.4 배틀 API

#### GET /api/battles/[id]
```typescript
Response:
{
  id: number,
  status: string,
  endsAt: string,
  initialCash: number,
  participants: [{ userId: number, nickname: string }]
}
```

#### GET /api/battles/[id]/portfolio
```typescript
Response:
{
  cashBalance: number,
  holdings: [{
    stockId: number,
    symbol: string,
    quantity: number,
    avgBuyPrice: number
  }]
}
```
- 없으면 자동 생성(initial_cash 기준) 후 반환

#### GET /api/battles/[id]/prices
```typescript
Response:
{
  prices: [{
    stockId: number,
    symbol: string,
    currentPrice: number,
    initialPrice: number,
    changeRate: number,
    history: [{ price: number, recordedAt: string }]
  }],
  battleEnded: boolean,
  endsAt: string
}
```
- 요청 시 마지막 기록 시간 확인 → 10초 지났으면 새 가격 생성 후 반환
- history: 직전 5개 (차트용)
- changeRate: (currentPrice - initialPrice) / initialPrice

#### GET /api/battles/[id]/rankings
```typescript
Response:
{
  rankings: [{
    rank: number,
    userId: number,
    nickname: string,
    totalAssets: number,
    changeRate: number
  }]
}
```
- totalAssets = 소지금 + SUM(보유수량 × 현재가)
- changeRate = (totalAssets - initialCash) / initialCash

#### POST /api/battles/[id]/trade
```typescript
Request Body:
{ stockId: number, type: 'buy' | 'sell', quantity: number }

Response:
{
  cashBalance: number,
  holding: {
    stockId: number,
    quantity: number,
    avgBuyPrice: number
  } | null
}
```
- holding이 null이면 전량 매도된 것
- 서버에서 SQL 트랜잭션 + SELECT FOR UPDATE 사용

에러: BATTLE_002, BATTLE_003, BATTLE_004, AUTH_003

매수 트랜잭션 SQL:
```sql
BEGIN;
  SELECT cash_balance FROM portfolios WHERE id = ? FOR UPDATE;
  -- 잔고 부족 시 ROLLBACK
  UPDATE portfolios SET cash_balance = cash_balance - (price × quantity) WHERE id = ?;
  INSERT INTO holdings (portfolio_id, stock_id, quantity, avg_buy_price)
  VALUES (?, ?, ?, ?)
  ON DUPLICATE KEY UPDATE
    avg_buy_price = (avg_buy_price * quantity + ? * ?) / (quantity + ?),
    quantity = quantity + ?;
  INSERT INTO transactions (portfolio_id, stock_id, type, quantity, price)
  VALUES (?, ?, 'buy', ?, ?);
COMMIT;
```

### 5.5 결과 API

#### GET /api/battles/[id]/result
```typescript
Response:
{
  rankings: [{
    rank: number,
    userId: number,
    nickname: string,
    finalAssets: number,
    changeRate: number,
    isMe: boolean
  }]
}
```

#### POST /api/battles/[id]/retry
```typescript
Response:
{
  redirect: 'room' | 'battle' | 'lobby',
  roomId?: number,
  battleId?: number
}
```
- room status=waiting → room_members 재추가 → redirect: room
- room status=active → redirect: battle
- room status=closed → redirect: lobby

---

## 6. 화면 구성

### 6.1 공통 레이아웃
- 모든 페이지에 공통 헤더 적용 (Next.js layout.tsx)
- 헤더: 좌측 로고 "StockBattle" + 우측 닉네임 + 로그아웃 버튼
- 로그인 페이지(/)는 헤더 없이 독립 레이아웃

### 6.2 로그인/회원가입 (/)
- 로고 + 슬로건 한 줄 ("5분 안에 승부나는 모의 주식 배틀" 등)
- 로그인 폼 (이메일, 비밀번호)
- 회원가입 전환 링크
- 깔끔한 디자인, 후킹 멘트로 서비스 분위기 전달

### 6.3 로비 (/lobby)
- 상단: 동적 서브 멘트 "현재 N개의 방이 대기 중이에요"
- 상단 우측: 방 만들기 버튼
- 방 목록 (2열 그리드):
  - 방 카드: 방 이름, 참가자 수 (슬롯 동그라미로 시각화), 참가 버튼
  - status=waiting인 방만 표시
- 빈 상태: "아직 열린 방이 없어요. 첫 번째로 만들어보세요!"
- Long Polling으로 실시간 갱신

### 6.4 대기실 (/room/[id])
- 중앙 정렬
- 방 이름 + "참가자 2/4 · 2명 더 오면 자동 시작돼요"
- 참가자 슬롯 4개 (2×2 그리드)
  - 채워진 슬롯: 아바타(이니셜) + 닉네임 + 배지(방장/나)
  - 빈 슬롯: 점선 + "대기 중..."
- 대기 애니메이션 (점 3개 깜빡임)
- 시작 버튼 (방장만 표시, 2명 이상 시 활성)
- 나가기 버튼
- Short Polling 2초

### 6.5 배틀 (/battle/[id]) — 옵션 A 확정
- **상단 HUD**: 타이머(빨간 점 + 카운트다운) | 소지금 | 총 자산 | 수익률
- **좌측 메인**: 종목 카드 2×2 그리드 (4개 종목)
- **우측 사이드**: 실시간 순위 패널

종목 카드 구성:
```
종목명 + 이름           변동성 배지
[차트 — 기준선 + 직전 5개 포인트, 종목별 고유 색상]
현재가        등락률 (시작가 기준)
보유 N주 · 평균 N원   (미보유 시 "미보유")
[매도]  [-] 수량 [+]  [매수]
```

차트 규칙:
- 기준선: 시작가를 가로 실선으로 표시
- 직전 5개 가격 포인트를 선으로 연결
- 색상은 종목마다 고정 (SAFE-X 초록, GROW-X 주황, VOLT-X 보라, MOON-X 분홍)
- 마지막 포인트에 동그라미로 현재 위치 표시

매수/매도 버튼:
- 매도: 파란색 버튼 (좌측)
- 매수: 빨간색 버튼 (우측)
- 수량 조절: 가운데 [-] 숫자 [+]

순위 패널:
- "실시간 순위" 제목
- 순위 카드 × 4: 순위 번호, 닉네임, 총 자산, 수익률
- 내 항목: 빨간 하이라이트 테두리
- 리렌더링으로 갱신 (모션 없음)

### 6.6 결과 (/result/[id])
- "최종 결과" 타이틀
- 순위 리스트 (세로 배치)
  - 1등: 황금 배경 + 왕관 아이콘 + 하이라이트
  - 나머지: 심플 카드
  - 내 항목: "나" 배지
- 하단 버튼: 로비로 (보조) | 다시하기 (주)

---

## 7. Error Boundary

### 7.1 파일 구조
```
app/
  error.tsx              ← 전역 fallback
  not-found.tsx          ← 404
  lobby/error.tsx        ← 로비 전용
  room/[id]/error.tsx    ← 대기실 전용
  battle/[id]/error.tsx  ← 배틀 전용
  result/[id]/error.tsx  ← 결과 전용
```

### 7.2 에러 케이스별 처리

#### 인증 관련 (모든 페이지)
- JWT 만료/없음 → "로그인이 필요해요" → / 이동

#### 로비
- 방 목록 로드 실패 → "방 목록을 불러오지 못했어요" → 재시도 버튼
- 방 만들기 실패 → "방을 만들지 못했어요" → 모달 닫고 제자리
- 방 참가 실패 (꽉 참) → "이미 꽉 찬 방이에요" → 제자리
- 방 참가 실패 (진행중) → "이미 시작된 배틀이에요" → 제자리

#### 대기실
- 참가자 아닌데 접근 → "잘못된 접근이에요" → /lobby
- 방장 나감 → "방장이 나가서 방이 닫혔어요" → /lobby
- 방 정보 로드 실패 → "방 정보를 불러오지 못했어요" → /lobby

#### 배틀
- 참가자 아닌데 접근 → "잘못된 접근이에요" → /lobby
- 매수 실패 (소지금 부족) → "소지금이 부족해요" → 제자리
- 매수/매도 실패 (서버 오류) → "거래에 실패했어요" → 제자리
- 매도 실패 (수량 부족) → "보유 수량이 부족해요" → 제자리
- 배틀 강제 종료 → "배틀이 예기치 않게 종료됐어요" → /result/[id] 또는 /lobby

#### 결과
- 참가자 아닌데 접근 → "잘못된 접근이에요" → /lobby
- 다시하기 시 방 닫힘 → "방장이 나가서 방이 닫혔어요" → /lobby
- 결과 로드 실패 → "결과를 불러오지 못했어요" → /lobby

#### 구현 방식
- 예측 가능한 에러: 각 컴포넌트 안에서 토스트/인라인 메시지 처리
- 예측 불가능한 에러: Next.js error.tsx로 fallback

---

## 8. 폴더 구조

```
stockbattle/
├── app/
│   ├── layout.tsx
│   ├── page.tsx                      /
│   ├── error.tsx
│   ├── not-found.tsx
│   │
│   ├── lobby/
│   │   ├── layout.tsx                공통 헤더 레이아웃
│   │   ├── page.tsx                  /lobby
│   │   └── error.tsx
│   │
│   ├── room/
│   │   └── [id]/
│   │       ├── page.tsx              /room/[id]
│   │       └── error.tsx
│   │
│   ├── battle/
│   │   └── [id]/
│   │       ├── page.tsx              /battle/[id]
│   │       └── error.tsx
│   │
│   ├── result/
│   │   └── [id]/
│   │       ├── page.tsx              /result/[id]
│   │       └── error.tsx
│   │
│   └── api/
│       ├── auth/
│       │   ├── register/route.ts
│       │   ├── login/route.ts
│       │   └── me/route.ts
│       │
│       ├── rooms/
│       │   ├── route.ts              GET 목록 / POST 생성
│       │   ├── poll/route.ts         GET long polling
│       │   └── [id]/
│       │       ├── route.ts          GET 방 정보
│       │       ├── join/route.ts
│       │       ├── leave/route.ts
│       │       └── start/route.ts
│       │
│       └── battles/
│           └── [id]/
│               ├── route.ts          GET 배틀 메타
│               ├── portfolio/route.ts
│               ├── prices/route.ts
│               ├── rankings/route.ts
│               ├── trade/route.ts
│               ├── result/route.ts
│               └── retry/route.ts
│
├── components/
│   ├── layout/
│   │   └── Header.tsx
│   ├── auth/
│   │   ├── LoginForm.tsx
│   │   └── RegisterForm.tsx
│   ├── lobby/
│   │   ├── RoomList.tsx
│   │   ├── RoomCard.tsx
│   │   └── CreateRoomModal.tsx
│   ├── room/
│   │   ├── WaitingRoom.tsx
│   │   └── PlayerSlot.tsx
│   ├── battle/
│   │   ├── BattleScreen.tsx
│   │   ├── HUD.tsx
│   │   ├── StockGrid.tsx
│   │   ├── StockCard.tsx
│   │   ├── Sparkline.tsx
│   │   └── RankingPanel.tsx
│   ├── result/
│   │   ├── ResultScreen.tsx
│   │   └── RankingList.tsx
│   └── ui/
│       └── Toast.tsx
│
├── lib/
│   ├── db.ts                         MariaDB 연결 풀
│   ├── auth.ts                       JWT 발급/검증 (jose)
│   ├── middleware.ts                  API 인증 미들웨어
│   └── errors.ts                     에러 코드 상수
│
├── store/
│   ├── authStore.ts                  Zustand: 유저/JWT
│   └── battleStore.ts                Zustand: 포트폴리오/타이머
│
├── hooks/
│   ├── useRoomPoll.ts                Long Polling
│   ├── useWaitingRoom.ts             대기실 Short Polling
│   ├── usePrices.ts                  주가 Short Polling
│   └── useRankings.ts                순위 Short Polling
│
├── types/
│   └── index.ts                      공통 타입 정의
│
├── middleware.ts                     Next.js 라우트 가드
├── .env.local
├── next.config.ts
└── package.json
```

---

## 9. 환경 변수 (.env.local)

```
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=stockuser
DB_PASSWORD=password
DB_NAME=stockbattle
JWT_SECRET=stockbattle_jwt_secret_key
TOKEN_COOKIE_NAME=token
NODE_ENV=development
```

---

## 10. GCP 배포 순서

```bash
# 1. Next.js 프로젝트 생성
npx create-next-app@latest stockbattle --typescript
cd stockbattle

# 2. next.config.ts 수정
cat > next.config.ts <<'EOF'
import type { NextConfig } from 'next';
const nextConfig: NextConfig = {
  allowedDevOrigins: ['*.trycloudflare.com'],
  experimental: {
    serverActions: {
      allowedOrigins: ['*.trycloudflare.com'],
    },
  },
};
export default nextConfig;
EOF

# 3. 패키지 설치
npm install mysql2 bcryptjs jose zustand @tanstack/react-query
npm install -D tsx @types/bcryptjs

# 4. MariaDB 세팅
sudo mysql
CREATE DATABASE stockbattle CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'stockuser'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON stockbattle.* TO 'stockuser'@'localhost';
FLUSH PRIVILEGES;
USE stockbattle;
-- 위 4장의 CREATE TABLE 및 INSERT INTO stocks 실행

# 5. .env.local 작성
nano .env.local

# 6. 파일질라로 코드 업로드 (node_modules, .env.local 제외)

# 7. 빌드 + 실행
npm run build
nohup npm run start > app.log 2>&1 &
disown -a

# 8. Cloudflared 터널
nohup cloudflared tunnel --url http://localhost:3000 > tunnel.log 2>&1 &
disown -a
```

---

## 11. 패키지 목록

```json
{
  "dependencies": {
    "mysql2": "DB 연결",
    "bcryptjs": "비밀번호 해시",
    "jose": "JWT 발급/검증",
    "zustand": "클라이언트 상태 관리",
    "@tanstack/react-query": "서버 상태 관리 / 폴링"
  },
  "devDependencies": {
    "tsx": "TypeScript 실행",
    "@types/bcryptjs": "타입 정의"
  }
}
```

---

## 12. 핵심 구현 주의사항

### 12.1 Race Condition 방지
- 매수/매도 시 반드시 SQL 트랜잭션 + SELECT FOR UPDATE 사용
- 방 참가 시 동시 요청으로 4명 초과 방지: INSERT 전 COUNT 확인

### 12.2 타이머 관리
- 클라이언트 타이머는 표시용 (setInterval 1초)
- 실제 종료 판단은 서버 ends_at 기준
- 폴링마다 서버 endsAt 값으로 동기화

### 12.3 가격 생성 (요청 driven)
```typescript
// /api/battles/[id]/prices 내부 로직
const lastRecorded = await getLastRecordedTime(battleId);
const elapsed = Date.now() - lastRecorded;
if (elapsed >= 10000) {
  await generateNewPrices(battleId);
}
const prices = await getLatestPrices(battleId);
```

### 12.4 배틀 종료 감지
```typescript
// 폴링 응답에 항상 포함
const battle = await getBattle(battleId);
const battleEnded = new Date(battle.ends_at) <= new Date();
if (battleEnded && battle.status === 'active') {
  await updateBattleStatus(battleId, 'ended');
}
return { prices, battleEnded, endsAt: battle.ends_at };
```

### 12.5 평균단가 계산
```
새 평균단가 = (기존 avg × 기존 qty + 매수가 × 매수qty) / (기존 qty + 매수qty)
```
SQL ON DUPLICATE KEY UPDATE로 처리:
```sql
avg_buy_price = (avg_buy_price * quantity + VALUES(avg_buy_price) * VALUES(quantity)) / (quantity + VALUES(quantity)),
quantity = quantity + VALUES(quantity)
```

### 12.6 순위 계산 SQL
```sql
SELECT
  u.nickname,
  p.cash_balance,
  p.cash_balance + COALESCE(SUM(h.quantity * latest.price), 0) AS total_assets
FROM portfolios p
JOIN users u ON p.user_id = u.id
LEFT JOIN holdings h ON h.portfolio_id = p.id
LEFT JOIN (
  SELECT stock_id, price
  FROM stock_prices sp1
  WHERE battle_id = ?
    AND recorded_at = (
      SELECT MAX(recorded_at) FROM stock_prices sp2
      WHERE sp2.stock_id = sp1.stock_id AND sp2.battle_id = ?
    )
) latest ON latest.stock_id = h.stock_id
WHERE p.battle_id = ?
GROUP BY p.id
ORDER BY total_assets DESC;
```

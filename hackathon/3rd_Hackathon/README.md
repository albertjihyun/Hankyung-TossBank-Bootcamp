# StockBattle — Real-Time Mock Stock Trading Battle

A room-based platform where up to 4 players compete by trading virtual stocks within a 3-minute session.  
Built end-to-end in 8 hours: from database design to cloud deployment.

---

# 1. Project Overview

## Project Topic

**StockBattle** is a room-based real-time mock stock trading battle platform.

- Users can create or join a room and trade among 4 virtual stocks over a 3-minute session, competing by portfolio return.
- Initial capital: 1,000,000 KRW / Up to 4 players per room
- Stock prices fluctuate every 10 seconds:
  - SAFE-X ±0.5%
  - GROW-X ±1.5%
  - VOLT-X ±3%
  - MOON-X ±5%

## Tech Stack

**React, Next.js** (App Router), TypeScript, Tailwind CSS, **React Query** (TanStack Query), **Zustand**, MariaDB, JWT (jose), bcryptjs, GCP, Cloudflare

---

# 2. Backend Architecture and Routing

## Implementation Approach

Implemented using **Next.js Route Handlers** (`app/api/...`)

- Managed pages and APIs within a single Next.js application without a separate Express server.
- All protected APIs share JWT authentication through the `authenticate()` function in `lib/apiMiddleware.ts`.
- Standardized response format:
  - Success: `{ success: true, data: {...} }`
  - Failure: `{ success: false, error: { code, message } }`

## Main API Routes

### Authentication
- `POST /api/auth/register`
  - User registration (duplicate email/nickname checks, bcrypt hashing)

- `POST /api/auth/login`
  - Login and JWT issuance

- `GET /api/auth/me`
  - Fetch current user and active room information

- `POST /api/auth/logout`
  - Clear token cookie

### Room Management
- `GET /api/rooms`
  - Retrieve waiting room list

- `POST /api/rooms`
  - Create room

- `GET /api/rooms/poll`
  - Lobby room list polling

- `GET /api/rooms/[id]`
  - Room detail and participant list (used for waiting room polling)

- `POST /api/rooms/[id]/join`
  - Join room (capacity / duplicate / already-started validation)

- `POST /api/rooms/[id]/leave`
  - Leave room (if host exits, room closes for all)

- `POST /api/rooms/[id]/start`
  - Host manually starts battle (minimum 2 players)

### Battle APIs
- `GET /api/battles/[id]`
  - Battle metadata and participant list

- `GET /api/battles/[id]/portfolio`
  - Retrieve portfolio (auto-created if absent)

- `GET /api/battles/[id]/prices`
  - Current prices + 5-point history + battle completion check

- `GET /api/battles/[id]/rankings`
  - Live rankings

- `POST /api/battles/[id]/trade`
  - Buy / sell execution (DB transaction)

- `GET /api/battles/[id]/result`
  - Final results

- `POST /api/battles/[id]/retry`
  - Retry battle

---

# 3. Database and SQL Usage

## Tables Used

### users
User information  
- `email UNIQUE`
- `nickname UNIQUE`
- `password_hash`

### rooms
Room metadata  
- waiting / active / closed

### room_members
Room participants  
- Duplicate joins prevented via `UNIQUE KEY`
- Row deleted when a user leaves

### stocks
4 virtual stocks  
- symbol
- volatility
- initial_price

### battles
Battle metadata  
- `room_id UNIQUE`
- `ends_at = start + 3 min`

### stock_prices
Price history  
- 4 stock rows inserted every 10 seconds

### portfolios
User cash balance  
- initialized at 1,000,000

### holdings
Stock holdings  
- `ON DUPLICATE KEY UPDATE`
- Deletes row on full liquidation

### transactions
Append-only trade records  
- No updates or deletes

## Key SQL

### Buy Transaction — Preventing Race Conditions via SELECT FOR UPDATE

```sql
BEGIN;
  SELECT id, cash_balance FROM portfolios
  WHERE battle_id = ? AND user_id = ? FOR UPDATE;

  UPDATE portfolios
  SET cash_balance = cash_balance - (price * quantity)
  WHERE id = ?;

  INSERT INTO holdings (portfolio_id, stock_id, quantity, avg_buy_price)
  VALUES (?, ?, ?, ?)
  ON DUPLICATE KEY UPDATE
    avg_buy_price =
      (avg_buy_price * quantity + VALUES(avg_buy_price) * VALUES(quantity))
      / (quantity + VALUES(quantity)),
    quantity = quantity + VALUES(quantity);

  INSERT INTO transactions (portfolio_id, stock_id, type, quantity, price)
  VALUES (?, ?, 'buy', ?, ?);
COMMIT;
```

### Automatic Price Generation — Request-driven with NOT EXISTS

```sql
INSERT INTO stock_prices (battle_id, stock_id, price)
SELECT sp_ref.battle_id, sp_ref.stock_id,
  GREATEST(
    1,
    ROUND(sp_ref.price * (1 + (RAND() - 0.5) * 2 * st.volatility))
  )
FROM (
  SELECT sp1.battle_id, sp1.stock_id, sp1.price
  FROM stock_prices sp1
  WHERE sp1.battle_id = ?
    AND sp1.recorded_at = (
      SELECT MAX(sp2.recorded_at)
      FROM stock_prices sp2
      WHERE sp2.battle_id = sp1.battle_id
        AND sp2.stock_id = sp1.stock_id
    )
) sp_ref
JOIN stocks st ON st.id = sp_ref.stock_id
WHERE NOT EXISTS (
  SELECT 1
  FROM stock_prices sp3
  WHERE sp3.battle_id = ?
    AND sp3.stock_id = sp_ref.stock_id
    AND sp3.recorded_at > DATE_SUB(NOW(), INTERVAL 10 SECOND)
);
```

### Live Ranking Calculation

```sql
SELECT u.nickname,
  p.cash_balance + COALESCE(SUM(h.quantity * latest.price), 0) AS total_assets
FROM portfolios p
JOIN users u ON p.user_id = u.id
LEFT JOIN holdings h ON h.portfolio_id = p.id
LEFT JOIN (
  SELECT stock_id, price
  FROM stock_prices sp1
  WHERE battle_id = ?
    AND recorded_at = (
      SELECT MAX(recorded_at)
      FROM stock_prices sp2
      WHERE sp2.stock_id = sp1.stock_id
        AND sp2.battle_id = ?
    )
) latest ON latest.stock_id = h.stock_id
WHERE p.battle_id = ?
GROUP BY p.id
ORDER BY total_assets DESC;
```

---

# 4. Frontend State Management and Data Optimization

## State Management Strategy

Used **Zustand** for client-local state, handling only data requiring immediate UI responsiveness.

### authStore
- Logged-in user information (`userId`, `nickname`, `token`)
- Persisted through localStorage

### battleStore
- In-battle portfolio state (`cashBalance`, `holdings`, `tradeQty`)
- Session-level state with optimistic updates

## Optimistic Update Flow

- User clicks buy/sell
- Immediate reflection in Zustand state
- Server success → confirm with server state
- Failure → rollback to previous state + error toast

## Server Data Management

Used **React Query useQuery** for polling-based server state.

### Polling Strategy

| Data Flow | Strategy |
|----------|----------|
| Lobby room list | Short Polling (1s) |
| Waiting room status | Short Polling (1s) |
| Stock prices | Short Polling (1s) |
| Live rankings | Short Polling (1s) |

Different polling strategies were applied based on each data flow's characteristics — balancing update frequency against the cost of repeated requests.

---

# 5. Troubleshooting (Problem Solving Log)

## Case 1: Real-time Update Strategy — Long Polling vs Short Polling

Multiple data flows were active simultaneously:
- Lobby rooms
- Waiting room participants
- Battle rankings
- Stock prices

Initially, I believed applying Long Polling to room synchronization and rankings was theoretically more efficient and better suited for real-time interactions.

However, after deployment, the UX did not feel as good as expected.

Small delays accumulated in ways that made updates feel unnatural, and occasional timing mismatches appeared. After switching to Short Polling, behavior became significantly more stable.

Even after resolving it, it did not feel like the problem was fully solved.

Short Polling improved UX, but repeated requests can increase server load, and bridging the gap between theoretical efficiency and practical user experience still feels like **an unresolved problem I need to work through.**

### Final Approach Applied

All data flows ended up on **Short Polling**, with intervals tuned per use case:

| Data Flow | Interval |
|----------|----------|
| Lobby room list | 1s |
| Waiting room status | 1s |
| Stock prices | 1s |
| Live rankings | 1s |

---

## Case 2: Inability to Run Background Processes in Next.js Route Handlers

The system needed stock prices to update every 10 seconds, but Next.js Route Handlers cannot maintain background processes such as `setInterval`.

Instead of running a separate process, I solved this with a **request-driven model**.

When `/api/battles/[id]/prices` is requested:

1. Check the timestamp of the last generated price  
2. If 10 seconds have passed, generate a new price  
3. Return updated results  

To prevent duplicate generation under concurrent requests, I embedded:

```sql
WHERE NOT EXISTS ...
AND recorded_at > DATE_SUB(NOW(), INTERVAL 10 SECOND)
```

directly in the INSERT query so duplication is prevented at the database level.

This allowed price generation without background workers while preserving consistency.

---

# 6. What I Learned

This project pushed me to think less about simply shipping features and more about:

- Real-time system trade-offs  
- UX-driven architectural decisions  
- Database-level consistency strategies  
- Designing around framework constraints  

A major takeaway was:

> System design is often less about finding a perfect answer, and more about understanding the compromises behind each choice.

There is still a lot to improve, but this project helped me get closer to thinking like an engineer making design decisions, not just implementing features.

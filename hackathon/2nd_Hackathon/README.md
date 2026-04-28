# DevQ — Developer Q&A Community

> A community platform where developers ask questions and get answers.  
> Built end-to-end in 8 hours: from database design to cloud deployment.

**Stack:** HTML · Tailwind CSS · Vanilla JS · Node.js · Express · MariaDB · JWT · Cloudflare Tunnel

---

## Why DevQ?

Every developer hits walls — a weird error, an unfamiliar API, a concept that just won't click. Stack Overflow exists, but it can feel intimidating. DevQ is a lightweight, focused space where developers can ask questions without friction and get answers from peers.

The goal was simple: **a place where asking feels safe and answering feels rewarding.**

---

## Planning

### The Problem We're Solving

Developers spend a significant portion of their time stuck. Most of that time isn't spent on hard algorithmic problems — it's spent on environment issues, unclear error messages, or gaps in documentation. A focused Q&A platform with a low barrier to entry can cut that wasted time dramatically.

### Core User Flows

I identified three distinct user types and designed around their needs:

**Guest (not logged in)**
- Can browse all questions and read answers freely
- Encouraged to sign up when they want to contribute
- No forced login walls on read-only content — friction kills engagement

**Registered User**
- Can post questions with title, body, and tags
- Can answer any question
- Can edit or delete their own content only
- Ownership is enforced at the API level, not just the UI

**Admin**
- Full oversight: can delete any question or answer
- Access to a dedicated dashboard showing all users and questions
- Can remove users (cascades to their content via FK constraints)
- Role is assigned at registration — kept simple for hackathon scope

### Why These Features, In This Order

I prioritized features by **user value × implementation speed**:

1. **Auth first** — everything else depends on knowing who the user is
2. **Question CRUD** — the core loop of the product
3. **Answers** — what makes questions valuable
4. **Admin dashboard** — necessary for content moderation at any scale

Features intentionally left out (time constraints):
- Search / filtering by tag
- Upvoting answers
- Notifications
- Pagination

These aren't hard to add — the data model already supports most of them. They were cut to ship on time.

### Data Model Design

The schema is intentionally minimal but production-ready in structure.

```
users
  └── questions (user_id → users.id, CASCADE DELETE)
        └── answers (question_id → questions.id, CASCADE DELETE)
  └── answers (user_id → users.id, CASCADE DELETE)
```

`ON DELETE CASCADE` is used throughout. This means:
- Delete a user → their questions and answers disappear automatically
- Delete a question → its answers disappear automatically
- No orphaned rows, no manual cleanup needed

Tags are stored as a comma-separated string in a single `VARCHAR` column (`"javascript,node,express"`). Not normalized — a deliberate tradeoff. For a hackathon with no search requirement, a join table would be over-engineering.

---

## Tech Stack

| Layer | Choice | Reason |
|-------|--------|--------|
| Frontend | HTML + Tailwind CSS (CDN) + Vanilla JS | No build step, fast to write, readable output |
| Backend | Node.js + Express | Lightweight, fast to prototype |
| Auth | JWT + bcrypt | Stateless auth, industry standard |
| Database | MariaDB | MySQL-compatible, open source |
| Deployment | GCP VM + cloudflared | Persistent process, stable uptime |

**React was intentionally avoided.** For a content-heavy MPA (Multi-Page Application) with distinct pages per route, vanilla JS + HTML is faster to build and easier to debug under time pressure.

---

## Project Structure

```
/
├── server.js                  # Entry point — middleware, route mounting, static serving
├── db.js                      # MySQL2 connection pool
├── seed.js                    # Sample data (run once)
├── routes/
│   ├── auth.js                # POST /auth/register, /auth/login
│   ├── questions.js           # GET/POST/PUT/DELETE /api/questions
│   ├── answers.js             # POST /api/questions/:id/answers, DELETE /api/answers/:id
│   └── admin.js               # GET /api/admin/users|questions, DELETE /api/admin/users/:id
├── middleware/
│   ├── verifyToken.js         # JWT verification → attaches req.user
│   ├── requireAdmin.js        # Blocks non-admin access
│   └── checkOwnerOrAdmin.js   # Allows edit/delete only to owner or admin
└── public/
    ├── index.html             # Question list
    ├── question-detail.html   # Question + answers
    ├── question-form.html     # Create / edit question
    ├── login.html
    ├── register.html
    ├── admin.html             # Admin dashboard
    └── js/
        └── auth.js            # Shared auth utilities (getToken, getUser, logout…)
```

---

## API Reference

### Auth
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | — | Register with email, password, nickname, role |
| POST | `/auth/login` | — | Login → returns JWT |

### Questions
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/questions` | — | List all questions (newest first) |
| GET | `/api/questions/:id` | — | Question detail + answers |
| POST | `/api/questions` | user | Create question |
| PUT | `/api/questions/:id` | owner / admin | Edit question |
| DELETE | `/api/questions/:id` | owner / admin | Delete question |

### Answers
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/questions/:id/answers` | user | Post an answer |
| DELETE | `/api/answers/:id` | owner / admin | Delete an answer |

### Admin
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/admin/users` | admin | All users |
| GET | `/api/admin/questions` | admin | All questions |
| DELETE | `/api/admin/users/:id` | admin | Force delete user |

---

## Key SQL Patterns

```sql
-- Question list with author nickname (used in index.html)
SELECT q.id, q.title, q.tag, q.created_at, u.nickname
FROM questions q
JOIN users u ON q.user_id = u.id
ORDER BY q.created_at DESC;

-- Question detail (two separate queries)
SELECT q.*, u.nickname FROM questions q
JOIN users u ON q.user_id = u.id WHERE q.id = ?;

SELECT a.*, u.nickname FROM answers a
JOIN users u ON a.user_id = u.id
WHERE a.question_id = ? ORDER BY a.created_at ASC;

-- Ownership check (used in checkOwnerOrAdmin middleware)
SELECT user_id FROM questions WHERE id = ?;
SELECT user_id FROM answers WHERE id = ?;
```

---

## Deployment (GCP VM)

### Database setup (MariaDB shell — run once)

```sql
CREATE DATABASE testdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'testuser'@'localhost' IDENTIFIED BY '1234';
GRANT ALL PRIVILEGES ON testdb.* TO 'testuser'@'localhost';
FLUSH PRIVILEGES;

USE testdb;

CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(50) NOT NULL,
  role ENUM('user','admin') DEFAULT 'user',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE questions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  tag VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE answers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  question_id INT NOT NULL,
  user_id INT NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### Server setup (SSH — run once)

```bash
# Fix /var/www permissions
sudo chown -R $USER:$USER /var/www

# Install Node.js 20
sudo apt-get update -y
sudo apt-get install -y curl build-essential python3 ca-certificates gnupg
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs

# Install dependencies
cd /var/www
npm install express mysql2 jsonwebtoken bcrypt cors

# Seed sample data
node seed.js

# Install cloudflared
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared-linux-amd64.deb
```

### Start the server

```bash
cd /var/www

# Start Node server (detached from the shell session)
node server.js &
disown -a

# Start cloudflared tunnel (get public HTTPS URL)
cloudflared tunnel --url http://localhost:3000 &
disown -a
```

The tunnel outputs a URL like `https://xxxx.trycloudflare.com` — that's the live address.

> **Note:** `disown -a` detaches the process from the shell so it survives SSH disconnects. For production, pm2 or systemd would be the right choice — they add crash recovery and auto-restart on reboot — but this was sufficient within the time constraints.

### Restart after VM reboot

```bash
node server.js &
disown -a
cloudflared tunnel --url http://localhost:3000 &
disown -a
```

---

## Troubleshooting

### 502 Bad Gateway

502 always means `localhost:3000` isn't responding — Cloudflare itself is fine. The cause varies depending on when it happens:

**Immediate 502 on the tunnel URL**

The server isn't running at all. Either it failed to start, or the previous process was never cleaned up.

```bash
ps aux | grep node   # check if the process is running
node server.js &
disown -a
```

**Server dies when the SSH session closes**

Processes started in a shell session are children of that shell. When the session ends, the OS sends SIGHUP to all child processes, killing them. `disown -a` detaches the process from the shell so it keeps running after the SSH session closes.

```bash
node server.js &
disown -a
```

**502 after a few minutes (worked fine at first)**

An unhandled exception crashed the process. Adding global error handlers in `server.js` prevents unexpected errors from taking the server down.

```js
process.on('unhandledRejection', (reason) => {
  console.error('[unhandledRejection]', reason);
});
process.on('uncaughtException', (err) => {
  console.error('[uncaughtException]', err);
});
```

> For the SSH and crash cases, pm2 or systemd would be the proper fix — they survive SSH disconnects, auto-restart on crash, and persist across reboots. `disown -a` + error handlers was the pragmatic choice given the time constraints.

---

### Admin dashboard row numbers out of order

**Symptom:** The number column in the admin dashboard showed gaps — 1, 3, 5 instead of 1, 2, 3.

**Cause:** The column was rendering the database primary key (`id`) directly. Deleted rows leave gaps in the auto-increment sequence.

**Fix:** Use the JavaScript array index from the API response instead:
```js
// Before
users.map(u => `<td>${u.id}</td>`)

// After
users.map((u, i) => `<td>${i + 1}</td>`)
```
The backend query uses `ORDER BY id ASC` so the display order is always consistent with registration order.

---

## Reflection

**What worked well:**
- The middleware chain (`verifyToken → requireAdmin → checkOwnerOrAdmin`) made permission logic clean and reusable. Adding new protected routes was a one-liner.
- Tailwind CDN meant zero build config. Writing UI was fast.
- `ON DELETE CASCADE` on all foreign keys meant I never had to write cleanup queries.
- Wrapping every route in `try-catch` + adding global `unhandledRejection`/`uncaughtException` handlers meant SQL errors and unexpected failures never took the server down.

**What I'd improve with more time:**
- Tag-based filtering (the data is already there — just needs a `WHERE tag LIKE ?`)
- Answer count displayed on the question list cards
- pm2 or systemd for proper process management (crash recovery + reboot persistence)
- HTTPS on the origin, not just through the Cloudflare tunnel

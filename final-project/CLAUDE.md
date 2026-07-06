# CLAUDE.md — Final Project

Scope: These rules apply to the **final-project directory only**. Ignore them for the parent repo (mid-project, hackathon, etc.).

## Stack

- **Frontend**: Next.js (App Router, React)
- **Backend**: Spring Boot (Gradle)
- **Integration**: May communicate with the LLM/agent team's **FastAPI** service. Agree on the request/response schema before implementing any backend↔FastAPI interface.

## Git workflow — run automatically (details live in Skills)

- **Starting** a new feature/page/fix → run the **`feature-workflow`** skill (sync `main` + create a branch) *before* writing code. Never commit to `main` directly.
- **Finishing** a unit of work → run the **`ship-it`** skill (pre-commit checks → commit → push → PR to `main`).
- Commit/push/PR are pre-authorized — proceed without asking. But **confirm first** before force-push, branch deletion, or any direct manipulation of `main`.

## Coding rules

**General**
- Read the surrounding code first; match its conventions, naming, and structure. Don't introduce new patterns unprompted.
- Comment only to explain *why*. Skip comments for self-evident code.
- Never hardcode secrets/tokens/DB credentials. Use env vars (`.env`, `application.yml`) and verify `.gitignore`.

**Frontend (Next.js)**
- Default to Server Components; add `"use client"` only where interactivity is needed.
- Fetch data in server components / route handlers. Inject the backend base URL via env var.
- Split components by reuse, but don't over-abstract prematurely.

**Backend (Spring Boot)**
- Respect layering: `Controller → Service → Repository`. No business logic in controllers.
- Use DTOs for requests/responses; never expose entities directly in API responses.
- Build with `./gradlew`. JDK/Gradle are not on PATH — run with an explicit `JAVA_HOME` (OpenJDK 21).

**FastAPI integration (LLM team)**
- Agree on and document the communication schema before implementing.
- Every outbound call must include timeout, error handling, and retry policy.

## Token economy (how Claude should work here)

- Don't read whole files blindly — Read with offset/limit or narrow with Grep/Glob first.
- Don't re-read a file you just edited to verify (Edit errors out on failure).
- Delegate broad searches to Explore/Agent and take only the conclusion.
- Don't re-explain or re-litigate settled decisions. When info is sufficient, act.
- Be concise: recommend one option instead of listing many; don't echo command output verbatim.

## Run / build (update once directory layout is fixed)

- Frontend: `cd frontend && npm run dev`
- Backend: `./gradlew bootRun` (explicit `JAVA_HOME`)

---
name: feature-workflow
description: Start a new feature, page, or unit of work in final-project. Use at the START of any new feature/fix/refactor — syncs main and creates a properly-named branch BEFORE writing code, so work never lands on main directly.
---

# Feature workflow (start of a unit of work)

Run this the moment a new feature/page/fix begins — without being asked.

## 0. Safety check (monorepo!)
`final-project/` lives inside the parent repo — branch switches affect the whole repo.
- `git status` first. If there are uncommitted changes (anywhere in the repo), stop and ask the user whether to commit, stash, or abort — don't let changes ride along across a checkout.

## 1. Sync main
```
git checkout main && git pull origin main
```

## 2. Create a branch (never commit to `main`)
One branch per unit of work. Naming: `<type>/<kebab-desc>`
- `feat/login-page`, `fix/cart-total`, `refactor/api-client`
- types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`, `style`

```
git checkout -b <type>/<kebab-desc>
```

## 3. Then implement, following the coding rules in CLAUDE.md
- Read surrounding code first; match its conventions.
- Frontend: default to Server Components; `"use client"` only where needed.
- Backend: `Controller → Service → Repository`; DTOs for API, never expose entities.
- Never hardcode secrets; use env vars and check `.gitignore`.

When the work is done, use the `ship-it` skill to commit/push/PR.

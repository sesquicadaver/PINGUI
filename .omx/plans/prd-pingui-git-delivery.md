# PRD — PINGUI Git Delivery

## User Stories

### US-G01 — Pre-commit CI
Run `./scripts/ci_venv.sh` — must pass.

### US-G02 — Secret scan
Ensure no `.env`, credentials, tokens in staged files.

### US-G03 — Initial commit
Commit: src/, tests/, config/, docs/, scripts/, .github/, pyproject.toml, README, ROADMAP, .gitignore, systemd/, .omx/context, .omx/plans, .omx/reviews

Exclude: .venv, caches, .coverage, .omx/state, *.egg-info

### US-G04 — Post-commit review
Review `git show` diff — APPROVE + CLEAR.

## Test Spec

```bash
bash scripts/ci_venv.sh
git status
git diff --cached --stat
```

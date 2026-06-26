# PRD — Merge PR #1

1. Wait for PR CI checks pass
2. `gh pr merge 1 --merge --delete-branch` (or squash if preferred - use merge to preserve commits)
3. `git checkout main && git pull origin main`
4. Verify `./scripts/ci_venv.sh` on main

## Acceptance
- PR state: MERGED
- main contains MVP commits

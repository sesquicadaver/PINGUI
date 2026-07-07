> **Language:** English · [Українська](../CONTRIBUTING.md)

# Contributing to PINGUI

Thank you for your interest in the project. This document describes the process for making changes to the repository.

## Before Opening a PR

1. Run `./pingui.sh --deploy` in venv — all gates must pass.
2. Update [LIVING_SPEC.md](../LIVING_SPEC.md) when behavior or modules change.
3. Add or update tests for new logic.
4. Documentation: README or the relevant file in `docs/` and `docs/en/` (see [README.md](README.md)). When behavior changes, update **both** language versions.

## Branches

- `main` — stable branch; CI on push/PR.
- Feature branches: `feature/short-description`.

## Pull Request

Template: `.github/pull_request_template.md`

### Required Checklist

- [ ] `./pingui.sh --deploy` or `./scripts/ci_venv.sh` passes
- [ ] [LIVING_SPEC.md](../LIVING_SPEC.md) updated
- [ ] Anti-stub: no unjustified stubs in `src/pingui/`
- [ ] Manual QA (if UI or network changed) — checklist in README

### Anti-stub Review

Separate review item:

- No `pass` / `return None` / `Mock` in production unless temporarily marked with `TODO(#issue)`.
- Worker, tracer, store — real logic, not no-op.

## Commit Style

Short imperative subject in English (consistent with repo history):

```
Add host rename validation in session store

Fix probe timeout handling when TTL exceeds max hops.
```

## Code Review

Expectations:

- Minimal diff, no unrelated changes.
- Compliance with [DEVELOPMENT.md](DEVELOPMENT.md).
- Thread-safety for worker API.
- Injectable transport for testable ICMP.

## Bug Reports

Include:

- OS and Python version
- Output of `./pingui.sh --deploy` or `./scripts/check_caps.sh`
- Reproduction steps for GUI/network issues

## License

MIT — contributions under the same license.

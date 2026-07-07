> **Language:** [Ukrainian](../CONTRIBUTING.md) · English

# Contributing to PINGUI

Thank you for your interest in the project. This document describes the change process in the repository.

## Before PR

1. `./pingui.sh --deploy` in venv — all gates green.
2. Update [LIVING_SPEC.md](LIVING_SPEC.md) when behavior or modules change.
3. Add/update tests for new logic.
4. Documentation: README or the relevant file in `docs/` (see [README.md](README.md)).

## Branches

- `main` — stable branch; CI on push/PR.
- Feature branches: `feature/short-description`.

## Pull Request

Template: `.github/pull_request_template.md`

### Required checklist

- [ ] `./pingui.sh --deploy` or `./scripts/ci_venv.sh` passes
- [ ] [LIVING_SPEC.md](LIVING_SPEC.md) updated
- [ ] Anti-stub: no unjustified stubs in `src/pingui/`
- [ ] Manual QA (if UI or network changed) — checklist in README

### Anti-stub review

Separate review item:

- No `pass` / `return None` / `Mock` in production unless temporary with `TODO(#issue)`.
- Worker, tracer, store — real logic, not no-op.

## Commit style

Short imperative subject, in English (as in repo history):

```
Add host rename validation in session store

Fix probe timeout handling when TTL exceeds max hops.
```

## Code review

Expectations:

- Minimal diff, no unrelated changes.
- Compliance with [DEVELOPMENT.md](DEVELOPMENT.md).
- Thread-safety for worker API.
- Injectable transport for testable ICMP.

## Bug reports

Include:

- OS and Python version
- Output of `./pingui.sh --deploy` or `./scripts/check_caps.sh`
- Reproduction steps for GUI/network issues

## License

MIT — contributions under the same license.

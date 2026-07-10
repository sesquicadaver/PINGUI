> **Language:** English · [Українська](../ADR_DAEMON.md)

# ADR: Java headless daemon (P12-001)

**Date:** 2026-07-09  
**Status:** accepted  
**Branch (historical):** accepted on `beta`; shipped on `main` and `beta` after merge.

## Context

NOC profiles need route monitoring without JavaFX (server, systemd). Python has `daemon`/`stop` (PY-030); Java before P12 only launched GUI or `--export-report`.

## Decision

| Aspect | Policy |
|--------|--------|
| Process | `MonitorService` + `SessionStore` in foreground; main thread blocks until SIGTERM |
| CLI | `--daemon`, `--pid-file PATH`, `--stop`, `--status` |
| PID file | Write PID after start; remove in shutdown hook |
| Config | `--config` + YAML; CLI overrides as in GUI |
| SQLite | `--session-db` or YAML `persistence.session_db` |
| Alerts | `--alert-webhook`, `--desktop-alerts` (Linux) — as P10 |
| Reload | **SIGHUP → reload config** — planned P12-050 (not in P12-010) |

## Alternatives

1. **Separate JAR without JavaFX** — rejected: single artifact, conditional `main` branch.
2. **Fork + detach (double-fork)** — rejected: systemd `Type=simple` is enough; complicates logs.

## Consequences

- `PinguiApplication.main` branches: export / daemon / stop / status / GUI.
- `DaemonRunner` imports `ui.MonitorLifecycle` (allowed — not a lower layer).
- Documentation: `systemd/pingui-java.service.example`, DEPLOYMENT § Java daemon.

## Follow-ups

- P12-050: SIGHUP reload `ProfilesConfig` without process restart.
- PY parity: `python -m pingui daemon` remains a separate path.

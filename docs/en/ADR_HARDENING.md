> **Language:** English · [Українська](../ADR_HARDENING.md)

# ADR: Hardening post-audit (P26-009)

**Date:** 2026-09-03  
**Status:** accepted  
**Branch:** `beta` (after merge — `main` and `beta`)

## Context

Static audit of `main` @ `baff7cc` (PR #14) plus leftovers after P24/P25 on `beta` showed gaps in resilience / launchers / structural debt / coverage / latency baseline. GUI paint (buffer churn, coalesce, scene cache) is already closed in [ADR_GUI_PAINT.md](ADR_GUI_PAINT.md) — **do not** reopen. Phase 26 (`P26-001`…`008`) ships incremental fixes; this ADR records **policy** as SSOT.

A separate follow-up from audit `pimgui-5.md` lives in **phase 28** (queue after P27): `SinkRegistry` hang isolation, `inFlight` before pool, Python schema gate.

## Decision

### 1. Docs phase sync (P26-001)

| Rule | Details |
|------|---------|
| NEXT SSOT | Only [ROADMAP.md § NEXT](ROADMAP.md#next--single-source-of-truth) |
| README / JAVA | Must not say “phase N DONE” while NEXT is still in that phase |
| main vs beta | Role table reflects the **current** NEXT on `beta` |

### 2. Telemetry failure isolation (P26-002)

| Rule | Details |
|------|---------|
| Poll ≠ sink I/O | Sink fail/timeout **must not** block the poll loop |
| Call timeout | Per-sink call timeout (5s) in `SinkRegistry` |
| Backpressure | Non-blocking bus offers; drop + `failureCount` / metric |
| Shutdown | Documented flush; contract tests |

Bus details — [ADR_TELEMETRY.md](ADR_TELEMETRY.md) §7. Hang isolation with a bounded executor — **P28-001 ✅**.

### 3. SQLite reopen / corrupt (P26-003)

| Rule | Details |
|------|---------|
| Reopen | Append after reopen without breaking API contract |
| Legacy migrate | v1/v3→v4 within pre-P27 schema; corrupt/truncated → `PersistenceException` |
| Concurrent | Export smoke during append |

Record-format normalize schema v5–v7 (no legacy `.db` migrate) — **phase 27** (already `[x]` in the queue).

### 4. Launcher smoke (P26-004)

| Rule | Details |
|------|---------|
| Quoting | Paths with spaces — `scripts/smoke_launcher.*` + CI |
| Detach | Detached GUI / `--foreground`; Windows `PINGUI_JAVAW` |
| Fail path | Startup failure → `gui.log` |

### 5. Structural LOC gates (P26-005 / P26-006)

| Rule | Details |
|------|---------|
| `MainController` | Shell ≤ **550** LOC (`MainControllerLocGateTest`); dialogs/geometry/lifecycle → coordinators |
| `MonitorService` | Poll orchestration separate from post-poll effects (`PollResultEffects`, `TelemetryEmission`) |

### 6. Package JaCoCo (P26-007)

| Gate | Minimum |
|------|---------|
| `config` / `probe` / `monitor` | ≥ 0.85 |
| `telemetry` | ≥ 0.80 |
| `persistence` | ≥ 0.75 |
| BUNDLE | ≥ 0.80 |
| UI | explicit exclude `io/pingui/ui/**` (not a hidden gap) |

A single bundle threshold is **not** enough — package minima are required.

### 7. Latency baseline EWMA (P26-008)

| Rule | Details |
|------|---------|
| AVG | **EWMA** α=`0.2` (`AlertRuleEngine.LATENCY_EWMA_ALPHA`) |
| Anti-poison | Bad samples do not update the baseline |
| ETA UI | Help/Settings: ≈ `fail_after × interval` (`LatencyHighRuleConfig.approximateFiringEta`) |

Rule contract — [ADR_ALERT_RULES.md](ADR_ALERT_RULES.md) §7.

## Consequences

- Positive: poll more resilient to sink fail; launchers exercised; LOC/coverage gates in CI; latency AVG no longer unbounded mean.
- Negative: PING_ONLY `inFlight` still in P28-002; Python session schema parity lags Java v7.
- UI package coverage is deliberately excluded from PACKAGE minima — manual CHECKLIST remains required.

## Follow-ups (phase 28+)

| ID | Topic |
|----|-------|
| **P28-001** | ✅ `SinkRegistry` bounded executor + hang isolation |
| **P28-002** | Reserve `inFlight` before `probePool.execute` |
| **P28-003** | Python `session_db` reject `version != SCHEMA_VERSION` |

## References

- [ROADMAP.md](ROADMAP.md) — phases 26 / 28
- [CHECKLIST.md](CHECKLIST.md) — § Hardening smoke (P26-009)
- [LIVING_SPEC.md](LIVING_SPEC.md) — P26 → tests matrix
- [ADR_TELEMETRY.md](ADR_TELEMETRY.md), [ADR_ALERT_RULES.md](ADR_ALERT_RULES.md), [ADR_GUI_PAINT.md](ADR_GUI_PAINT.md)

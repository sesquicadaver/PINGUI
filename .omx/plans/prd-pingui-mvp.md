# PRD — PINGUI MVP

**Status:** Approved for Ralph execution  
**Source:** 3.txt, ROADMAP.md Sprint 1

## RALPLAN-DR Summary

### Principles
1. In-memory only — no disk persistence in MVP
2. Modular Python package under `src/pingui/`
3. Testable core (ICMP/tracer/store) isolated from Qt GUI
4. Linux raw ICMP with explicit capability check
5. CI gates merge: ruff, mypy, pytest

### Decision Drivers
1. GUI responsiveness — QThread worker
2. Traceroute reliability — scapy over manual raw sockets
3. Verifiability — contract tests with mock transport

### Options Considered
| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| scapy ICMP | Fast to implement, TTL handling | Extra dep, needs cap_net_raw | **Chosen** |
| stdlib raw socket | Fewer deps | Manual IP/ICMP assembly | Rejected |
| Folium geo-map | Rich viz | Out of MVP scope (3.txt) | Backlog |

## User Stories

### US-001 — Project bootstrap
**Acceptance:** pyproject.toml, venv scripts, README, LIVING_SPEC, CI workflow exist and work.

### US-002 — Route tracing
**Acceptance:** `trace_route()` returns hops with RTT; timeout as `*`; stops at target; mock contract tests pass.

### US-003 — Session store
**Acceptance:** RAM store per host; ping history trim 50; avg ping; route IP extraction.

### US-004 — Route change detection
**Acceptance:** Alert on path change; no alert on first observation; unit tests pass.

### US-005 — GUI
**Acceptance:** PyQt6 window with host list, log, NetworkX graph; worker signals; clean shutdown.

### US-006 — CLI
**Acceptance:** `--config`, `--interval`, `--max-hops`, `--timeout`, permission check on startup.

### US-007 — Quality gates
**Acceptance:** `./scripts/ci_venv.sh` green; coverage ≥80%; no cyclic imports.

## Test Spec

```bash
./scripts/ci_venv.sh
.venv/bin/python scripts/check_imports.py
QT_QPA_PLATFORM=offscreen .venv/bin/pytest tests/integration/test_ui_smoke.py
```

## ADR

**Decision:** PyQt6 + scapy + in-memory SessionStore + NetworkX topological graph  
**Consequences:** Linux-only runtime; no history between sessions  
**Follow-ups:** SQLite (B-01), ThreadPool worker (P4-08), geo-map (B-04)

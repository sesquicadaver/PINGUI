> **Language:** English · [Українська](../ARCHITECTURE.md)

# PINGUI Architecture

## Overview

PINGUI is a single-process desktop application: **PyQt6 GUI** + **background QThread worker** + **in-memory SessionStore**.
Network operations (ICMP) are isolated in the `icmp/` layer; polling business logic lives in `monitor/polling.py` without Qt dependencies.

```
┌─────────────────────────────────────────────────────────────┐
│  MainWindow (Qt GUI thread)                                 │
│  ├─ QListWidget (targets + checkboxes)                      │
│  ├─ GraphCanvas (Matplotlib)                                │
│  └─ SessionStore (RAM)                                      │
└───────────────▲─────────────────────────────────────────────┘
                │ pyqtSignal: data_received, route_changed,
                │             probe_error
┌───────────────┴─────────────────────────────────────────────┐
│  LightweightMonitorWorker (QThread)                         │
│  └─ poll_host_route → trace_route → send_probe (scapy)      │
└─────────────────────────────────────────────────────────────┘
```

## Layers

| Layer | Directory | Responsibility |
|-------|-----------|----------------|
| Entry | `__main__.py`, `ui/app.py` | CLI, bootstrap Qt |
| Config | `config.py` | YAML, host validation, DNS |
| Domain | `models.py` | HopNode, RouteSnapshot, HostSessionData |
| ICMP | `icmp/` | Raw ICMP, traceroute TTL 1..N |
| Monitor | `monitor/` | Polling, store, worker, route change |
| UI | `ui/` | MainWindow, GraphCanvas |

## Data flow (one worker cycle)

1. Worker collects **enabled** targets (`set_host_enabled`).
2. For each target: `poll_host_route(host, previous_ips)`.
3. `trace_route` sends ICMP with TTL=1..max_hops via `ScapyProbeTransport`.
4. Timeout hop → `HopNode.timeout()` (`ip="*"`).
5. `detect_route_change` compares IP chain with previous.
6. Signals:
   - `data_received(host, RouteSnapshot)` — always on success;
   - `route_changed(host, old_ips, new_ips)` — only on change;
   - `probe_error(host, message)` — on OSError / trace error.
7. GUI updates `SessionStore`, redraws graph for active host.

## SessionStore

Per host:

- `current_route` — latest trace;
- `previous_route` — previous chain on IP sequence change;
- `last_known_by_hop` — last known IP per hop (for gray chain);
- `ping_history[ip]` — up to 50 RTT samples per IP;
- `enabled` — active tracing flag.

## GraphCanvas

- Vertical layout: “Your PC” → hop 1 → … → target.
- Two columns: **left** — `inactive_route()` (gray), **right** — `current_route`.
- Node color by average RTT (`ping_color`: green / yellow / red / gray).

## Thread safety

- `LightweightMonitorWorker` — mutex (`threading.Lock`) on host lists, enabled, last_routes.
- `SessionStore` — GUI thread only (updates via Qt signals/slots).
- Coverage: `concurrency = ["thread"]` in pytest-cov.

## Package dependencies

```
config ← models
icmp ← config
monitor ← config, icmp, models
ui ← config, models, monitor
__main__ ← config, icmp, ui, logging_setup
```

Circular imports forbidden; check: `scripts/check_imports.py`.

## ADR: Scapy for ICMP

`icmp/raw_socket.py` uses **scapy** instead of manual IP/ICMP header assembly:
more reliable parsing of TTL-exceeded / echo reply on Linux. Requires `CAP_NET_RAW` or root.

## Out of MVP scope

- No persistence between sessions by default (RAM-only MVP).
- Optional: `--session-db PATH` — Python `persistence/session_db.py`; Java `io.pingui.persistence` (P11-011…012).
- No separate backend/API server.
- IPv6 literals in YAML (RFC 5952); subprocess `traceroute -6` for v6 trace; raw ICMP remains v4-only.

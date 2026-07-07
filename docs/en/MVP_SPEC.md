> **Language:** [Ukrainian](../MVP_SPEC.md) · English

# MVP — PINGUI technical specification

Brief requirements summary for the current release (in-memory desktop monitor).

## Goal

Linux desktop app (PyQt6): monitor up to **10 targets** in a list, ICMP traceroute-like tracing,
RTT per hop, route change detection, topological visualization. Data **RAM-only** for the session.

## Functional requirements

| ID | Requirement | Implementation |
|----|-------------|----------------|
| F-01 | 0–10 targets in YAML config | `config.py`, `config/hosts.example.yaml` |
| F-02 | Edit list in GUI (add / change / delete / save) | `ui/main_window.py` |
| F-03 | Trace only for targets with checkbox enabled | `monitor/worker.py` |
| F-04 | No more than 10 active traces at once | `worker.set_host_enabled`, `config.MAX_HOSTS` |
| F-05 | Vertical route graph (top to bottom) | `ui/graph_canvas.py` |
| F-06 | Previous route — gray column left; current — right | `graph_canvas`, `session_store.inactive_route` |
| F-07 | Last known IP for timeout hops | `monitor/route_history.py` |
| F-08 | Route change and probe error log | `main_window._on_route_changed`, `_on_probe_error` |
| F-09 | Raw ICMP (CAP_NET_RAW or root) | `icmp/raw_socket.py`, `pingui.sh --deploy` |
| F-10 | CLI: `--config`, `--interval`, `--max-hops`, `--timeout`, `--verbose` | `__main__.py` |

## Non-functional requirements

- Python ≥ 3.11, venv `--copies` for `setcap`
- CI: ruff, mypy, pytest, coverage ≥ 80% (`pyproject.toml`, `scripts/ci_venv.sh`)
- Tests only in venv; network tests marked `@pytest.mark.network`
- No stubs in production paths (anti-stub review in PR)

## Out of MVP (backlog)

See [ROADMAP.md](../ROADMAP.md) — SQLite, geo-map, export, jitter/loss, etc.

## Requirements traceability

Full matrix «requirement → module → tests»: [LIVING_SPEC.md](LIVING_SPEC.md).

Documentation index: [README.md](README.md).

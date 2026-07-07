> **Language:** [Ukrainian](ROADMAP.md) · English

# ROADMAP — PINGUI

**MVP status:** ✅ implemented (2026-06-26)

- Launch: `./pingui.sh` / `./pingui.sh --deploy`
- CI: ruff + mypy + pytest, coverage ≥ 80%
- Documentation: `README.md`, `docs/MVP_SPEC.md`, `docs/LIVING_SPEC.md`

---

## MVP goal

Linux desktop app: monitor up to 10 targets in a list, ICMP traceroute, RTT per hop, route change detection, topological map in GUI, data in RAM only for the session, edit target list in GUI.

---

## Repository structure (current)

```
PINGUI/
├── pingui.sh
├── java/                     # cross-platform Java edition
│   ├── pingui-java.sh
│   ├── build.gradle.kts
│   └── src/main/java/io/pingui/
├── pyproject.toml
├── README.md
├── ROADMAP.md
├── docs/
│   ├── README.md           # documentation index
│   ├── en/                 # English docs
│   ├── USER_GUIDE.md
│   ├── ARCHITECTURE.md
│   ├── DEPLOYMENT.md
│   ├── DEVELOPMENT.md
│   ├── TESTING.md
│   ├── MODULES.md
│   ├── CONFIGURATION.md
│   ├── CONTRIBUTING.md
│   ├── MVP_SPEC.md
│   └── LIVING_SPEC.md
├── config/
│   └── hosts.example.yaml
├── src/pingui/
│   ├── __init__.py
│   ├── __main__.py
│   ├── config.py
│   ├── models.py
│   ├── logging_setup.py
│   ├── icmp/
│   │   ├── raw_socket.py
│   │   └── tracer.py
│   ├── monitor/
│   │   ├── session_store.py
│   │   ├── route_history.py
│   │   ├── route_change.py
│   │   ├── polling.py
│   │   └── worker.py
│   └── ui/
│       ├── app.py
│       ├── main_window.py
│       └── graph_canvas.py
├── tests/
│   ├── conftest.py
│   ├── unit/
│   ├── contract/
│   └── integration/
├── scripts/
│   ├── ci_venv.sh
│   ├── check_caps.sh
│   ├── check_imports.py
│   └── setup_caps.sh
└── systemd/
    └── pingui-dev.service.example
```

---

## Phases (status)

| Phase | Description | Status |
|-------|-------------|--------|
| P0 | Project init, venv, CI | ✅ |
| P1 | Models and config | ✅ |
| P2 | ICMP / traceroute | ✅ |
| P3 | In-memory session store | ✅ |
| P4 | Background worker (QThread) | ✅ |
| P5 | GUI (PyQt6 + graph) | ✅ |
| P6 | Integration, CLI, logging | ✅ |
| P7 | CI, anti-stub, coverage ≥ 80% | ✅ |
| P8 | Linux deployment helpers | ✅ |
| **P9** | **Java cross-platform edition** | **✅ MVP** |

---

## Phase P9 — Java (cross-platform)

| ID | Task | Status |
|----|------|--------|
| J-P9-01 | Gradle + JavaFX scaffold (`java/`) | done |
| J-P9-02 | Models, config, monitor port | done |
| J-P9-03 | ProcessRouteProbe (traceroute/tracert) | done |
| J-P9-04 | JavaFX GUI + pingui-java.sh | done |
| J-P9-05 | JUnit tests + java-ci.yml | done |
| J-P9-06 | JavaFX graph parity | done |

## Backlog (post-MVP)

| ID | Task |
|----|------|
| B-01 | SQLite persistence between sessions | ✅ Python `--session-db` |
| B-02 | CSV/HTML report export | ✅ |
| B-03 | GeoIP (rough country) in node labels | ✅ |
| B-04 | Folium geo-map in separate view | ✅ |
| B-05 | TimescaleDB/InfluxDB backend | ✅ |
| B-06 | Jitter/loss statistics per hop | ✅ |
| **J-01** | **Java: JavaFX topological graph** | ✅ |
| **J-02** | **Java: jpackage installers** | ✅ Linux .deb |
| **J-04** | **Java: launcher `pingui-java.sh` / `pingui-java.bat`** | ✅ |
| **J-03** | **Java: optional raw ICMP (JNA)** | ✅ Linux |
| **J-05** | **Java: CI matrix + jpackage msi/dmg** | ✅ |
| **J-06** | **Java: hop jitter/loss in graph labels** | ✅ |

---

## Definition of Done (per feature)

1. Code in `src/pingui/`, no stubs in production paths.
2. Unit/contract/integration test where there is logic.
3. `./pingui.sh --deploy` passes in venv.
4. Row updated in `docs/LIVING_SPEC.md`.
5. If launch changes — update `README.md` or service file.

---

## Critical path (MVP — complete)

```
pingui.sh → config/models → icmp/tracer → session_store → worker → main_window/graph → CI
```

For Java fix plan and IPv6 phase 9, see [docs/en/ROADMAP.md](docs/en/ROADMAP.md).

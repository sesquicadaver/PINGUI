> **Language:** English · [Українська](../DEVELOPMENT.md)

# PINGUI Development

## Prerequisites

- Linux, Python ≥ 3.11
- Git
- sudo (for setcap on first deploy)

## Getting Started

```bash
git clone https://github.com/sesquicadaver/PINGUI.git
cd PINGUI
chmod +x pingui.sh
./pingui.sh --deploy
./pingui.sh --verbose   # GUI with debug log
```

### Dependencies (PY-060)

| Extra | Install | Purpose |
|-------|---------|---------|
| *(base)* | `pip install -e .` | Headless monitor/daemon/export (scapy, PyYAML, networkx) |
| `gui` | `pip install -e ".[gui]"` | PyQt6 GUI, folium map, matplotlib |
| `dev` | `pip install -e ".[dev,gui]"` | ruff, mypy, pytest (same as CI) |

`./pingui.sh --deploy` installs `.[dev,gui]`.

## Code Structure

```
src/pingui/
├── __main__.py          # CLI entry
├── config.py            # YAML + validation
├── models.py            # dataclasses
├── logging_setup.py
├── icmp/                # scapy probes, traceroute
├── monitor/             # polling, store, worker
└── ui/                  # PyQt6 + matplotlib
```

Tests mirror the domain:

```
tests/
├── unit/                # pure logic, mocks
├── contract/            # module boundaries
├── integration/         # Qt thread, UI smoke
└── conftest.py          # offscreen Qt, Agg backend
```

## Local Loop

```bash
source .venv/bin/activate

# Quick check
ruff check src tests
mypy src/pingui
pytest tests -m "not network" -q

# Full CI (same as GitHub Actions)
./scripts/ci_venv.sh
python scripts/check_imports.py
python scripts/check_doc_parity.py
```

## Code Standards

| Tool | Config | Rules |
|------|--------|-------|
| **ruff** | `pyproject.toml` | E, F, I, UP, B, SIM; line-length 100 |
| **mypy** | strict on `pingui` | scapy/matplotlib — ignore_missing_imports |
| **pytest** | `testpaths = tests` | coverage ≥ 80% |

### Conventions

- Dataclasses for domain models (`frozen` where possible).
- Qt signals only from worker → GUI (not the reverse for data).
- Injectable `ProbeTransport` for tests without network access.
- Config errors — `ConfigError`; ICMP permissions — `RawIcmpPermissionError`.
- Docstrings on public classes/functions.
- Comments only for non-obvious logic (do not duplicate code).

### Anti-stub

The following are forbidden without justification in `src/pingui/`:

- `pass` in production functions;
- `return None` as a stub;
- `Mock` outside `tests/`.

Temporary stubs must include an explicit `TODO(issue)`.

## Adding a Feature

1. Update [MVP_SPEC.md](MVP_SPEC.md) or the backlog in [ROADMAP.md](../../ROADMAP.md).
2. Implement the module; avoid cyclic imports.
3. Add unit/contract/integration tests.
4. Update [LIVING_SPEC.md](../LIVING_SPEC.md).
5. Run `./pingui.sh --deploy` in venv.
6. Open a PR per [CONTRIBUTING.md](CONTRIBUTING.md).

## Useful Commands

```bash
# Single test
pytest tests/unit/test_worker.py -v

# UI smoke
pytest tests/integration/test_ui_smoke.py -v

# Network tests (requires cap_net_raw)
pytest tests/integration/test_tracer_network.py -m network -v

# Clean up artifacts
./pingui.sh --destroy
```

## IDE

Recommended interpreter: `.venv/bin/python`.
Mypy path: `src` (see `pyproject.toml`).

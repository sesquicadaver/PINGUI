> **Language:** [Ukrainian](../DEVELOPMENT.md) · English

# PINGUI development

## Prerequisites

- Linux, Python ≥ 3.11
- Git
- sudo (for setcap on first deploy)

## Getting started

```bash
git clone https://github.com/sesquicadaver/PINGUI.git
cd PINGUI
chmod +x pingui.sh
./pingui.sh --deploy
./pingui.sh --verbose   # GUI with debug log
```

## Code structure

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

## Local cycle

```bash
source .venv/bin/activate

# Quick check
ruff check src tests
mypy src/pingui
pytest tests -m "not network" -q

# Full CI (as in GitHub Actions)
./scripts/ci_venv.sh
python scripts/check_imports.py
```

## Code standards

| Tool | Config | Rules |
|------|--------|-------|
| **ruff** | `pyproject.toml` | E, F, I, UP, B, SIM; line-length 100 |
| **mypy** | strict on `pingui` | scapy/matplotlib — ignore_missing_imports |
| **pytest** | `testpaths = tests` | coverage ≥ 80% |

### Conventions

- Dataclasses for domain models (`frozen` where possible).
- Qt signals only from worker → GUI (not the reverse for data).
- Injectable `ProbeTransport` for tests without network.
- Config errors — `ConfigError`; ICMP permissions — `RawIcmpPermissionError`.
- Docstrings on public classes/functions.
- Comments only for non-obvious logic (do not duplicate code).

### Anti-stub

In `src/pingui/` the following are forbidden without justification:

- `pass` in production functions;
- `return None` as a stub;
- `Mock` outside `tests/`.

Temporary stubs — with explicit `TODO(issue)`.

## Adding a feature

1. Update [MVP_SPEC.md](MVP_SPEC.md) or backlog in [ROADMAP.md](../ROADMAP.md).
2. Implement module; avoid cyclic imports.
3. Add unit/contract/integration tests.
4. Update [LIVING_SPEC.md](LIVING_SPEC.md).
5. `./pingui.sh --deploy` in venv.
6. PR per [CONTRIBUTING.md](CONTRIBUTING.md).

## Useful commands

```bash
# Single test
pytest tests/unit/test_worker.py -v

# UI smoke
pytest tests/integration/test_ui_smoke.py -v

# Network (requires cap_net_raw)
pytest tests/integration/test_tracer_network.py -m network -v

# Clean artifacts
./pingui.sh --destroy
```

## IDE

Recommended interpreter: `.venv/bin/python`.
Mypy path: `src` (see `pyproject.toml`).

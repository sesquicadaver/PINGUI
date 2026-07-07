> **Language:** [Ukrainian](../TESTING.md) · English

# PINGUI testing

## Strategy

| Level | Directory | Purpose |
|-------|-----------|---------|
| Unit | `tests/unit/` | Pure logic, mocks, no Qt event loop |
| Contract | `tests/contract/` | Module boundaries (tracer, worker→store) |
| Integration | `tests/integration/` | QThread, MainWindow offscreen, network |

## Running tests

```bash
# Standard (no live network)
pytest tests -m "not network" -q

# With coverage (as in CI)
pytest tests -m "not network" --cov=pingui --cov-fail-under=80

# Full deploy gate
./pingui.sh --deploy
```

## pytest markers

```python
@pytest.mark.network
```

Tests with this marker require:

- live network;
- `CAP_NET_RAW` or root.

CI **skips** them: `-m "not network"`.

## Headless Qt

`tests/conftest.py` (session autouse):

```python
QT_QPA_PLATFORM=offscreen
MPLBACKEND=Agg
```

GUI integration tests create `QApplication` before `MainWindow`.

## Coverage

| Parameter | Value |
|-----------|-------|
| `fail_under` | 80% |
| `concurrency` | `thread` (worker in QThread) |
| Omit | `__main__.py`, `ui/app.py`, `logging_setup.py` |

Approximate coverage (after MVP): **~90%**.

Lowest: `worker.run()` (background loop) — partially covered by `test_worker_run.py`.

## Key test files

| File | What it checks |
|------|----------------|
| `test_config.py` | YAML load/save, validation |
| `test_polling.py` | poll_host_route, route change |
| `test_worker.py` | add/rename/remove, enabled |
| `test_worker_run.py` | Qt signals from worker |
| `test_session_store.py` | routes, ping history, inactive |
| `test_graph_canvas.py` | layout, ping_color |
| `test_ui_smoke.py` | MainWindow CRUD, logs, checkbox |
| `test_tracer.py` (contract) | trace_route with mock transport |
| `test_worker_store.py` (contract) | worker → store flow |

## Java edition (`java/`)

```bash
cd java && ./gradlew test jacocoTestReport jacocoTestCoverageVerification
cd java && ./pingui-java.sh --package   # Linux .deb (locally)
```

JaCoCo gate ≥80% instruction coverage (JavaFX UI and `ProcessRouteProbe.trace` excluded — parser/unit tested separately).

CI: `.github/workflows/java-ci.yml`.

## Import graph

```bash
python scripts/check_imports.py
# OK: no cyclic imports in pingui
```

Runs in `--deploy` and CI.

## Manual QA

Checklist in [../README.md](../README.md#manual-qa-чекліст) and [USER_GUIDE.md](USER_GUIDE.md).

## Adding tests

1. Unit — for functions without Qt (mostly `monitor/`, `config/`).
2. Mock `ProbeTransport` — for ICMP without cap.
3. Integration — only when event loop or MainWindow needed.
4. `@pytest.mark.network` — only for real ICMP.
5. Update [LIVING_SPEC.md](LIVING_SPEC.md).

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Segfault in GUI test | Ensure `QApplication` created before `MainWindow` |
| Coverage < 80% | `pytest --cov=pingui --cov-report=term-missing` |
| Flaky worker test | Increase loop timeout; `app.processEvents()` |

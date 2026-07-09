> **Language:** English · [Українська](../TESTING.md)

# PINGUI Testing

## Strategy

| Level | Directory | Purpose |
|-------|-----------|---------|
| Unit | `tests/unit/` | Pure logic, mocks, no Qt event loop |
| Contract | `tests/contract/` | Module boundaries (tracer, worker→store) |
| Integration | `tests/integration/` | QThread, MainWindow offscreen, network |

## Running Tests

```bash
# Standard (no live network)
pytest tests -m "not network" -q

# With coverage (same as CI)
pytest tests -m "not network" --cov=pingui --cov-fail-under=80

# Full deploy gate
./pingui.sh --deploy
```

## pytest Markers

```python
@pytest.mark.network
```

Tests with this marker require:

- a live network;
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

Approximate coverage (post-MVP): **~90%**.

Lowest: `worker.run()` (background loop) — partially covered by `test_worker_run.py`.

## Key Test Files

| File | What it verifies |
|------|------------------|
| `test_config.py` | YAML load/save, validation, IPv6 RFC 5952 |
| `test_process_tracer.py` | `traceroute -6` argv, v6 output parser |
| `test_polling.py` | poll_host_route, route change |
| `test_worker.py` | add/rename/remove, enabled |
| `test_worker_run.py` | Qt signals from worker |
| `test_session_store.py` | routes, ping history, inactive |
| `test_graph_canvas.py` | layout, ping_color |
| `test_ui_smoke.py` | MainWindow CRUD, logs, checkbox |
| `test_tracer.py` (contract) | trace_route with mock transport |
| `test_tracer_network.py` (integration) | live v4/v6 trace (`@pytest.mark.network`) |
| `test_worker_store.py` (contract) | worker → store flow |
| `test_session_db.py` | SQLite round-trip, schema v2 |
| `test_session_export.py` | CSV/HTML row builder |
| `test_main_export.py` | CLI `--export-csv` / `--export-html` headless |
| `test_main_subcommands.py` | CLI `monitor` / `daemon` / `export` / `stop` |
| `test_main_dispatch.py` | `__main__` parse/dispatch edge cases (PY-064) |
| `test_monitor_loop.py` | Headless loop, callbacks, store integration |
| `test_daemon_runner.py` | PID file, `run_headless_monitor`, stop/status |
| `test_route_change_event.py` | `RouteChangeEvent` JSON round-trip |
| `test_alert_rate_limiter.py` | Per-host hourly burst limit |
| `test_desktop_notifier.py` | `notify-send` integration |
| `test_alert_webhook.py` (contract) | Webhook POST JSON payload |
| `test_timeseries.py` | influx/timescale factory, memory backend |
| `test_geoip_country.py` | CIDR longest-prefix, LAN, `prefixes_v6` |
| `test_geo_map.py` | folium map builder |
| `test_hop_stats.py` | jitter/loss summary |
| `test_doc_parity.py` | UK/EN banner parity |
| `test_pingui_sh.py` | `pingui.sh` syntax + passthrough args |

## Java edition (`java/`)

```bash
cd java && ./gradlew test jacocoTestReport jacocoTestCoverageVerification
cd java && ./pingui-java.sh --package   # Linux .deb (local)
```

JaCoCo gate ≥80% instruction coverage (JavaFX UI and `ProcessRouteProbe.trace` excluded — tested via parser/unit separately).

CI: `.github/workflows/java-ci.yml`.

## Import Graph

```bash
python scripts/check_imports.py
# OK: no cyclic imports in pingui
```

Runs in `--deploy` and CI.

## Documentation parity (UK/EN)

```bash
python scripts/check_doc_parity.py
# OK: UK/EN documentation parity
```

Checks:

- pairing of `docs/*.md` ↔ `docs/en/*.md`;
- language switchers in README, `java/README`, root `ROADMAP`;
- UK banner in `CHANGELOG.md` linking to `docs/en/`.

Runs in `./pingui.sh --deploy`, `./scripts/ci_venv.sh`, and Python CI.

## Manual QA

Checklist in [USER_GUIDE.md](USER_GUIDE.md) and [../../README.md](../../README.md).

## Adding Tests

1. Unit — for functions without Qt (mostly `monitor/`, `config/`).
2. Mock `ProbeTransport` — for ICMP without cap.
3. Integration — only when an event loop or MainWindow is required.
4. `@pytest.mark.network` — only for real ICMP.
5. Update [LIVING_SPEC.md](../LIVING_SPEC.md).

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Segfault in GUI test | Ensure `QApplication` is created before `MainWindow` |
| Coverage < 80% | `pytest --cov=pingui --cov-report=term-missing` |
| Flaky worker test | Increase loop timeout; call `app.processEvents()` |

> **Мова:** Українська · [English](en/TESTING.md)

# Тестування PINGUI

## Стратегія

| Рівень | Каталог | Призначення |
|--------|---------|-------------|
| Unit | `tests/unit/` | Чиста логіка, mocks, без Qt event loop |
| Contract | `tests/contract/` | Межі модулів (tracer, worker→store) |
| Integration | `tests/integration/` | QThread, MainWindow offscreen, мережа |

## Запуск

```bash
# Стандарт (без live network)
pytest tests -m "not network" -q

# З coverage (як у CI)
pytest tests -m "not network" --cov=pingui --cov-fail-under=80

# Повний deploy gate
./pingui.sh --deploy
```

## Маркери pytest

```python
@pytest.mark.network
```

Тести з цим маркером потребують:

- live мережу;
- `CAP_NET_RAW` або root.

CI їх **пропускає**: `-m "not network"`.

## Headless Qt

`tests/conftest.py` (session autouse):

```python
QT_QPA_PLATFORM=offscreen
MPLBACKEND=Agg
```

Integration-тести GUI створюють `QApplication` перед `MainWindow`.

## Coverage

| Параметр | Значення |
|----------|----------|
| `fail_under` | 80% |
| `concurrency` | `thread` (worker у QThread) |
| Omit | `__main__.py`, `ui/app.py`, `logging_setup.py` |

Орієнтовне покриття (після MVP): **~90%**.

Найнижче: `worker.run()` (фоновий цикл) — частково покритий `test_worker_run.py`.

## Ключові тест-файли

| Файл | Що перевіряє |
|------|--------------|
| `test_config.py` | YAML load/save, validation, IPv6 RFC 5952 |
| `test_process_tracer.py` | `traceroute -6` argv, v6 output parser |
| `test_polling.py` | poll_host_route, route change |
| `test_worker.py` | add/rename/remove, enabled |
| `test_worker_run.py` | Qt signals від worker |
| `test_session_store.py` | routes, ping history, inactive |
| `test_graph_canvas.py` | layout, ping_color |
| `test_ui_smoke.py` | MainWindow CRUD, logs, checkbox |
| `test_tracer.py` (contract) | trace_route з mock transport |
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
cd java && ./pingui-java.sh --package   # Linux .deb (локально)
```

JaCoCo gate ≥80% instruction coverage (JavaFX UI та `ProcessRouteProbe.trace` виключені — тестуються parser/unit окремо; IPv6 `HopDisplay`/`GeoCountry` у bundle).

Фікстури trace: `java/src/test/resources/trace/` — v4 regression: `ProcessRouteProbeTest.v4FixturesRemainGreen`.

LOG sink contract (P16-072): `SyslogGelfContractTest` + `TelemetryLogFieldFixture` — mock TCP syslog/GELF, спільні поля event.

CI: `.github/workflows/java-ci.yml`.

## Import graph

```bash
python scripts/check_imports.py
# OK: no cyclic imports in pingui
```

Виконується в `--deploy` і CI.

## Documentation parity (UK/EN)

```bash
python scripts/check_doc_parity.py
# OK: UK/EN documentation parity
```

Перевіряє:

- парність `docs/*.md` ↔ `docs/en/*.md`;
- перемикачі мов у README, `java/README`, root `ROADMAP`;
- наявність UK-банера в `CHANGELOG.md` з посиланням на `docs/en/`.

Виконується в `./pingui.sh --deploy`, `./scripts/ci_venv.sh` і Python CI.

## Manual QA

Чекліст у [../README.md](../README.md#manual-qa-чекліст) та [USER_GUIDE.md](USER_GUIDE.md).

## Додавання тестів

1. Unit — для функцій без Qt (переважно `monitor/`, `config/`).
2. Mock `ProbeTransport` — для ICMP без cap.
3. Integration — лише коли потрібен event loop або MainWindow.
4. `@pytest.mark.network` — лише для реального ICMP.
5. Оновити [LIVING_SPEC.md](LIVING_SPEC.md).

## Troubleshooting

| Проблема | Рішення |
|----------|---------|
| Segfault у GUI test | Переконатися, що `QApplication` створено до `MainWindow` |
| Coverage < 80% | `pytest --cov=pingui --cov-report=term-missing` |
| Flaky worker test | Збільшити timeout loop; `app.processEvents()` |

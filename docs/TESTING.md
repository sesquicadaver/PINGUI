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
| `test_config.py` | YAML load/save, validation |
| `test_polling.py` | poll_host_route, route change |
| `test_worker.py` | add/rename/remove, enabled |
| `test_worker_run.py` | Qt signals від worker |
| `test_session_store.py` | routes, ping history, inactive |
| `test_graph_canvas.py` | layout, ping_color |
| `test_ui_smoke.py` | MainWindow CRUD, logs, checkbox |
| `test_tracer.py` (contract) | trace_route з mock transport |
| `test_worker_store.py` (contract) | worker → store flow |

## Java edition (`java/`)

```bash
cd java && ./gradlew test jacocoTestReport jacocoTestCoverageVerification
cd java && ./pingui-java.sh --package   # Linux .deb (локально)
```

JaCoCo gate ≥80% instruction coverage (JavaFX UI та `ProcessRouteProbe.trace` виключені — тестуються parser/unit окремо).

CI: `.github/workflows/java-ci.yml`.

## Import graph

```bash
python scripts/check_imports.py
# OK: no cyclic imports in pingui
```

Виконується в `--deploy` і CI.

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

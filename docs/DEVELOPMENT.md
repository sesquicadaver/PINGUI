> **Мова:** Українська · [English](en/DEVELOPMENT.md)

# Розробка PINGUI

## Передумови

- Linux, Python ≥ 3.11
- Git
- sudo (для setcap при першому deploy)

## Початок роботи

```bash
git clone https://github.com/sesquicadaver/PINGUI.git
cd PINGUI
chmod +x pingui.sh
./pingui.sh --deploy
./pingui.sh --verbose   # GUI з debug-логом
```

## Структура коду

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

Тести дзеркалять домен:

```
tests/
├── unit/                # pure logic, mocks
├── contract/            # module boundaries
├── integration/         # Qt thread, UI smoke
└── conftest.py          # offscreen Qt, Agg backend
```

## Локальний цикл

```bash
source .venv/bin/activate

# Швидка перевірка
ruff check src tests
mypy src/pingui
pytest tests -m "not network" -q

# Повний CI (як у GitHub Actions)
./scripts/ci_venv.sh
python scripts/check_imports.py
```

## Стандарти коду

| Інструмент | Конфіг | Правила |
|------------|--------|---------|
| **ruff** | `pyproject.toml` | E, F, I, UP, B, SIM; line-length 100 |
| **mypy** | strict на `pingui` | scapy/matplotlib — ignore_missing_imports |
| **pytest** | `testpaths = tests` | coverage ≥ 80% |

### Конвенції

- Dataclasses для доменних моделей (`frozen` де можливо).
- Qt-сигнали лише з worker → GUI (не навпаки для даних).
- Injectable `ProbeTransport` для тестів без мережі.
- Помилки конфігу — `ConfigError`; ICMP прав — `RawIcmpPermissionError`.
- Docstrings на публічних класах/функціях.
- Коментарі лише для неочевидної логіки (не дублювати код).

### Anti-stub

У `src/pingui/` заборонені необґрунтовані:

- `pass` у production-функціях;
- `return None` як заглушка;
- `Mock` поза `tests/`.

Тимчасові заглушки — з явним `TODO(issue)`.

## Додавання фічі

1. Оновити [MVP_SPEC.md](MVP_SPEC.md) або backlog у [ROADMAP.md](../ROADMAP.md).
2. Реалізувати модуль; уникати циклічних імпортів.
3. Додати unit/contract/integration тести.
4. Оновити [LIVING_SPEC.md](LIVING_SPEC.md).
5. `./pingui.sh --deploy` у venv.
6. PR за [CONTRIBUTING.md](CONTRIBUTING.md).

## Корисні команди

```bash
# Один тест
pytest tests/unit/test_worker.py -v

# UI smoke
pytest tests/integration/test_ui_smoke.py -v

# Мережеві (потрібен cap_net_raw)
pytest tests/integration/test_tracer_network.py -m network -v

# Очистка артефактів
./pingui.sh --destroy
```

## IDE

Рекомендовано вказати інтерпретатор: `.venv/bin/python`.
Mypy path: `src` (див. `pyproject.toml`).

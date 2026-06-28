# PINGUI

Крос-платформний монітор маршрутів і RTT до 10 цілей одночасно.
Дані зберігаються **лише в RAM** протягом сесії.

| Гілка | Зміст |
|-------|--------|
| **`main`** | Java-редакція + документація для запуску (без тестів) |
| **`beta`** | Повний репозиторій: Python, Java, тести, CI, специфікації |

| Редакція | Платформа | Запуск | Особливості |
|----------|-----------|--------|-------------|
| **Python** | Linux | `./pingui.sh` | PyQt6, Matplotlib-граф, scapy raw ICMP |
| **Java** | Linux, macOS, Windows | `java/pingui-java.sh` (Unix) / `java/pingui-java.bat` (Windows) | JavaFX, traceroute/tracert |

## Python (Linux) — швидкий старт

```bash
chmod +x pingui.sh
./pingui.sh --deploy    # перше розгортання: venv, cap_net_raw, CI
./pingui.sh             # GUI
```

| Команда | Опис |
|---------|------|
| `./pingui.sh` | Запуск GUI (тиха підготовка venv/cap, якщо потрібно) |
| `./pingui.sh --deploy` | Повне розгортання + ruff, mypy, pytest (coverage ≥ 80%) |
| `./pingui.sh --destroy` | Видалити `.venv` та локальні кеші |
| `./pingui.sh --help` | Довідка |

Опції лише з `--deploy`: `--skip-tests`, `--force-venv`.

## Java (cross-platform) — швидкий старт

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh
./pingui-java.sh --build
./pingui-java.sh
```

**Windows**

```bat
cd java
pingui-java.bat --build
pingui-java.bat
```

Потрібні: **JDK 21+**, `traceroute` (Linux/macOS) або `tracert` (Windows).

Документація: [java/README.md](java/README.md), [docs/JAVA.md](docs/JAVA.md).

## Python — вимоги

- Linux (raw ICMP)
- Python ≥ 3.11
- Права `CAP_NET_RAW` або root для ICMP

Опційно: `pip install 'pingui[timeseries]'` для InfluxDB/Timescale backends.

## Python — GUI

- **Додати / Змінити / Видалити / Зберегти** — редагування списку цілей (до 10 у списку).
- **Чекбокс** — увімкнути трасування для цілі (не більше 10 активних одночасно).
- Граф: згори вниз; зліва — попередній маршрут (сірий), справа — поточний.
- Вкладка **«Карта»** — Folium geo-map hop-ів (country centroids, polyline маршруту).
- Неактивний ланцюг показує **останні відомі IP** hop-ів (навіть після таймауту в trace).
- Підпис hop-ноди може містити **грубу країну** (offline GeoIP hints, напр. `US`, `LAN`) та **jitter/loss** (`j:N loss:M%`).
- Список зберігається у YAML (`config/hosts.example.yaml` за замовчуванням).

## CLI

```bash
.venv/bin/python -m pingui --help
.venv/bin/python -m pingui --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Параметр | Опис |
|----------|------|
| `--config` | YAML зі списком 0–10 хостів |
| `--interval` | Пауза між циклами опитування (с) |
| `--max-hops` | Максимум TTL |
| `--timeout` | Таймаут probe (с) |
| `--session-db` | SQLite для збереження маршрутів/ping між сесіями (опційно) |
| `--export-csv` | Експорт звіту CSV і вихід (без GUI) |
| `--export-html` | Експорт звіту HTML і вихід (без GUI) |
| `--geoip-hints` | YAML з CIDR→країна для підписів hop-нод (default: `config/geoip_hints.yaml`) |
| `--no-geoip` | Вимкнути країну в підписах графа |
| `--no-geo-map` | Вимкнути вкладку Folium geo-map |
| `--ts-backend` | `influx` або `timescale` — time-series backend для RTT/route |
| `--influx-url`, `--influx-token`, `--influx-org`, `--influx-bucket` | InfluxDB 2.x (або `INFLUXDB_*` env) |
| `--timescale-dsn` | PostgreSQL/Timescale DSN (або `PINGUI_TIMESCALE_DSN`) |
| `--verbose` | Debug-лог |

## CI (локально)

```bash
./pingui.sh --deploy              # повний цикл (рекомендовано)
# або окремо:
./scripts/ci_venv.sh
.venv/bin/python scripts/check_imports.py
```

GitHub Actions: `.github/workflows/ci.yml` — той самий `ci_venv.sh` + import graph.

## ICMP capabilities

```bash
./pingui.sh --deploy              # автоматично setcap на .venv (--copies)
./scripts/check_caps.sh           # перевірка
./scripts/setup_caps.sh           # ручний setcap, якщо потрібно
```

## Manual QA чекліст

- [ ] `./pingui.sh --deploy`, потім `./pingui.sh`
- [ ] Додати ціль, увімкнути чекбокс — граф оновлюється
- [ ] Перемикання хоста у списку перемальовує граф
- [ ] **Зберегти** записує YAML
- [ ] Лог фіксує зміну маршруту (якщо трапиться)
- [ ] Закриття вікна завершує worker без зависання
- [ ] Без cap_net_raw — зрозуміле повідомлення про помилку

## Структура репозиторію

```
PINGUI/
├── pingui.sh                 # Python: Linux launcher
├── java/                     # Java: cross-platform edition
│   ├── pingui-java.sh
│   ├── build.gradle.kts
│   └── src/main/java/io/pingui/
├── pyproject.toml
├── config/hosts.example.yaml # Python config
├── src/pingui/               # пакет додатку
├── tests/                    # unit, contract, integration
├── scripts/                  # CI, cap_net_raw, import graph
├── systemd/                  # приклад service unit
└── docs/
    ├── README.md             # індекс документації
    ├── USER_GUIDE.md
    ├── ARCHITECTURE.md
    ├── DEPLOYMENT.md
    ├── DEVELOPMENT.md
    ├── TESTING.md
    ├── MODULES.md
    ├── CONFIGURATION.md
    ├── CONTRIBUTING.md
    ├── MVP_SPEC.md
    └── LIVING_SPEC.md
```

Детальний план і backlog: [ROADMAP.md](ROADMAP.md).

## Документація

Повний пакет: **[docs/README.md](docs/README.md)**

| Файл | Призначення |
|------|-------------|
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Посібник користувача GUI |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Розгортання, cap_net_raw, systemd |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Архітектура та потоки даних |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Розробка та стандарти коду |
| [docs/TESTING.md](docs/TESTING.md) | Тести та CI |
| [docs/MODULES.md](docs/MODULES.md) | Довідник модулів |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | YAML, CLI, env |
| [docs/MVP_SPEC.md](docs/MVP_SPEC.md) | Вимоги MVP |
| [docs/LIVING_SPEC.md](docs/LIVING_SPEC.md) | Living Spec |
| [docs/JAVA.md](docs/JAVA.md) | Java-редакція (cross-platform) |
| [java/README.md](java/README.md) | Запуск і розробка Java |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Участь у розробці |
| [ROADMAP.md](ROADMAP.md) | Фази та backlog |
| [CHANGELOG.md](CHANGELOG.md) | Історія змін |

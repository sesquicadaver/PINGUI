> **Мова:** Українська · [English](en/CONFIGURATION.md)

# Конфігурація PINGUI

## YAML — список цілей

Файл за замовчуванням: `config/hosts.example.yaml`.

```yaml
hosts:
  - "8.8.8.8"
  - "google.com"
```

### Правила

| Параметр | Значення |
|----------|----------|
| Кількість записів | 0–10 |
| Формат запису | IPv4 або hostname (латиниця, цифри, `-`, `.`) |
| Дублікати | Заборонені (case-insensitive) |
| Кодування файлу | UTF-8 |

Порожній список `hosts: []` — валідний; цілі додаються в GUI.

### Збереження з GUI

Кнопка **Зберегти** викликає `save_hosts_config(config_path, hosts)`.
Шлях конфігу передається при старті (`MainWindow.config_path`, зазвичай той самий файл, що й `--config`).

## CLI

### Підкоманди (PY-023)

```bash
.venv/bin/python -m pingui run              # GUI (за замовч.)
.venv/bin/python -m pingui export --csv out.csv
.venv/bin/python -m pingui monitor --config config/hosts.example.yaml
.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid
```

Плоский legacy-CLI (`--export-csv`, `--session-db` без підкоманди) збережено для зворотної сумісності.

### Launcher

```bash
./pingui.sh                              # GUI, config/hosts.example.yaml
./pingui.sh --export-csv report.csv      # headless export
./pingui.sh monitor --config config/hosts.example.yaml
./pingui.sh -- --session-db data/ping.db # явний роздільник launcher/CLI
```

### Базові опції

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--config` | Path | `config/hosts.example.yaml` | YAML з цілями |
| `--interval` | float | `1.0` | Секунди між повними циклами worker |
| `--max-hops` | int | `20` | Максимальний TTL |
| `--timeout` | float | `0.5` | Таймаут одного ICMP probe (с) |
| `--verbose` | flag | off | DEBUG-лог у stderr |

### Персистентність і export

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--session-db` | Path | — | SQLite: маршрути/ping між сесіями |
| `--export-csv` | Path | — | Експорт CSV і вихід (без GUI/ICMP) |
| `--export-html` | Path | — | Експорт HTML і вихід (без GUI/ICMP) |

### Alerts (PY-042…044)

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--alert-webhook` | URL | — | POST JSON `RouteChangeEvent` при зміні маршруту |
| `--desktop-alerts` | flag | off | Linux `notify-send` при зміні маршруту |
| `--alert-rate-limit` | int | `10` | Макс. алертів на host / годину |

Секрети в URL не логуються; помилки webhook — лише WARNING, процес не падає.

### GeoIP і карта

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--geoip-hints` | Path | `config/geoip_hints.yaml` | CIDR→country для міток hop |
| `--no-geoip` | flag | off | Вимкнути country hints |
| `--no-geo-map` | flag | off | Вимкнути вкладку folium geo-map |

### Time-series (optional extra: `pip install -e ".[timeseries]"`)

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--ts-backend` | `influx` \| `timescale` | — | Backend для RTT/route metrics |
| `--influx-url` | str | env `INFLUXDB_URL` | InfluxDB URL |
| `--influx-token` | str | env `INFLUXDB_TOKEN` | Token |
| `--influx-org` | str | env `INFLUXDB_ORG` | Org |
| `--influx-bucket` | str | env `INFLUXDB_BUCKET` | Bucket |
| `--timescale-dsn` | str | env `PINGUI_TIMESCALE_DSN` | PostgreSQL/Timescale DSN |

Валідація: `--interval`, `--timeout` > 0; `--max-hops` ≥ 1.

Режим export (`--export-csv` / `--export-html`) не вимагає raw ICMP і не запускає GUI.

## Змінні середовища

| Змінна | Коли | Опис |
|--------|------|------|
| `QT_QPA_PLATFORM=offscreen` | Тести / headless | Без дисплея |
| `MPLBACKEND=Agg` | Тести | Non-interactive Matplotlib |
| `QT_LOGGING_RULES` | `./pingui.sh` GUI | Приглушення Qt-шуму |
| `PYTHONWARNINGS=ignore` | `./pingui.sh` GUI | Менше warnings у консолі |
| `INFLUXDB_URL` | `--ts-backend influx` | InfluxDB endpoint |
| `INFLUXDB_TOKEN` | `--ts-backend influx` | InfluxDB token |
| `INFLUXDB_ORG` | `--ts-backend influx` | InfluxDB org |
| `INFLUXDB_BUCKET` | `--ts-backend influx` | InfluxDB bucket |
| `PINGUI_TIMESCALE_DSN` | `--ts-backend timescale` | PostgreSQL/Timescale DSN |

## Константи в коді

| Константа | Модуль | Значення |
|-----------|--------|----------|
| `MIN_HOSTS` | `config.py` | 0 |
| `MAX_HOSTS` | `config.py` | 10 |
| `MAX_PING_SAMPLES` | `session_store.py` | 50 |
| `DEFAULT_MAX_HOPS` | `icmp/tracer.py` | 20 |
| `DEFAULT_TIMEOUT` | `icmp/tracer.py` | 0.5 |

## Помилки конфігурації

Клас `ConfigError` (наслідує `ValueError`):

- файл не знайдено;
- некоректна структура YAML;
- невалідний hostname;
- дублікат або перевищення ліміту 10;
- помилка DNS (`resolve_host_ipv4`).

У GUI помилки додаються в текстовий лог з міткою часу.

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
| Формат запису | IPv4, IPv6 literal (RFC 5952, напр. `2001:db8::1` або `[::1]`) або hostname |
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
| `--no-persist-route-change` | flag | off | Не писати `route_change` у `persistence_event` (PY-P11) |
| `--no-persist-probe-error` | flag | off | Не писати `probe_error` у `persistence_event` (PY-P11) |
| `--export-csv` | Path | — | Експорт CSV і вихід (без GUI/ICMP) |
| `--export-html` | Path | — | Експорт HTML і вихід (без GUI/ICMP) |

YAML (спільний з Java SPIKE):

```yaml
persistence:
  session_db: data/ping.db   # якщо немає CLI --session-db
  events:
    route_change: true
    probe_error: true
```

Пріоритет: CLI > YAML > default (обидва типи подій увімкнені).

### Alerts (PY-042…044)

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--alert-webhook` | URL | — | POST JSON `RouteChangeEvent` при зміні маршруту |
| `--desktop-alerts` | flag | off | Linux `notify-send` при зміні маршруту |
| `--alert-rate-limit` | int | `10` | Макс. алертів на host / годину |

Секрети в URL не логуються; помилки webhook — лише WARNING, процес не падає.

### Java edition (`./pingui-java.sh`)

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--alert-webhook` | URL | — | POST JSON `RouteChangeEvent` при зміні маршруту |
| `--desktop-alerts` | flag | off | Linux `notify-send` при зміні маршруту |
| `--alert-rate-limit` | int | `10` | Макс. алертів на host / годину |

CLI має пріоритет над YAML. У профілі v2:

```yaml
profiles:
  noc:
    hosts:
      - "8.8.8.8"
    alerts:
      desktop: true
      webhook: https://hooks.example.com/ping
      rate_limit: 10
    # legacy alias:
    alert_webhook: https://hooks.example.com/ping
```

За замовчуванням alerts вимкнено (`NoOp` dispatcher).

### Телеметрія (P16-040…052, P16-080, P16-090…092)

Секція `telemetry:` у профілі v2 (Java) або top-level (Python `load_telemetry_config`). Пріоритет: **CLI > YAML > defaults**. За замовч. усі sinks **off**; `events_only: true`; `log_aggregates: false`. ADR: [ADR_TELEMETRY.md](ADR_TELEMETRY.md). Приклад: `java/config/hosts.example.yaml`. Windows-пресет: `config/hosts.windows.example.yaml` (P16-043: `events_only` без `jsonl_dir`).

**Java GUI (P16-090…092):** sinks працюють у desktop через `TelemetryAttachment`. Меню **Налаштування → Телеметрія…** редагує `events_only`, `log_aggregates`, local sqlite/jsonl, syslog(+TLS), GELF(+transport), Loki(URL+site), OTLP(endpoint+service); статус — `toRedactedString()`. Apply оновлює активний профіль і re-wire bus; запис на диск — «Зберегти». CLI `--telemetry-syslog` / `--telemetry-jsonl` / `--telemetry-otlp` блокують відповідні поля.

**Python (P16-093):** `telemetry:` + `--telemetry-*` **валідуються** при старті GUI/daemon; LOG sinks (sqlite/jsonl/syslog/GELF/Loki/OTLP) **не емітяться** у Python runtime — лише Java. TS push — `--ts-backend` / `InfluxTelemetrySink`. Якщо YAML має LOG sinks, stderr note (verbose додає `redacted_summary`).

```yaml
profiles:
  noc:
    hosts:
      - "8.8.8.8"
    telemetry:
      events_only: true
      log_aggregates: false
      sqlite: data/telemetry.db
      jsonl_dir: data/telemetry
      syslog:
        host: 127.0.0.1
        port: 514
        tls: false
      gelf:
        host: 127.0.0.1
        port: 12201
        transport: tcp   # tcp | udp
      loki:
        url: http://127.0.0.1:3100
        site: default
      otlp:                         # P16-080 OTLP/HTTP JSON (Collector :4318)
        endpoint: http://127.0.0.1:4318
        service_name: pingui
```

#### YAML `telemetry:` — поля

| Поле | Тип | За замовч. | Опис |
|------|-----|------------|------|
| `events_only` | bool | `true` | Remote LOG sinks (syslog/GELF/Loki/OTLP logs) приймають лише events; high-freq RTT samples — ні |
| `log_aggregates` | bool | `false` | Опційні 5m avg/max RTT → event `rtt_aggregate` (P16-034) |
| `sqlite` | Path | — (off) | Локальний `SqliteTelemetrySink` (schema v4) |
| `jsonl_dir` | Path | — (off) | Каталог `JsonlRotateSink` (`telemetry.jsonl.yyyy-MM-dd`) |
| `syslog.host` | str | — | RFC 5424 TCP syslog |
| `syslog.port` | int | `514` | 1…65535 |
| `syslog.tls` | bool | `false` | TLS на syslog TCP |
| `gelf.host` | str | — | Graylog GELF |
| `gelf.port` | int | `12201` | 1…65535 |
| `gelf.transport` | `tcp` \| `udp` | `tcp` | TCP `\0` framing (prod) / UDP (lab) |
| `loki.url` | URL | — | Base або повний `/loki/api/v1/push` |
| `loki.site` | str | — | Label `site` (разом із `job=pingui`, `host`) |
| `otlp.endpoint` | URL | — | OTLP/HTTP base (додає `/v1/logs`, `/v1/metrics`) |
| `otlp.service_name` | str | `pingui` | Resource attribute `service.name` |

Наявність блоку sink (`sqlite` / `jsonl_dir` / `syslog` / `gelf` / `loki` / `otlp`) **увімкнює** відповідний sink у моделі конфігу. Секрети в URL/token **не** логуються plaintext (P16-042: `TelemetryConfig.redactUrl` / `redactSecret`). OTLP: events → logs; samples → metrics лише коли `events_only: false` (Java `OtlpHttpTelemetrySink`).

#### CLI телеметрії (Java + Python)

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--telemetry-syslog` | `HOST:PORT` або `[IPv6]:PORT` | — | Override `telemetry.syslog` (tls=false) |
| `--telemetry-jsonl` | Path | — | Override `telemetry.jsonl_dir` (не плутати з retention) |
| `--telemetry-otlp` | URL | — | Override `telemetry.otlp.endpoint` (service_name=`pingui`) |

#### CLI one-shot / scrape (переважно Java)

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--telemetry-retention` | int ≥ 1 | — | Cron: purge SQLite (± JSONL) старші за N днів і вихід |
| `--telemetry-jsonl-dir` | Path | — | Каталог JSONL лише для `--telemetry-retention` |
| `--telemetry-dump` | Path `.csv`/`.json` | — | Dump telemetry з `--session-db` і вихід |
| `--metrics-port` | int | — (off) | Daemon: Prometheus scrape `127.0.0.1:N` → `PrometheusTelemetrySink` (P16-051) |

Time-series push (`--ts-backend influx|timescale` + Influx/Timescale flags/env) — окремий канал; у Python (P16-052) йде через `InfluxTelemetrySink` з telemetry bus. Див. секцію Time-series нижче.

Webhook route alerts лишаються в `alerts.webhook` / `--alert-webhook` (P10); HTTP emit — `WebhookTelemetrySink` (P16-050), payload ADR_ALERTS без змін.
### GeoIP і карта

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--geoip-hints` | Path | `config/geoip_hints.yaml` | CIDR→country для міток hop (`prefixes` v4, `prefixes_v6` v6) |
| `--no-geoip` | flag | off | Вимкнути country hints |
| `--asn-hints` | Path | `config/asn_hints.yaml` | CIDR→ASN+org для міток hop (`{asn, org}`) |
| `--no-asn` | flag | off | Вимкнути ASN hints |
| `--asn-timeout-ms` | int | `2000` | Зарезервовано під майбутній whois fallback |
| `--no-geo-map` | flag | off | Вимкнути вкладку folium geo-map |

Expert ping presets (Java GUI, P14-040 / P17-010): `config/ping_presets.yaml` поруч із hosts-конфігом (або CWD `config/ping_presets.yaml`); інакше bundled resource. Рівно 4 пресети (`mtu_probe`, `df`, `dscp`, `burst`). Обовʼязкові поля: `id`, `label`, `args`, `summary`, `expect`; опційно `caution`. Кнопки в `PingExpertDialog` підставляють args (збережений AF `-4`/`-6`) і показують status/tooltip з UX-копією. Пресети **не** запускають MTU sweep.

MTU discovery engine (P17-020, API): `MtuDiscovery` + `ProcessMtuProbeRunner` — лінійний ascending sweep `-s` (`min → start`) з `-M do`, N проб на розмір, stop при loss% ≥ порогу (default 1%), `recommendedMtu = last_good_payload + 28` (IPv4) / `+ 48` (IPv6). GUI wizard (P17-021): `MtuDiscoveryDialog` — HostList **MTU** / Expert «MTU wizard…»; Apply → `-M do -s <payload>` (пресет «MTU probe» лишається окремим).

### Time-series (optional extra: `pip install -e ".[timeseries]"`)

| Опція | Тип | За замовч. | Опис |
|-------|-----|------------|------|
| `--ts-backend` | `influx` \| `timescale` | — | Backend для RTT/route metrics |
| `--influx-url` | str | env `INFLUXDB_URL` | InfluxDB URL |
| `--influx-token` | str | env `INFLUXDB_TOKEN` | Token |
| `--influx-org` | str | env `INFLUXDB_ORG` | Org |
| `--influx-bucket` | str | env `INFLUXDB_BUCKET` | Bucket |
| `--timescale-dsn` | str | env `PINGUI_TIMESCALE_DSN` | PostgreSQL/Timescale DSN |

Python (P16-052): `--ts-backend` підключає `InfluxTelemetrySink` до telemetry bus (не dual-emit через SessionStore). Java лишає SessionStore dual-emit до окремого wire.

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
- невалідний hostname або IPv6 (zone ID `%iface` заборонено);
- дублікат або перевищення ліміту 10;
- помилка DNS (`resolve_host_ipv4`).

У GUI помилки додаються в текстовий лог з міткою часу.

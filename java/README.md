> **Мова:** Українська · [English](README.en.md)

# PINGUI Java

> **Мова:** Українська · [English](README.en.md)

Крос-платформова версія PINGUI на **Java 21 + JavaFX**.

Працює на **Linux, macOS та Windows**: трасування через системні
`traceroute` / `tracert`. Дані сесії — в RAM за замовчуванням; опційно SQLite через `--session-db` (P11-011…012).

> **Рекомендація:** **Linux** — оптимальна платформа (швидкий `traceroute -q 1`, Expert ping, raw ICMP). **Windows** — для періодичних перевірок: повний trace повільний через `tracert`; стартовий пресет `config/hosts.windows.example.yaml` (`probe_mode: ping_only`, `interval: 60`). [docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md#рекомендація-щодо-ос)

## Вимоги

| Компонент | Версія |
|-----------|--------|
| JDK | **21** (Java 25 як launcher Gradle не підтримується) |
| traceroute | Linux/macOS |
| tracert | Windows (вбудований) |

## Швидкий старт

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh gradlew
./pingui-java.sh              # GUI
./pingui-java.sh --build      # збірка
./pingui-java.sh --package    # jpackage (.deb / .dmg / .msi)
./pingui-java.sh --help
```

**Windows**

> ⚠ Повільне трасування — див. [DEPLOYMENT.md](../docs/DEPLOYMENT.md#рекомендація-щодо-ос). Launcher: `pingui-java.bat` або `gradlew.bat run`.

Потрібен **JDK 21** ([Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21); Add to PATH + JAVA_HOME).

`pingui-java.bat` — обгортка над `gradlew.bat`. Якщо `gradlew.bat build` успішний — launcher працює так само.

```bat
cd java
gradlew.bat build
gradlew.bat run
rem або
pingui-java.bat --build
pingui-java.bat --config config/hosts.windows.example.yaml
```

Якщо `java` не в PATH:

```bat
set "PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot"
pingui-java.bat --build
```

Gradle напряму:

```bash
cd java
./gradlew run          # Linux / macOS
gradlew.bat run        # Windows
```

## CLI

```bash
./pingui-java.sh -- --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Опція | За замовч. | Опис |
|-------|------------|------|
| `--config` | `config/hosts.example.yaml` | YAML з 0–10 цілями |
| `--interval` | *(з YAML)* | Перезапис poll interval (с), **лише якщо передано** |
| `--max-hops` | *(з YAML)* | Перезапис max hop, **лише якщо передано** |
| `--timeout` | *(з YAML)* | Перезапис probe timeout (с), **лише якщо передано** |
| `--probe` | *(з YAML)* | Перезапис `auto`/`process`/`raw`, **лише якщо передано** |
| `--alert-webhook` | off | POST JSON `RouteChangeEvent` при зміні маршруту |
| `--desktop-alerts` | off | Linux `notify-send` при зміні маршруту |
| `--alert-rate-limit` | `10` | Макс. алертів на host / годину |
| `--session-db` | off | SQLite метрики сесії + події (`host_session`, `persistence_event`); альтернатива — YAML `persistence.session_db` або GUI «База даних…» |
| `--export-report` | off | Експорт CSV/HTML з `--session-db` і вихід (без GUI) |
| `--export-schedule` | off | Cron one-shot: `hourly` \| `daily` \| `weekly` (разом із `--export-dir`) |
| `--export-dir` | off | Каталог для `--export-schedule` (пише CSV+HTML зі штампом UTC) |
| `--daemon` | off | Headless `MonitorService` без JavaFX (NOC) |
| `--pid-file` | `$TMP/pingui-java.pid` | PID-файл для `--daemon` / `--stop` / `--status` |
| `--metrics-port` | off | Prometheus `GET /metrics` на `127.0.0.1:N` (лише з `--daemon`) |
| `--api-port` | off | Read-only REST: `/hosts`, `/routes/{host}`, `/openapi.json` на `127.0.0.1:N` (daemon) |
| `--ts-backend` | off | Time-series push: `influx` \| `timescale` (Python B-05 parity) |
| `--influx-url` / `--influx-token` / `--influx-org` / `--influx-bucket` | env `INFLUXDB_*` | InfluxDB 2.x write (token не логується) |
| `--timescale-dsn` | env `PINGUI_TIMESCALE_DSN` | PostgreSQL/Timescale JDBC або `postgresql://…` |
| `--stop` | off | Зупинити daemon за PID-файлом |
| `--status` | off | Статус daemon (running/stopped) |
| `--no-persist-route-change` | off | Не писати `route_change` у SQLite |
| `--no-persist-probe-error` | off | Не писати `probe_error` у SQLite |
| `--geoip-hints` | `config/geoip_hints.yaml` | Offline CIDR→країна |
| `--no-geoip` | off | Вимкнути країну в підписах |
| `--asn-hints` | `config/asn_hints.yaml` | Offline CIDR→ASN+org |
| `--no-asn` | off | Вимкнути ASN у підписах |
| `--asn-timeout-ms` | `2000` | Резерв під whois fallback |
| `--telemetry-syslog` | off | Syslog sink `HOST:PORT` (override YAML) |
| `--telemetry-jsonl` | off | Каталог JSONL rotate sink |
| `--telemetry-otlp` | off | OTLP/HTTP endpoint |
| `--telemetry-dump` | off | Dump telemetry SQLite/JSONL |
| `--verbose` | off | Debug-лог |

CLI **не затирає** поля профілю defaults (1.0 / 20 / 0.5 / auto), якщо відповідний прапор не передано. Повний telemetry YAML: [CONFIGURATION § telemetry](../docs/CONFIGURATION.md).

**Prometheus (P15-010/011):** `./pingui-java.sh -- --daemon --metrics-port 9090` → `http://127.0.0.1:9090/metrics`. Метрики: `pingui_rtt_ms`, `pingui_route_change_total`, `pingui_target_reachable`, `pingui_trace_duration_ms`. Без `--metrics-port` listener не стартує.

**Time-series (P15-020):** `--ts-backend influx` (+ Influx flags/env) або `--ts-backend timescale --timescale-dsn …` — dual-emit RTT/route з `SessionStore` (GUI і daemon). Помилки write → WARN, poll не зупиняється. JDBC PostgreSQL **опційний** (P19-006): default jpackage/`installDist` без драйвера; для Timescale — `./gradlew run -PwithPostgresql=true` (див. [DEPLOYMENT.md](../docs/DEPLOYMENT.md#збірка-та-пакування)).

**Scheduled export (P15-030):** `./pingui-java.sh -- --session-db data/session.db --export-schedule daily --export-dir reports/` → `pingui-daily-YYYY-MM-DD.csv` + `.html` (UTC). Для cron; не тримає процес.

**Read-only API (P15-040):** `./pingui-java.sh -- --daemon --api-port 8080` → `http://127.0.0.1:8080/hosts`, `/routes/{host}`, `/openapi.json`. Auth поза scope v1 — див. [DEPLOYMENT § reverse proxy + TLS](../docs/DEPLOYMENT.md#reverse-proxy--tls-p15-041).

## GUI

- **Про** / **Довідка** — меню з діалогами «Про PINGUI…» та «Довідка…» (F1); dual-stack IPv4/IPv6 literal
- **Профілі трасування**: кілька named-профілів у YAML, перемикання в UI
- Список до **10 цілей**, чекбокс = активне трасування; **Ping only** = лише ping без trace
- **Додати / Змінити / Видалити / Зберегти** → YAML
- **Експерт** (Linux): **Exten.** → пресети `ping(8)`; **MTU** / **MTU wizard…** (sweep `-s` + `-M do`); **Self-check** DF/DSCP/Burst → Alert; один AF (`-4`/`-6`); на Win/mac disabled
- **Телеметрія…** (меню Налаштування): sinks sqlite/jsonl/syslog/GELF/Loki/OTLP + `events_only`
- **Простий** / **Розширений**: метрики RTT, loss %, граф маршруту, лог змін
- **База даних…** (меню): підключення SQLite без CLI; **Історія змін** — timeline `route_change` + replay на графі (розширений режим)

## Архітектура

```
io.pingui
├── config/          ProfilesConfig, PingExpertEntry, PingPresets, TelemetryConfig
├── model/           HopNode, RouteSnapshot
├── probe/           RouteProbeFactory, ProcessRouteProbe, ProcessExpertPing,
                       MtuDiscovery*, PresetSelfCheck*, Trace parsers
├── monitor/         SessionStore, MonitorService, AlertDispatchers, RouteChangeEvent
├── telemetry/       TelemetryBus, sinks (sqlite/jsonl/syslog/GELF/Loki/OTLP), SinkRegistry
├── persistence/     SessionDatabase, PersistenceEventWriter (P11); timeseries/ (P15-020)
├── observability/   PrometheusExporter, PrometheusTelemetrySink (P16-051), MetricsHttpServer (P15-010)
├── api/             ReadOnlyApiServer (P15-040)
├── export/          SessionReportExporter (P11-030), ScheduledExport (P15-030)
└── ui/              MainController, HostListPresenter, PingExpertDialog,
                       MtuDiscoveryDialog, PresetSelfCheckUi, TelemetrySettingsDialog, GraphCanvas
```

Деталі: [docs/JAVA.md](../docs/JAVA.md).

### Формат профілів (v2)

```yaml
active_profile: office
profiles:
  office:
    interval: 1.0
    max_hops: 20
    timeout: 0.5
    probe: auto
    hosts:
      - address: "8.8.8.8"
        enabled: true
        ping_only: false
        ping_expert:
          chain: false
          args: ["-4", "-s", "128"]
      - "2001:db8::1"
      - address: "2001:4860:4860::8888"
        enabled: true
```

## Збірка

```bash
cd java
./gradlew check          # compile + Spotless + Checkstyle + layerCheck + JaCoCo + tests
./gradlew test           # JUnit 5 only
./gradlew spotlessApply  # автоформат Java / Gradle Kotlin DSL
./gradlew build
./gradlew run
./gradlew jpackageDeb   # Linux .deb → build/dist/
```

Unit-тести — `src/test/java`; матриця: [docs/LIVING_SPEC.md](../docs/LIVING_SPEC.md). CI: ![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)

## Пакування (jpackage)

Єдине джерело версії — `version` у `build.gradle.kts` (`0.2.0-SNAPSHOT` за замовч.). `generateBuildProperties` пише її в `pingui/build.properties` і JAR manifest; About (`AppInfo`) читає звідти. `jpackage --app-version` бере semver без суфікса `-SNAPSHOT` (напр. `0.2.0`).

```bash
./pingui-java.sh --package    # Linux / macOS
pingui-java.bat --package     # Windows
ls build/dist/
```

> **Language:** English · [Українська](README.md)

# PINGUI Java

Cross-platform PINGUI built with **Java 21 + JavaFX**.

Runs on **Linux, macOS, and Windows**: route tracing via system
`traceroute` / `tracert`. Session data is RAM-only by default; optional SQLite via `--session-db` (P11-011…012).

> **Recommendation:** **Linux** is the optimal platform (fast `traceroute -q 1`, Expert ping, raw ICMP). **Windows** is suitable for periodic checks: full trace is slow via `tracert`; starter preset `config/hosts.windows.example.yaml` (`probe_mode: ping_only`, `interval: 60`). [docs/DEPLOYMENT.md](../docs/en/DEPLOYMENT.md#os-recommendation)

## Requirements

| Component | Version |
|-----------|---------|
| JDK | **21** (Java 25 is not supported as the Gradle launcher) |
| traceroute | Linux/macOS |
| tracert | Windows (built-in) |

## Quick start

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh gradlew
./pingui-java.sh              # GUI
./pingui-java.sh --build      # build
./pingui-java.sh --package    # jpackage (.deb / .dmg / .msi)
./pingui-java.sh --help
```

**Windows**

> ⚠ Slow tracing — see [DEPLOYMENT.md](../docs/en/DEPLOYMENT.md#os-recommendation). Launcher: `pingui-java.bat` or `gradlew.bat run`.

Requires **JDK 21** ([Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21); Add to PATH + JAVA_HOME).

`pingui-java.bat` wraps `gradlew.bat`. If `gradlew.bat build` succeeds, the launcher works the same way.

```bat
cd java
gradlew.bat build
gradlew.bat run
rem or
pingui-java.bat --build
pingui-java.bat --config config/hosts.windows.example.yaml
```

If `java` is not on PATH:

```bat
set "PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot"
pingui-java.bat --build
```

Gradle directly:

```bash
cd java
./gradlew run          # Linux / macOS
gradlew.bat run        # Windows
```

## CLI

```bash
./pingui-java.sh -- --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Option | Default | Description |
|--------|---------|-------------|
| `--config` | `config/hosts.example.yaml` | YAML with 0–10 targets |
| `--interval` | *(from YAML)* | Override poll interval (s), **only if passed** |
| `--max-hops` | *(from YAML)* | Override max hop, **only if passed** |
| `--timeout` | *(from YAML)* | Override probe timeout (s), **only if passed** |
| `--probe` | *(from YAML)* | Override `auto`/`process`/`raw`, **only if passed** |
| `--alert-webhook` | off | POST JSON `RouteChangeEvent` on route change |
| `--desktop-alerts` | off | Linux `notify-send` on route change |
| `--alert-rate-limit` | `10` | Max alerts per host / hour |
| `--session-db` | off | SQLite session metrics + events (`host_session`, `persistence_event`); alternative — YAML `persistence.session_db` or GUI **Database…** |
| `--export-report` | off | Export CSV/HTML from `--session-db` and exit (no GUI) |
| `--export-schedule` | off | Cron one-shot: `hourly` \| `daily` \| `weekly` (with `--export-dir`) |
| `--export-dir` | off | Directory for `--export-schedule` (writes stamped CSV+HTML) |
| `--daemon` | off | Headless `MonitorService` without JavaFX (NOC) |
| `--pid-file` | `$TMP/pingui-java.pid` | PID file for `--daemon` / `--stop` / `--status` |
| `--metrics-port` | off | Prometheus `GET /metrics` on `127.0.0.1:N` (with `--daemon` only) |
| `--api-port` | off | Read-only REST: `/hosts`, `/routes/{host}`, `/openapi.json` on `127.0.0.1:N` (daemon) |
| `--ts-backend` | off | Time-series push: `influx` \| `timescale` (Python B-05 parity) |
| `--influx-url` / `--influx-token` / `--influx-org` / `--influx-bucket` | env `INFLUXDB_*` | InfluxDB 2.x write (token never logged) |
| `--timescale-dsn` | env `PINGUI_TIMESCALE_DSN` | PostgreSQL/Timescale JDBC or `postgresql://…` |
| `--stop` | off | Stop daemon via PID file |
| `--status` | off | Daemon status (running/stopped) |
| `--no-persist-route-change` | off | Skip `route_change` events in SQLite |
| `--no-persist-probe-error` | off | Skip `probe_error` events in SQLite |
| `--geoip-hints` | `config/geoip_hints.yaml` | Offline CIDR→country |
| `--no-geoip` | off | Disable country in labels |
| `--asn-hints` | `config/asn_hints.yaml` | Offline CIDR→ASN+org |
| `--no-asn` | off | Disable ASN in labels |
| `--asn-timeout-ms` | `2000` | Reserved for whois fallback |
| `--verbose` | off | Debug log |

The CLI **does not overwrite** profile defaults (1.0 / 20 / 0.5 / auto) unless the corresponding flag is provided.

**Prometheus (P15-010/011):** `./pingui-java.sh -- --daemon --metrics-port 9090` → `http://127.0.0.1:9090/metrics`. Metrics: `pingui_rtt_ms`, `pingui_route_change_total`, `pingui_target_reachable`, `pingui_trace_duration_ms`. Without `--metrics-port` no listener starts.

**Time-series (P15-020):** `--ts-backend influx` (+ Influx flags/env) or `--ts-backend timescale --timescale-dsn …` — dual-emit RTT/route from `SessionStore` (GUI and daemon). Write failures → WARN; poll continues.

**Scheduled export (P15-030):** `./pingui-java.sh -- --session-db data/session.db --export-schedule daily --export-dir reports/` → `pingui-daily-YYYY-MM-DD.csv` + `.html` (UTC). For cron; does not keep the process running.

**Read-only API (P15-040):** `./pingui-java.sh -- --daemon --api-port 8080` → `http://127.0.0.1:8080/hosts`, `/routes/{host}`, `/openapi.json`. Auth out of scope for v1 (see P15-041).

## GUI

- **About** / **Help** — menu with “About PINGUI…” and “Help…” dialogs (F1); dual-stack IPv4/IPv6 literals
- **Trace profiles**: multiple named profiles in YAML, switchable in the UI
- List of up to **10 targets**, checkbox = active tracing; **Ping only** = ping without trace
- **Add / Edit / Delete / Save** → YAML
- **Expert** (Linux): **Exten.** → `ping(8)` iputils parameters; single AF (`-4` or `-6`, default IPv4); disabled on Win/mac
- **Simple** / **Advanced**: RTT metrics, loss %, route graph, change log
- **Database…** (menu): connect SQLite without CLI; **Route history** — `route_change` timeline + graph replay (advanced mode)

## Architecture

```
io.pingui
├── config/          ProfilesConfig, PingExpertEntry
├── model/           HopNode, RouteSnapshot
├── probe/           RouteProbeFactory, ProcessRouteProbe, TraceCommandBuilder,
                       UnixTraceOutputParser, WindowsTraceOutputParser, ProcessExpertPing
├── monitor/         SessionStore, MonitorService, AlertDispatchers, RouteChangeEvent
├── persistence/     SessionDatabase, PersistenceEventWriter (P11); timeseries/ (P15-020)
├── observability/   PrometheusExporter, MetricsHttpServer (P15-010)
├── api/             ReadOnlyApiServer (P15-040)
├── export/          SessionReportExporter (P11-030), ScheduledExport (P15-030)
└── ui/              MainController (wiring), ProfileUiCoordinator, HostListPresenter,
                       MonitorLifecycle, ViewModeController, RouteGraphPresenter, GraphCanvas
```

Details: [docs/JAVA.md](../docs/en/JAVA.md).

### Profile format (v2)

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

## Build

```bash
cd java
./gradlew check          # compile + Spotless + Checkstyle + layerCheck + JaCoCo + tests
./gradlew test           # JUnit 5 only
./gradlew spotlessApply  # auto-format Java / Gradle Kotlin DSL
./gradlew build
./gradlew run
./gradlew jpackageDeb   # Linux .deb → build/dist/
```

Unit tests (21) — `src/test/java`; matrix: [docs/LIVING_SPEC.md](../docs/en/LIVING_SPEC.md). CI: ![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)

## Packaging (jpackage)

```bash
./pingui-java.sh --package    # Linux / macOS
pingui-java.bat --package     # Windows
ls build/dist/
```

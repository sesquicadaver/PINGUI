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
| `--no-persist-route-change` | off | Skip `route_change` events in SQLite |
| `--no-persist-probe-error` | off | Skip `probe_error` events in SQLite |
| `--geoip-hints` | `config/geoip_hints.yaml` | Offline CIDR→country |
| `--no-geoip` | off | Disable country in labels |
| `--asn-hints` | `config/asn_hints.yaml` | Offline CIDR→ASN+org |
| `--no-asn` | off | Disable ASN in labels |
| `--asn-timeout-ms` | `2000` | Reserved for whois fallback |
| `--verbose` | off | Debug log |

The CLI **does not overwrite** profile defaults (1.0 / 20 / 0.5 / auto) unless the corresponding flag is provided.

**Prometheus (P15-010):** the daemon can listen on `http://127.0.0.1:<port>/metrics` when `AppOptions.metricsPort` is set (CLI `--metrics-port` — P15-011). Metrics: `pingui_rtt_ms`, `pingui_route_change_total`, `pingui_target_reachable`, `pingui_trace_duration_ms`.

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
├── persistence/     SessionDatabase, PersistenceEventWriter (P11-010…011)
├── observability/   PrometheusExporter, MetricsHttpServer (P15-010)
├── export/          SessionReportExporter (P11-030)
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

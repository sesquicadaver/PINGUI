> **Language:** [Ukrainian](../README.md) · English

# PINGUI Java

Cross-platform PINGUI on **Java 21 + JavaFX**.

Runs on **Linux, macOS, and Windows**: tracing via system
`traceroute` / `tracert`. Session data — RAM only.

> **Recommendation:** **Linux** — optimal platform (fast `traceroute -q 1`, Expert ping, raw ICMP). **Windows** — for periodic checks: full trace is slow via `tracert`; in GUI use **Ping only** or increase `interval` in YAML. [docs/en/DEPLOYMENT.md](../docs/en/DEPLOYMENT.md#os-recommendation)

## Requirements

| Component | Version |
|-----------|---------|
| JDK | **21** (Java 25 as Gradle launcher is not supported) |
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

`pingui-java.bat` wraps `gradlew.bat`. If `gradlew.bat build` succeeds — launcher works the same.

```bat
cd java
gradlew.bat build
gradlew.bat run
rem or
pingui-java.bat --build
pingui-java.bat
```

If `java` is not in PATH:

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
| `--geoip-hints` | `config/geoip_hints.yaml` | Offline CIDR→country |
| `--no-geoip` | off | Disable country in labels |
| `--verbose` | off | Debug log |

CLI **does not overwrite** profile defaults (1.0 / 20 / 0.5 / auto) unless the corresponding flag is passed.

## GUI

- **About** / **Help** — menu with «About PINGUI…» and «Help…» (F1)
- **Tracing profiles**: multiple named profiles in YAML, switch in UI
- List up to **10 targets**, checkbox = active tracing; **Ping only** = ping without trace
- **Add / Edit / Delete / Save** → YAML
- **Expert** (Linux): **Exten.** → `ping(8)` iputils params; disabled on Win/mac
- **Simple** / **Extended**: RTT metrics, loss %, route graph, change log

## Architecture

```
io.pingui
├── config/          ProfilesConfig, PingExpertEntry
├── model/           HopNode, RouteSnapshot
├── probe/           RouteProbeFactory, ProcessRouteProbe, TraceCommandBuilder,
                       UnixTraceOutputParser, WindowsTraceOutputParser, ProcessExpertPing
├── monitor/         SessionStore, MonitorService, ExpertPingEnricher
└── ui/              MainController (wiring), ProfileUiCoordinator, HostListPresenter,
                       MonitorLifecycle, ViewModeController, RouteGraphPresenter, GraphCanvas
```

Details: [docs/en/JAVA.md](../docs/en/JAVA.md).

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
./gradlew spotlessApply  # autoformat Java / Gradle Kotlin DSL
./gradlew build
./gradlew run
./gradlew jpackageDeb   # Linux .deb → build/dist/
```

Unit tests (21+) — `src/test/java`; matrix: [docs/en/LIVING_SPEC.md](../docs/en/LIVING_SPEC.md). CI: ![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)

## Packaging (jpackage)

```bash
./pingui-java.sh --package    # Linux / macOS
pingui-java.bat --package     # Windows
ls build/dist/
```

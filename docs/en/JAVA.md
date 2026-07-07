> **Language:** English · [Українська](../JAVA.md)

# PINGUI Java — Architecture and Deployment

Cross-platform implementation in the [`java/`](../../java/) directory.

## Goal

Enable route monitoring **independently of OS** without Python/PyQt6 and without Linux-only `cap_net_raw`.

## Stack

- Java 21 (LTS)
- JavaFX 21 (GUI)
- Gradle 8.10 (Kotlin DSL)
- Spotless + Palantir Java Format (`./gradlew spotlessCheck`)
- SnakeYAML (config)
- JUnit 5 (unit tests in `src/test/java`, `./gradlew test`)

## Limitations

- **Raw ICMP** — IPv4 only (`AF_INET`); IPv6 literal with `probe: auto` on Linux automatically uses subprocess trace.
- **Hostname AAAA** — OS resolve for trace/ping; explicit `-6` in Expert or v6 literal in YAML.
- Full ICMPv6 raw trace — see [ROADMAP.md](../ROADMAP.md) V6-040+.

## CLI vs YAML profile

| Profile field | Default source | CLI override |
|---------------|----------------|--------------|
| `interval` | YAML active profile | `--interval SEC` (only if passed) |
| `max_hops` | YAML | `--max-hops N` |
| `timeout` | YAML | `--timeout SEC` |
| `probe` | YAML | `--probe MODE` |

Implementation: `CliProfileOverrides` + `PinguiApplication.parseOptions()`; merge in `MainController.applyCliOverridesToActiveProfile()`.

## Probe layer

Python uses scapy + raw ICMP. Java supports two backends:

| Backend | Class | OS | Requirements |
|---------|-------|-----|--------------|
| **process** (default) | `ProcessRouteProbe` | Linux, macOS, Windows | `traceroute` / `tracert` in PATH |
| **raw-icmp** (Linux) | `RawIcmpRouteProbe` | Linux | JNA + `CAP_NET_RAW` or root |
| **auto** | `RouteProbeFactory` | Linux + cap → raw for v4/hostname, process for v6 literal |

CLI: `--probe auto|process|raw` (default: `auto`).

> **Performance:** on **Windows** subprocess trace via `tracert` is orders of magnitude slower than Linux `traceroute` (`-q 1`). For production monitoring **Linux** is recommended; on Windows use **Ping only** or a large `interval`. See [DEPLOYMENT.md](DEPLOYMENT.md#os-recommendation).

### ProcessRouteProbe (subprocess)

| OS | Builder | Command |
|----|---------|---------|
| Linux | `LinuxTracerouteCommand` | `traceroute -n -w SEC -m N -q 1 HOST` (GNU inetutils: without `-n`) |
| macOS | `MacTracerouteCommand` | same; binary `/usr/sbin/traceroute` if present |
| Windows | `WindowsTracertCommand` | `tracert -d -h N -w MS HOST` (3 probes/hop, MS ≥ 4000) — **slow** |

Parsers: `UnixTraceOutputParser`, `WindowsTraceOutputParser`. Factory: `TraceCommandFactory`.

#### Parser limitations (known limitations)

| Area | Behavior |
|------|----------|
| **IPv6 trace output** | Literal v6: `-6` in traceroute/tracert + v6 parsers (fixtures `unix_v6_*`, `win_v6_*`). Hostname AAAA — OS resolve, not PINGUI |
| **ASN / IGP labels** | Not parsed; hop IP taken from first IPv4 token or `[IP]` |
| **Unix hostname hops** | Token after hop number stored as “IP” (may be hostname) |
| **Windows localization** | Timeout: `timed out`, `timeout`, `перевищ…`; RTT: `ms` / `мс`, `<1 ms` → 0.5 |
| **GNU inetutils** | Flavor without `-n`; `-n` gives exit 64 — detection via `traceroute --version` |
| **Mixed trace formats** | Classic vertical traceroute/tracert only; MTR/JSON — out of scope |

### RawIcmpRouteProbe (JNA, Linux)

Incremental TTL 1..N via raw ICMP socket (parity with Python `trace_route`).

Requires: `sudo setcap cap_net_raw+ep` on JDK binary or run as root.

## Monitor layer

`MonitorService` — single daemon thread (`ScheduledExecutorService`):

1. Collects enabled hosts.
2. `RoutePoller.pollHostRoute()` → `RouteProbe.trace()`.
3. If expert ping is configured for a host — `ExpertPingEnricher` runs `ping -c 1` with extra flags.
4. Callbacks: `onDataReceived`, `onRouteChanged`, `onProbeError`.

Store/history/change detection logic — port from Python (`SessionStore`, `RouteHistory`, `RouteChangeDetector`).

## UI layer

`MainController` (JavaFX):

- **About** / **Help** (F1) menu — `AppMenuDialogs`
- **Trace profile** selection (ComboBox + new/delete); all profiles in one YAML
- **“Expert”** checkbox → **Exten.** button on host row → `PingExpertDialog` (catalog from `pingMan.txt`, without `-c/-w/-W/-i` etc.)
- `ListView<HostItem>` + CheckBox in cell
- **GraphCanvas** — vertical graph, inactive/active columns
- Log `TextArea`

Worker updates via `Platform.runLater()`.

## Configuration

**v2 (Java, recommended)** — multiple profiles + expert ping:

```yaml
active_profile: default
profiles:
  default:
    interval: 1.0
    max_hops: 20
    timeout: 0.5
    probe: auto
    hosts:
      - address: "8.8.8.8"
        enabled: true
        ping_expert:
          chain: false
          args: ["-4", "-s", "128"]
```

**Legacy** (Python + Java load):

```yaml
hosts:
  - "8.8.8.8"
  - "google.com"
```

Legacy auto-migrates to profile `default`. Save from Java UI writes v2.

Default file: `java/config/hosts.example.yaml` (working directory — `java/`).

## Build

**Linux / macOS**

```bash
cd java && ./gradlew build
cd java && ./pingui-java.sh --package    # jpackage (.deb / .dmg)
```

**Windows**

```bat
cd java
gradlew.bat build
pingui-java.bat --package    REM .msi
```

Tests and CI — branch **`beta`**.

## MVP parity matrix

| ID | Requirement | Java status |
|----|-------------|-------------|
| F-01 | YAML 0–10 | ✅ |
| F-02 | GUI CRUD | ✅ |
| F-03 | Trace enabled only | ✅ |
| F-04 | Max 10 active | ✅ |
| F-05 | Vertical graph | ✅ JavaFX GraphCanvas |
| F-06 | Inactive column | ✅ two columns |
| F-07 | Last known IP | ✅ |
| F-08 | Route change log | ✅ |
| F-09 | ICMP | ✅ raw (Linux) or traceroute |
| F-10 | CLI options | ✅ |

## Future

See backlog on branch **`beta`**.

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

- **Raw ICMP** — Linux only (`AF_INET` / `AF_INET6`); `probe: auto` keeps IPv6 literals on subprocess trace (V6-044); `probe: raw` uses raw v6 with `cap_net_raw`.
- **Hostname AAAA** — trace: OS resolve; expert ping `-6`: AAAA via `HostAddressResolver` (V6-055).

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
| **raw-icmp** (Linux) | `RawIcmpRouteProbe` | Linux | JNA + `CAP_NET_RAW`; v4 and v6 literal with `probe: raw` |
| **auto** | `RouteProbeFactory` | Linux + cap → raw for v4/hostname, process for v6 literal |

CLI: `--probe auto|process|raw` (default: `auto`).

### Poll modes (`probe_mode`, P13-001…050)

Orthogonal to transport `probe:` — this is the **monitoring strategy**, not the trace backend.

| `probe_mode` | Call | Per poll | Per-hop RTT/loss | Route change |
|--------------|------|----------|------------------|--------------|
| **`trace`** | `RoutePoller.pollHostRoute` → `RouteProbe.trace()` | Full trace of all hops | Yes (expert/default ping) | Full path compare |
| **`mtr`** | `RoutePoller.pollHostMtr` → `MtrProbe` | **One hop** (TTL probe) | Yes, incremental | DISCOVERING grows by prefix; burst only on reroute |
| **`ping_only`** | `RoutePoller.pollHostPingOnly` | Ping to target | Target only | No hops |

YAML (profile + per-host override):

```yaml
profiles:
  default:
    probe_mode: trace          # trace | mtr | ping_only
    interval: 30.0
    max_concurrent_traces: 3
    hosts:
      - address: "8.8.8.8"
        probe_mode: ping_only  # optional override
```

Legacy `ping_only: true` on a host → `probe_mode: ping_only` (deprecated flag; kept for backward compat).

See [ADR_PROBE_MODES.md](ADR_PROBE_MODES.md).

#### MTR vs full trace — known limitations (P13-050)

| Topic | `trace` | `mtr` |
|-------|---------|-------|
| Load per cycle | All hops (subprocess or raw ICMP) | One hop per `MtrProbe.poll()` call |
| Transport | Depends on `probe:` (`auto`/`process`/`raw`) | `IcmpMtrHopProber` / platform ping — **not** external `mtr` subprocess |
| Route discovery | Full path immediately | **DISCOVERING** phase: hop 1→N until target reached |
| Windows | Slow `tracert` (3 probes/hop) | Lighter per-hop ping; `ping_only` recommended on Windows |
| `probe: raw` | Raw ICMP trace (Linux) | MTR does **not** use `RawIcmpRouteProbe` |
| Expert ping | After trace (chain/target) | After MTR snapshot when expert is configured |
| Concurrency | `max_concurrent_traces` cap | No cap (light probes) |
| Interval (default) | Profile `interval` (30–300 s) | 10 s; `ping_only` — 1.5 s (`HostPollSchedule`) |
| Burst after reroute | Yes (`BurstSchedulePolicy`) | No on prefix growth (real reroute only) |

**Not MTR:** parsing output from external `mtr`/`mtr.exe` — out of scope (see parser table below). In-process state machine — see `MtrProbe`, `MtrProbeState`.

> **Performance:** on **Windows** subprocess trace via `tracert` is orders of magnitude slower than Linux `traceroute` (`-q 1`). Starter preset `config/hosts.windows.example.yaml` (`probe_mode: ping_only`, `interval: 60`). See [DEPLOYMENT.md](DEPLOYMENT.md#os-recommendation).

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
| **Mixed trace formats** | Classic vertical traceroute/tracert only; external **mtr** / JSON output — out of scope (`probe_mode: mtr` ≠ subprocess mtr) |

### RawIcmpRouteProbe (JNA, Linux)

Incremental TTL/hop limit 1..N via raw ICMP socket (IPv4 `IP_TTL`, IPv6 `IPV6_UNICAST_HOPS`).

Requires: `sudo setcap cap_net_raw+ep` on JDK binary or run as root. IPv6 literal with `probe: raw`; with `probe: auto` — subprocess `traceroute -6`.

## Monitor layer (P13-020…030)

`MonitorService` — **0.25 s** scheduler tick + probe thread pool:

1. `cycle()` — **due** hosts only (`HostPollSchedule`, per-host `lastPollAt`).
2. `dispatchDueHost()` — `TraceConcurrencyLimiter` for `probe_mode: trace`.
3. `pollHostOnce()` branches on `resolveProbeMode(host)`:
   - **trace** → `pollHostRoute`
   - **mtr** → `pollHostMtr`
   - **ping_only** → `pollHostPingOnly`
4. `BurstSchedulePolicy` — ×0.25 interval for 5 min after reroute (not MTR prefix growth).
5. Expert/default ping enrich for trace/mtr; callbacks + persistence + alerts.

Store/history/change detection — `SessionStore`, `RouteHistory`, `RouteChangeDetector`.

## UI layer

`MainController` (JavaFX):

- **About** / **Help** (F1) menu — `AppMenuDialogs`
- **Trace profile** selection (ComboBox + new/delete); all profiles in one YAML
- **“Expert”** checkbox → **Exten.** / **MTU** on host row → `PingExpertDialog` (catalog from `pingMan.txt`, without `-c/-w/-W/-i` etc.); 4 quick presets from `ping_presets.yaml` (MTU probe, DF, DSCP, Burst); **MTU wizard…** (`MtuDiscoveryDialog`); **Self-check** (`PresetSelfCheckUi`)
- **Settings → Telemetry…** — `TelemetrySettingsDialog` + bus via `TelemetryAttachment`
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
    probe_mode: trace
    max_concurrent_traces: 3
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

Tests and CI — on **`main`** and **`beta`** (ROADMAP development on `beta`).

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

The linear ROADMAP queue: **NEXT = P21-002** (phase 20 — GUI UX). Details — [docs/en/ROADMAP.md](ROADMAP.md) § NEXT.

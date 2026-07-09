> **Language:** English · [Українська](../DEPLOYMENT.md)

# PINGUI Deployment (Java)

## Platforms

| OS | Support |
|----|---------|
| Linux (Ubuntu 22.04+) | ✅ |
| Windows 11+ | ✅ |
| macOS 12+ | ✅ |

Step-by-step checklist: **[CHECKLIST.md](CHECKLIST.md)**.

Expert ping (“Expert” mode) — **Linux only** (iputils `ping`).

**Dual-stack (phase 9, `beta` / **0.2.0**):** YAML and session accept IPv6 literals (RFC 5952). IPv6 literal → subprocess `traceroute -6` with `probe: auto` (**Linux/macOS**); `probe: raw` + `cap_net_raw` — raw ICMPv6 on Linux. Windows Python trace for v6 literals is not supported yet.

## OS recommendation

| Platform | For daily use | Notes |
|----------|---------------|-------|
| **Linux** | ✅ **Recommended** | `traceroute -q 1` — fast; Expert ping; raw ICMP |
| **macOS** | ✅ Good | Fast trace; Expert ping unavailable |
| **Windows** | ⚠ Limited | **Slow `tracert`**: 3 probes per hop, ~4 s wait per probe → full trace 1–4+ min to target at 20 hops. Expert ping unavailable |

**Windows is not the best choice** for continuous route monitoring with a short `interval` (e.g. 1 s): the next cycle often starts before the previous trace finishes.

**How to mitigate on Windows:**

- In the GUI: **Ping only** checkbox on the host (RTT to target only, no hops).
- In YAML: `ping_only: true` or `interval: 30` (or higher) for trace mode.
- Expect a 1–4 minute delay for the first full trace to a remote target.

## Requirements

| Component | Version |
|-----------|---------|
| JDK | **21** (not Java 25 as the Gradle launcher) |
| traceroute | Linux/macOS |
| tracert | Windows (built-in) |
| Display | X11 / Wayland / Windows Desktop |

## Launch

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh gradlew
export PINGUI_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # if needed
./pingui-java.sh --build
./pingui-java.sh
```

**Windows**

```bat
cd java
pingui-java.bat --build
pingui-java.bat
```

## Build and packaging

```bash
cd java
./gradlew build          # compile + jar
./pingui-java.sh --package   # jpackage: .deb / .dmg / .msi
```

## Raw ICMP (Linux, optional)

By default `traceroute`/`tracert` is used. Raw ICMP (`probe: auto|raw`) on **Linux** opens a raw `AF_INET` (ICMPv4) socket — requires:

```bash
sudo setcap cap_net_raw+ep "$(readlink -f "$(which java)")"
```

Restart PINGUI after `setcap`. The same capability is required for Python raw ICMP (scapy); it applies to the process opening the raw socket, not the venv itself.

### IPv6 and `cap_net_raw` (dual-stack, `beta`)

| Trace/ping path | Target | `cap_net_raw`? | Notes |
|-----------------|--------|----------------|-------|
| `probe: auto` / `raw` | IPv4 literal, hostname (A) | **Yes** (raw) | Without cap → fallback to `traceroute` |
| `probe: auto` | IPv6 literal | **No** | Always subprocess `traceroute -6` (Java and Python) |
| Expert ping `-6` | IPv6 literal / `-6` | **No** | iputils `ping`, not a raw socket |
| Raw ICMPv6 (`probe: raw`) | IPv6 literal | **Yes** | Java `beta`: `LinuxJnaIcmpTransport` + `IcmpV6Packet`; `auto` keeps process trace |

**Current behaviour:** even with `cap_net_raw` on the JDK, **IPv6 literals never use raw ICMP** — only process trace. The capability affects v4/hostname in `auto|raw` mode.

**Verify cap (Linux):**

```bash
getcap "$(readlink -f "$(which java)")"
# expected: cap_net_raw=ep
```

If raw v4 cannot open, PINGUI logs a socket error and (`probe: auto`) falls back to `traceroute`.

## Configuration

Example: `java/config/hosts.example.yaml`. Format v2:

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
```

## Python NOC (headless, `beta` branch)

No Qt — background monitoring for servers / NOC:

```bash
cd /path/to/PINGUI
./pingui.sh --deploy   # venv + parity (first time)
.venv/bin/python -m pingui monitor --config config/hosts.example.yaml --session-db data/ping.db
```

Daemon with PID file (PY-030…032):

```bash
.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui daemon --alert-webhook https://hooks.example.com/pingui --session-db data/ping.db
.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid
.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid
```

systemd example: `systemd/pingui.service.example` (Type=simple, `ExecStart=... daemon`).

## SQLite session persistence (Java / Python)

| What | Where |
|------|-------|
| DB file | `--session-db PATH`, YAML `persistence.session_db`, or GUI **Settings → Database…** |
| Route metrics | `host_session` table (JSON: routes, `ping_history`, `hop_stats`) |
| Events | `persistence_event` table (`route_change`, `probe_error`) |

**Writing to the DB:** after connecting SQLite, enable the target checkbox in the host list — legacy YAML defaults to `enabled: false`; without active monitoring, route and `hop_stats` are not updated. `host_session` rows appear on connect; `current_route_json` / `hop_stats` after the first successful poll.

**Disk and retention (P11-050):**

- No automatic TTL / rotation for `host_session` — the file grows with host count and accumulated `ping_history` / `hop_stats` (in-memory caps: up to 50 RTT samples per hop in `hop_stats`, ping history per IP).
- `persistence_event` rows are removed only manually: GUI **Database…** → disable event type → **Delete** (purge confirm).
- Full reset: delete the `.db` file or remove the host row via the UI.
- Headless report: `./pingui-java.sh -- --session-db data/ping.db --export-report report.csv`
- Typical size: a few–tens of KB per host on a NOC profile; monitor `du -h data/ping.db` on long-running daemons.

Schema details: [SPIKE_PERSISTENCE.md](../SPIKE_PERSISTENCE.md).

## Troubleshooting

| Symptom | Solution |
|---------|----------|
| SQLite empty / no route data | Connect DB (CLI/YAML/GUI), **enable host checkbox**, wait for a poll; check: `sqlite3 data/ping.db "SELECT host, enabled, length(current_route_json) FROM host_session;"` |
| Trace on Windows “hangs” / very long | Normal for `tracert`; enable **Ping only** or increase `interval` |
| Gradle “What went wrong: 25.0.3” | JDK 21: `export PINGUI_JAVA_HOME=.../java-21-openjdk-*` |
| “No hops parsed” | Install `traceroute`; on macOS — `/usr/sbin/traceroute` |
| JavaFX runtime missing | `./gradlew run` or jpackage installer |
| Expert ping without RTT | Linux + `iputils-ping` |
| `dbind-WARNING` / `accessibility bus` | Harmless without a11y; do not run via `sudo`; or `NO_AT_BRIDGE=1` (default in `pingui-java.sh`) |
| IPv6 trace “no hops” | `traceroute -6` on PATH; raw cap **not** required for v6 literal |
| Raw ICMP v4 “permission denied” | `setcap cap_net_raw+ep` on JDK binary (see § Raw ICMP) |

## Development

Tests, CI, Python edition — branch **`beta`**.

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

**IPv4 trace/raw ICMP; dual-stack config (V6-S1):** YAML and session accept IPv6 literals (RFC 5952, e.g. `2001:db8::1` or `[::1]`). Subprocess trace and raw ICMP for v6 — phase 9.2+ ([ROADMAP.md](../ROADMAP.md)); probing remains IPv4-oriented for now.

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

By default `traceroute`/`tracert` is used. Raw ICMP (`probe: auto|raw`):

```bash
sudo setcap cap_net_raw+ep "$(readlink -f "$(which java)")"
```

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

## Troubleshooting

| Symptom | Solution |
|---------|----------|
| Trace on Windows “hangs” / very long | Normal for `tracert`; enable **Ping only** or increase `interval` |
| Gradle “What went wrong: 25.0.3” | JDK 21: `export PINGUI_JAVA_HOME=.../java-21-openjdk-*` |
| “No hops parsed” | Install `traceroute`; on macOS — `/usr/sbin/traceroute` |
| JavaFX runtime missing | `./gradlew run` or jpackage installer |
| Expert ping without RTT | Linux + `iputils-ping` |

## Development

Tests, CI, Python edition — branch **`beta`**.

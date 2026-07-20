> **Language:** English · [Українська](../CHECKLIST.md)

# PINGUI Deployment Checklist (Java)

Checklist for the Java edition on **Linux / Windows / macOS**.

**Expert ping** (“Expert” mode, **Exten.** button) — **Linux + iputils-ping only**.

Python edition and tests are in **both** branch trees; ROADMAP work lands on **`beta`**.

Details: [JAVA.md](JAVA.md), [DEPLOYMENT.md](DEPLOYMENT.md).

### Java daemon smoke (P12)

- [ ] `./pingui-java.sh -- --daemon --config config/hosts.example.yaml --session-db data/ping.db --pid-file /tmp/pingui-java.pid` (hosts `enabled: true` in YAML)
- [ ] `./pingui-java.sh -- --status --pid-file /tmp/pingui-java.pid` → `running pid=…`
- [ ] `sqlite3 data/ping.db "SELECT host FROM host_session;"` — rows after poll
- [ ] `./pingui-java.sh -- --daemon --alert-webhook URL …` — route change → POST (webhook log)
- [ ] `--api-port 8080` / `--metrics-port 9090`: `curl http://127.0.0.1:8080/hosts` and `curl http://127.0.0.1:9090/metrics`; TLS — see [DEPLOYMENT § reverse proxy](DEPLOYMENT.md#reverse-proxy--tls-p15-041)
- [ ] `./pingui-java.sh -- --stop --pid-file /tmp/pingui-java.pid`

### Python daemon smoke

- [ ] `./pingui.sh --deploy` — venv + doc parity
- [ ] `.venv/bin/python -m pingui monitor --config config/hosts.example.yaml` — foreground headless (Ctrl+C)
- [ ] `.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid`
- [ ] `.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid` → running
- [ ] `.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid`

### Python IPv6 smoke (Linux/macOS)

- [ ] `traceroute -6` installed (`which traceroute`)
- [ ] YAML with `::1` or `2001:db8::1`: `load_hosts_config` succeeds
- [ ] `.venv/bin/python -c "from pingui.icmp.tracer import trace_route; print(trace_route('::1', max_hops=3))"` — ≥1 hop
- [ ] GeoIP v6: `country_code_for_ip('2001:4860:4860::8888')` → `US` (from `config/geoip_hints.yaml`)

### Java IPv6 UI smoke

- [ ] Help (F1) mentions IPv4/IPv6 literals
- [ ] Add target `2001:db8::1` — normalized in log; invalid `not:valid:ipv6` → error in journal
- [ ] Route graph hop with v6 shows bracketed IP `[2001:4860:4860::8888]`

### Java IPv6 process trace smoke (Linux, V6-070)

- [ ] `traceroute -6 ::1` — ≥1 hop (`traceroute` in PATH)
- [ ] YAML profile with `2001:db8::1` — trace without «No hops parsed»
- [ ] `./gradlew test --tests io.pingui.probe.ProcessRouteProbeTest` — `unix_v6_*` green
- [ ] Ping-only on v6 literal — RTT updates without crash
- [ ] `./gradlew test --tests io.pingui.probe.ProcessRouteProbeTest.v4FixturesRemainGreen` — v4 regression (CI)

### Java IPv6 smoke (Windows, optional — V6-071)

- [ ] `tracert -6 ::1` completes (slow; Ping only recommended for production)
- [ ] `./gradlew test --tests io.pingui.probe.ProcessRouteProbeTest.parseWindowsIpv6*` — `win_v6_*` green

### Java IPv6 raw ICMP smoke (Linux optional, V6-040…043)

- [ ] `./gradlew test --tests io.pingui.probe.icmp.IcmpV6PacketTest` — packet build/parse (CI)
- [ ] `./gradlew test --tests io.pingui.probe.icmp.LinuxCLibraryTest` — `sockaddr_in6` layout (Linux CI)
- [ ] `./gradlew test --tests io.pingui.probe.RawIcmpRouteProbeTest.traceIpv6UsesHopLimitSequence` — hop-limit trace (CI)
- [ ] YAML `probe: raw` + `cap_net_raw` + target `::1` — trace without crash (manual)

### Python alert smoke

- [ ] `python -m pingui monitor --alert-webhook http://127.0.0.1:9/hook` — starts without crash (unreachable webhook → log)
- [ ] `python -m pingui run --desktop-alerts` — GUI + notify-send on route change (Linux)
- [ ] `python -m pingui daemon --alert-webhook URL --session-db data/ping.db` — route change → POST JSON

### Java alert smoke (Linux)

- [ ] `./gradlew test --tests io.pingui.monitor.WebhookAlertDispatcherTest` — contract POST JSON (CI)
- [ ] `./gradlew test --tests io.pingui.monitor.AlertRateLimiterTest` — burst rate limit (CI)
- [ ] `./pingui-java.sh --alert-webhook http://127.0.0.1:9/hook` — starts without crash (unreachable webhook → WARNING)
- [ ] `./pingui-java.sh --desktop-alerts` — GUI + `notify-send` on route change (requires `libnotify-bin`)
- [ ] YAML `alerts.webhook` / `alert_webhook` in profile — route change → POST without CLI override

### Java telemetry smoke (P16-071)

Sink fields: [CONFIGURATION § Telemetry](CONFIGURATION.md#telemetry-p16-040052). LOG-server: [DEPLOYMENT § LOG-server](DEPLOYMENT.md#log-server-p16-061). Unit coverage: `TelemetrySinkInstallerTest`, `TelemetryAttachmentTest`, `DaemonRunnerTest.startRegistersSqliteAndSyslogFromTelemetryConfig`, `SqliteTelemetrySinkTest`, `SyslogSinkTest`, `AppMenuDialogsTest`. Desktop GUI uses the same `TelemetryAttachment` (P16-090); GUI smoke below (P16-094).

**Prepare a profile** (copy of `java/config/hosts.example.yaml` or a temp YAML): host `enabled: true`; in the profile:

```yaml
telemetry:
  events_only: true
  sqlite: data/telemetry.db
  syslog:
    host: 127.0.0.1
    port: 1514
    tls: false
```

- [ ] CI: `cd java && ./gradlew test --tests io.pingui.TelemetrySinkInstallerTest --tests io.pingui.daemon.DaemonRunnerTest.startRegistersSqliteAndSyslogFromTelemetryConfig` — green
- [ ] Terminal A (mock syslog TCP): `nc -l 1514 | tee /tmp/pingui-syslog.log` (or rsyslog from DEPLOYMENT)
- [ ] Terminal B: `./pingui-java.sh -- --daemon --config <yaml> --session-db data/ping.db --pid-file /tmp/pingui-java.pid` (or CLI override `--telemetry-syslog 127.0.0.1:1514`)
- [ ] After first poll (baseline route_change): `sqlite3 data/telemetry.db "SELECT event, host FROM telemetry_event LIMIT 5;"` — has `route_change` (or `probe_error`)
- [ ] In `/tmp/pingui-syslog.log` — RFC 5424 line with JSON `"event":"route_change"` (or `probe_error`); **no** hop-RTT sample flood when `events_only: true`
- [ ] `./pingui-java.sh -- --stop --pid-file /tmp/pingui-java.pid`

### Java GUI telemetry smoke (P16-094)

- [ ] CI: `cd java && ./gradlew test --tests io.pingui.ui.AppMenuDialogsTest --tests io.pingui.TelemetryAttachmentTest --tests io.pingui.ui.TelemetrySettingsDialogTest` — green
- [ ] About (“About PINGUI…”) mentions SQLite session and Telemetry menu (not “RAM only”)
- [ ] Help (F1) has Settings section with `persistence.session_db` ≠ `telemetry.sqlite`
- [ ] GUI: **Settings → Telemetry…** → sqlite `data/telemetry.db`, Apply → log “Телеметрія оновлена”; **Save** → YAML has `telemetry.sqlite`
- [ ] After poll: `sqlite3 data/telemetry.db "SELECT event FROM telemetry_event LIMIT 3;"` — has `route_change` or `probe_error`

---

## Linux (Ubuntu 22.04 / 24.04 / 26.04)

### Preflight

- [ ] x86_64 or arm64
- [ ] GUI: X11 or Wayland
- [ ] Network reachability to monitoring targets
- [ ] JDK **21** (not Java 25 as the Gradle launcher)

### System packages

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk traceroute iputils-ping
```

- [ ] `java -version` → major **21**
- [ ] `traceroute --version` — installed
- [ ] `ping -V` → **iputils** (for Expert ping)

### Installation

```bash
cd /path/to/PINGUI/java
chmod +x pingui-java.sh gradlew
export PINGUI_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # if needed
./pingui-java.sh --build
./pingui-java.sh
```

- [ ] `./pingui-java.sh --build` — no “What went wrong: 25.0.3”
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI opens

### Smoke test

- [ ] Add `8.8.8.8`, enable checkbox
- [ ] Simple: loss %, min/avg/max RTT
- [ ] Extended: graph + change log
- [ ] Save YAML → restart → target persists
- [ ] Expert ON → **Exten.** → `-4 -s 128` → RTT updates
- [ ] Expert ON → **MTU** (or Expert → **MTU wizard…**) → Start → Stop/finish → Alert with MTU → Apply → Expert args `-M do -s …`
- [ ] Expert → Exten. → **Self-check** → Alert for DF/DSCP/Burst (loss%/RTT); Expert form unchanged

### jpackage (.deb)

```bash
cd java && ./pingui-java.sh --package
ls build/dist/*.deb
```

### Troubleshooting

| Symptom | Action |
|---------|--------|
| Gradle “25.0.3” | `export PINGUI_JAVA_HOME=.../java-21-openjdk-*` |
| “No hops parsed” | `sudo apt install traceroute` |
| Expert ping without RTT | check `ping -V` (iputils) |
| Raw ICMP denied | `sudo setcap cap_net_raw+ep` on JDK binary |

---

## Windows 11+

> ⚠ **Warning:** Windows is **not recommended** for intensive route monitoring. `tracert` runs 3 probes per hop with long timeouts; one trace to 20 hops can take **1–4+ minutes**. Expert ping unavailable. For practical use: **Ping only** in the GUI or starter preset `config/hosts.windows.example.yaml` (`probe_mode: ping_only`, `interval: 60`; telemetry P16-043: `events_only: true`, **no** `jsonl_dir`). Recommended platform — **Linux**. [DEPLOYMENT.md#os-recommendation](DEPLOYMENT.md#os-recommendation)

### Preflight

- [ ] Windows 11 x64
- [ ] **JDK 21 installed** (Eclipse Temurin / Microsoft Build of OpenJDK)
- [ ] During install: **Add to PATH** + **Set JAVA_HOME**
- [ ] `tracert` and `ping` — built-in

### Installing JDK 21 (if `java` not found)

1. Download [Eclipse Temurin 21 (Windows x64)](https://adoptium.net/temurin/releases/?version=21)
2. Installer → enable **Add to PATH**, **Set JAVA_HOME**
3. New cmd window:

```bat
java -version
```

Should show `openjdk version "21.x.x"`.

If PATH did not update:

```bat
set "PINGUI_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
cd C:\path\to\PINGUI\java
pingui-java.bat --build
```

`pingui-java.bat` only passes `JAVA_HOME`/`PINGUI_JAVA_HOME` to `gradlew.bat` (no JDK search in cmd — the old search broke on paths with spaces).

### Building PINGUI

```bat
cd C:\path\to\PINGUI\java
pingui-java.bat --build
pingui-java.bat --config config/hosts.windows.example.yaml
```

- [ ] `java -version` → **21**
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI (JavaFX) opens with `hosts.windows.example.yaml` (ping_only, interval 60)

### Smoke test

- [ ] Start with `config/hosts.windows.example.yaml` (P13-040 + P16-043 preset)
- [ ] Target `8.8.8.8`, checkbox enabled
- [ ] **Ping only** ON (preset) → RTT within a few seconds (no wait for full trace)
- [ ] Telemetry: YAML has `events_only: true`, no `jsonl_dir` / high-freq sqlite (no hop-RTT JSONL)
- [ ] Or trace OFF + ping only OFF: first trace — **up to 4 min** (normal for `tracert`)
- [ ] Simple / Extended — metrics and graph
- [ ] YAML save/load
- [ ] “Expert” **disabled**

### jpackage (.msi)

- [ ] WiX Toolset v3+ in PATH
- [ ] `pingui-java.bat --package` → `build\dist\*.msi`

---

## macOS (Intel / Apple Silicon)

### Preflight

- [ ] macOS 12+
- [ ] JDK **21** (`/usr/libexec/java_home -v 21`)
- [ ] `traceroute` (Java resolves `/usr/sbin/traceroute` automatically)
- [ ] Xcode Command Line Tools (jpackage)

### Installation

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
cd /path/to/PINGUI/java
chmod +x pingui-java.sh gradlew
./pingui-java.sh --build
./pingui-java.sh
```

- [ ] `java -version` → **21**
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI opens

### Smoke test

- [ ] Trace in GUI — ≥ 1 hop
- [ ] Simple / Extended, YAML save/load
- [ ] “Expert” **disabled**

---

## Universal smoke test

1. `[launcher] --build`
2. Launch GUI
3. `8.8.8.8` + checkbox → 10–30 s
4. Simple: RTT/loss; Extended: graph
5. Save config → restart
6. **Linux only:** Expert → Exten. → `-4 -s 128`
7. **Linux only:** Expert → **MTU** → wizard Start/Apply (not the «MTU probe» preset)
8. **Linux only:** Expert → Exten. → **Self-check** → Alert DF/DSCP/Burst
9. Optional: **Settings → Telemetry…** — see § GUI telemetry smoke (P16-094)

---

## § GUI smoke (B-035, after UI-split)

Run on **Linux** (regression for “black frame” after profile CRUD):

- [ ] **About** (menu) — version dialog opens without hanging
- [ ] **F1 / Help** — help dialog opens
- [ ] **New profile** → name `test` → host list empty, window without black bars
- [ ] **Delete profile** (return to default) → Simple mode, window shrinks correctly (no oversized frame left)
- [ ] **Extended** → graph + log; **Simple** → compact layout again
- [ ] **P20-004 Route diff:** Extended — route change → rows with `~`/`+`/`−` and color
- [ ] **P20-003 Dirty/unsaved:** mutate host → «Save *»; profile switch → Confirm; Save clears *
- [ ] **P20-002 Confirm delete:** Delete host / profile → Confirm; Cancel is a no-op
- [ ] **P20-001 Simple feedback:** failed add host (duplicate) → status + Alert; Extended fail → log only (no Alert); live «Last update…» in Extended is not overwritten by feedback info
- [ ] Add `8.8.8.8` → **Save** → restart `./pingui-java.sh` → target and profile persist
- [ ] Profile switching in ComboBox — hosts update

---

## § CLI interval (M-014)

Verify that YAML `interval` is not overwritten without CLI:

1. In active profile YAML: `interval: 30.0`
2. Launch **without** `--interval`: `./pingui-java.sh --config /path/to/hosts.yaml`
3. Expect polling ~30 s between cycles (not ~1 s)

Automated contract: `PinguiApplicationTest.m014_yamlInterval30_noCliOverride_preservesInterval`.

---

## § Release / jpackage smoke (B-061)

After version bump or before release tag:

- [ ] `cd java && ./gradlew check` — green (including `layerCheck`)
- [ ] `./pingui-java.sh --build` — SUCCESS
- [ ] `./pingui-java.sh --package` (Linux: `.deb`) — artifact in `build/dist/`
- [ ] Installer runs; GUI opens
- [ ] **About** — version includes git sha (`AppInfo.versionDetail()`)
- [ ] Smoke from § GUI smoke (B-035) on target OS

---

## § Docs smoke (B-062, weekly / before release)

Verify README ↔ actual CLI alignment:

- [ ] `java/README.en.md` — flags `--config`, `--interval`, `--probe`, `--geoip-hints` match `./pingui-java.sh --help`
- [ ] `java/README.md` — same CLI parity (UK)
- [ ] `docs/en/JAVA.md` — CLI vs YAML table is current
- [ ] `docs/JAVA.md` — UK parity with EN
- [ ] `docs/en/DEPLOYMENT.md` — JDK 21, Windows warning, dual-stack
- [ ] `docs/` ↔ `docs/en/` — language switchers and content parity (bilingual smoke)
- [ ] `python3 scripts/check_doc_parity.py` — green (CI gate)
- [ ] `docs/ROADMAP.md` — closed tasks marked `[x]`
- [ ] CI badge in `README.md` — shows latest push

---

## Capabilities by platform

| Feature | Linux | Windows | macOS |
|---------|-------|---------|-------|
| Trace (tracert/traceroute) | ✅ | ✅ | ✅ |
| Expert ping | ✅ | ❌ | ❌ |
| Raw ICMP (`probe: raw`) | ✅ | ❌ | ❌ |

Unit tests: `cd java && ./gradlew check` (JUnit 5); Python — `.venv/bin/pytest` (both branches; develop on **`beta`**).

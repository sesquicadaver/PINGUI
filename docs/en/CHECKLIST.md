> **Language:** English · [Українська](../CHECKLIST.md)

# PINGUI Deployment Checklist (Java)

Checklist for the Java edition on **Linux / Windows / macOS**.

**Expert ping** (“Expert” mode, **Exten.** button) — **Linux + iputils-ping only**.

Python edition and tests — branch **`beta`**.

Details: [JAVA.md](JAVA.md), [DEPLOYMENT.md](DEPLOYMENT.md).

### Python daemon smoke (beta)

- [ ] `./pingui.sh --deploy` — venv + doc parity
- [ ] `.venv/bin/python -m pingui monitor --config config/hosts.example.yaml` — foreground headless (Ctrl+C)
- [ ] `.venv/bin/python -m pingui daemon --session-db data/ping.db --pid-file /tmp/pingui.pid`
- [ ] `.venv/bin/python -m pingui status --pid-file /tmp/pingui.pid` → running
- [ ] `.venv/bin/python -m pingui stop --pid-file /tmp/pingui.pid`

### Python alert smoke (beta)

- [ ] `python -m pingui monitor --alert-webhook http://127.0.0.1:9/hook` — starts without crash (unreachable webhook → log)
- [ ] `python -m pingui run --desktop-alerts` — GUI + notify-send on route change (Linux)
- [ ] `python -m pingui daemon --alert-webhook URL --session-db data/ping.db` — route change → POST JSON

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

> ⚠ **Warning:** Windows is **not recommended** for intensive route monitoring. `tracert` runs 3 probes per hop with long timeouts; one trace to 20 hops can take **1–4+ minutes**. Expert ping unavailable. For practical use: **Ping only** in the GUI or `ping_only: true` / `interval: 30` in YAML. Recommended platform — **Linux**. [DEPLOYMENT.md#os-recommendation](DEPLOYMENT.md#os-recommendation)

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
pingui-java.bat
```

- [ ] `java -version` → **21**
- [ ] `./gradlew build` — SUCCESS
- [ ] GUI (JavaFX) opens

### Smoke test

- [ ] Target `8.8.8.8`, checkbox enabled
- [ ] **Ping only** ON → RTT within a few seconds (no wait for full trace)
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

---

## § GUI smoke (B-035, after UI-split)

Run on **Linux** (regression for “black frame” after profile CRUD):

- [ ] **About** (menu) — version dialog opens without hanging
- [ ] **F1 / Help** — help dialog opens
- [ ] **New profile** → name `test` → host list empty, window without black bars
- [ ] **Delete profile** (return to default) → Simple mode, window shrinks correctly (no oversized frame left)
- [ ] **Extended** → graph + log; **Simple** → compact layout again
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

Unit tests: `cd java && ./gradlew check` (JUnit 5 on `main`; Python tests — branch **`beta`**).

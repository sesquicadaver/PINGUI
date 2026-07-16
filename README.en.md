# PINGUI

> **Language:** English · [Українська](README.md)

![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)
![Python CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/ci.yml/badge.svg)

Cross-platform route and RTT monitor for up to 10 targets simultaneously (Java 21 + JavaFX).
By default session data lives **in RAM**; optional **SQLite** (`--session-db`, GUI Database settings), alerts, route history, headless **daemon**, dual-stack **IPv6**, and the Python edition are in **both** branch trees after merges from `beta`.

> **OS recommendation:** use **Linux** for daily work — fastest tracing and full feature set (Expert ping, raw ICMP). **Windows** is supported, but `tracert` is slow (3 probes per hop, seconds of wait each); a full trace to 20 hops can take **minutes**. On Windows prefer **Ping only** or `interval` ≥ 30 s. Details: [docs/en/DEPLOYMENT.md](docs/en/DEPLOYMENT.md#os-recommendation).

## Branches `main` and `beta`

| | **`main`** | **`beta`** |
|---|------------|------------|
| **Role** | Stable snapshot after merge from `beta` (production) | Active development (`/autopilot` only when NEXT ≠ DONE) |
| **Java desktop** | ✅ GUI + Pro (IPv6, SQLite, alerts, history, daemon, export, telemetry, Expert MTU) as of last merge | ✅ Same **plus** any work until merge; linear ROADMAP queue **P19-005** (phase 19) |
| **Session** | RAM by default; optional **SQLite** | Same |
| **Alerts / history / daemon / IPv6** | ✅ (after phases 9–12+ merged) | ✅ + newer work until merge |
| **Python PyQt6** | ✅ `src/pingui/`, `./pingui.sh`, pytest (may lag `beta` slightly) | ✅ Latest Python stack |
| **CI** | Java `gradlew check` + Python pytest | Same |
| **Documentation** | Synced on merge | Living docs ahead of `main` until merge |

**Which to use:** **`main`** — last stable merge for production; **`beta`** — day-to-day development and newest features (until merged to `main`). The gap is **how far `beta` is ahead**, not “`main` lacks SQLite/alerts/Python”. Plan: [docs/en/ROADMAP.md](docs/en/ROADMAP.md).

```bash
git clone https://github.com/sesquicadaver/PINGUI.git
cd PINGUI
git checkout beta    # or main
```

## Quick start

**Linux / macOS**

```bash
git clone https://github.com/sesquicadaver/PINGUI.git
cd PINGUI/java
chmod +x pingui-java.sh gradlew
./pingui-java.sh --build
./pingui-java.sh
```

**Windows**

> ⚠ **Not ideal for intensive monitoring.** `tracert` is much slower than Linux `traceroute` (3 probes/hop, long timeouts). First trace may take 1–4 min per target; Expert ping unavailable. On Windows: **Ping only** on host or `interval: 30`+ in profile. See [docs/en/DEPLOYMENT.md](docs/en/DEPLOYMENT.md#os-recommendation).

Requires **JDK 21**: [Eclipse Temurin 21 (Windows x64)](https://adoptium.net/temurin/releases/?version=21) — enable **Add to PATH** and **Set JAVA_HOME** during install.

```bat
cd PINGUI\java
pingui-java.bat --build
pingui-java.bat
```

Requirements: **JDK 21** ([Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)), `traceroute` (Linux/macOS) or `tracert` (Windows).

## Features

- Up to **10 targets**, checkbox = active tracing; **Ping only** = target RTT without trace
- **Tracing profiles** in one YAML (`active_profile` + `profiles`)
- **Simple** / **Extended** UI modes; loss %, min/avg/max RTT; route graph
- **Expert ping** (Linux, iputils) — **Exten.**: presets, **MTU wizard**, **Self-check** DF/DSCP/Burst
- **Telemetry** — sinks (sqlite/jsonl/syslog/GELF/Loki/OTLP), **Settings → Telemetry…**
- Tracing via `traceroute` / `tracert`; optional raw ICMP on Linux (`probe: auto|raw`)
- Dual-stack IPv6, route-change alerts, SQLite + route history, headless daemon, CSV/HTML export — see [java/README.en.md](java/README.en.md)

## CLI

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --interval 2 --max-hops 15
```

| Option | Description |
|--------|-------------|
| `--config` | YAML with 0–10 targets (v2 `profiles:` or legacy `hosts:`) |
| `--interval` | Override active profile `interval` **only if passed** |
| `--max-hops` | Override active profile `max_hops` **only if passed** |
| `--timeout` | Override active profile `timeout` **only if passed** |
| `--probe` | Override active profile `probe` **only if passed** |
| `--session-db` | SQLite metrics + session events |
| `--export-report` | CSV/HTML from `--session-db` without GUI |
| `--daemon` / `--stop` / `--status` | Headless monitor (NOC) |
| `--alert-webhook` / `--desktop-alerts` | Route-change alerts |
| `--geoip-hints` | Offline CIDR→country |
| `--no-geoip` | Disable country in labels |
| `--verbose` | Debug log |

Without `--interval` / `--max-hops` / `--timeout` / `--probe`, values come from the YAML profile. Full CLI (including persistence): [java/README.en.md](java/README.en.md#cli).

## Repository layout

```
PINGUI/
├── java/                 # Java edition (JavaFX + daemon / SQLite / alerts)
├── src/pingui/           # Python edition
├── tests/                # pytest
├── config/               # YAML examples, GeoIP hints
├── systemd/              # pingui-java.service.example
├── docs/
│   ├── en/               # English documentation
│   └── *.md              # Ukrainian documentation
└── CHANGELOG.md
```

## Documentation

| File | Purpose |
|------|---------|
| [docs/en/README.md](docs/en/README.md) | Documentation index (EN) |
| [docs/README.md](docs/README.md) | Індекс документації (UA) |
| [java/README.en.md](java/README.en.md) | Launch, Gradle, GUI, YAML (EN) |
| [java/README.md](java/README.md) | Запуск, Gradle, GUI, YAML (UA) |
| [docs/en/CHECKLIST.md](docs/en/CHECKLIST.md) | Checklist Linux / Windows / macOS |
| [docs/en/DEPLOYMENT.md](docs/en/DEPLOYMENT.md) | Deployment |
| [docs/en/JAVA.md](docs/en/JAVA.md) | Java edition architecture |
| [docs/en/ROADMAP.md](docs/en/ROADMAP.md) | Development plan (`main` / `beta`) |
| [CHANGELOG.md](CHANGELOG.md) | Change history |

Python: `./pingui.sh` (venv; develop on **`beta`**). Java: `cd java && ./gradlew check`. Headless NOC: `./pingui-java.sh -- --daemon --config …`.

## Support the project

If this project is useful to you, you may support its development with a voluntary donation in USDT.

Donations are optional and do not provide ownership, equity, tokens, governance rights, paid support, priority service, or any investment return.

### USDT donations

| Network | Address |
|---|---|
| USDT ERC-20 / Ethereum | 0xfa9821efd142228d53e1418fe335bb1cd8ff3c39 |
| USDT TRC-20 / Tron | TNnhueeGqujf6AAUhcgissoEkL7tdzmqQv |

### Important

Please make sure that the selected network matches the address type.

- Send **USDT ERC-20** only to the Ethereum address.
- Send **USDT TRC-20** only to the Tron address.

Transactions sent to the wrong network may be permanently lost.

Thank you for supporting the project.

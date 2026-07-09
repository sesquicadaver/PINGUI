# PINGUI

> **Language:** English · [Українська](README.md)

![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)
![Python CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/ci.yml/badge.svg)

Cross-platform route and RTT monitor for up to 10 targets simultaneously (Java 21 + JavaFX).
By default session data lives **in RAM**; on branch **`beta`**, Java also supports optional **SQLite** (`--session-db`, GUI Database settings) and a headless **daemon**.

> **OS recommendation:** use **Linux** for daily work — fastest tracing and full feature set (Expert ping, raw ICMP). **Windows** is supported, but `tracert` is slow (3 probes per hop, seconds of wait each); a full trace to 20 hops can take **minutes**. On Windows prefer **Ping only** or `interval` ≥ 30 s. Details: [docs/en/DEPLOYMENT.md](docs/en/DEPLOYMENT.md#os-recommendation).

## Branches `main` and `beta`

| | **`main`** | **`beta`** |
|---|------------|------------|
| **Role** | Stable line for daily Java GUI | Development: new ROADMAP phases, Python edition |
| **Java desktop** | ✅ GUI, profiles, trace/tracert, Expert ping (Linux) | ✅ Everything on `main` **plus** phases 9–12 (see below) |
| **Session** | RAM only | RAM or **SQLite** (metrics + `route_change` / `probe_error`) |
| **Alerts** | — | Webhook + desktop alerts on route change (P10) |
| **Route history** | — | “Route history” panel + graph replay (P11) |
| **Headless NOC** | — | `--daemon`, `--stop`, `--status`, systemd example (P12) |
| **Dual-stack IPv6** | Limited / without phase 9 | ✅ Java + Python (phase 9) |
| **Python PyQt6** | Code in repo, **without** full `beta` parity | ✅ `./pingui.sh`, pytest, timeseries/export |
| **CI** | Java `gradlew check` | Java + Python pytest |
| **Documentation** | UK + EN index | Full set + ADR/SPIKE (P10–P13) |

**Which to use:** production GUI on a stable base — `main`; SQLite, alerts, history, daemon, IPv6, and Python — **`beta`**. Full plan: [docs/en/ROADMAP.md](docs/en/ROADMAP.md).

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
- **Expert ping** (Linux, iputils) — **Exten.** dialog per host
- Tracing via `traceroute` / `tracert`; optional raw ICMP on Linux (`probe: auto|raw`)
- **`beta`:** dual-stack IPv6, route-change alerts, SQLite + route history, headless daemon, CSV/HTML export — see [java/README.en.md](java/README.en.md)

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
| `--session-db` | *(beta)* SQLite metrics + session events |
| `--export-report` | *(beta)* CSV/HTML from `--session-db` without GUI |
| `--daemon` / `--stop` / `--status` | *(beta)* headless monitor (NOC) |
| `--alert-webhook` / `--desktop-alerts` | *(beta)* route-change alerts |
| `--geoip-hints` | Offline CIDR→country |
| `--no-geoip` | Disable country in labels |
| `--verbose` | Debug log |

Without `--interval` / `--max-hops` / `--timeout` / `--probe`, values come from the YAML profile. Full CLI (including persistence): [java/README.en.md](java/README.en.md#cli).

## Repository layout

```
PINGUI/
├── java/                 # Java edition (JavaFX + optional daemon on beta)
├── src/pingui/           # Python edition (full cycle on beta)
├── tests/                # pytest (beta)
├── config/               # YAML examples, GeoIP hints
├── systemd/              # pingui-java.service.example (beta)
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

Python: `./pingui.sh` on branch **`beta`** (venv). Java: `cd java && ./gradlew check`. Headless NOC on **`beta`**: `./pingui-java.sh -- --daemon --config …`.

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

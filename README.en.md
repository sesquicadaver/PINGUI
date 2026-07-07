> **Language:** [Ukrainian](README.md) · English

# PINGUI

![Java CI](https://github.com/sesquicadaver/PINGUI/actions/workflows/java.yml/badge.svg)

Cross-platform route and RTT monitor for up to 10 targets simultaneously (Java 21 + JavaFX).
Data is stored **in RAM only** for the session.

> **OS recommendation:** use **Linux** for daily work — fastest tracing and full feature set (Expert ping, raw ICMP). **Windows** is supported, but `tracert` is slow (3 probes per hop, seconds of wait each); a full trace to 20 hops can take **minutes**. On Windows prefer **Ping only** or `interval` ≥ 30 s. Details: [docs/en/DEPLOYMENT.md](docs/en/DEPLOYMENT.md#os-recommendation).

| Branch | Contents |
|--------|----------|
| **`main`** | Java edition + docs + unit tests + JaCoCo + CI |
| **`beta`** | Python + Java, pytest, JaCoCo, full CI, specs |

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

- Up to **10 targets**, checkbox = active tracing
- **Tracing profiles** in one YAML (`active_profile` + `profiles`)
- **Simple** / **Extended** UI modes; loss %, min/avg/max RTT
- **Expert ping** (Linux, iputils) — **Exten.** dialog per host
- Tracing via `traceroute` / `tracert` (no `CAP_NET_RAW` by default)
- Optional on Linux: raw ICMP (`probe: auto|raw` + `cap_net_raw`)
- **Dual-stack config:** IPv6 literals in YAML (RFC 5952); subprocess trace v6 — phase 9 ([docs/en/DEPLOYMENT.md](docs/en/DEPLOYMENT.md))

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
| `--geoip-hints` | Offline CIDR→country |
| `--no-geoip` | Disable country in labels |
| `--verbose` | Debug log |

Without `--interval` / `--max-hops` / `--timeout` / `--probe`, values come from the YAML profile.

## Repository layout

**`main`:** Java + docs. **`beta`:** + `src/pingui/` (Python), `tests/`, `pyproject.toml`, `pingui.sh`.

```
PINGUI/
├── java/                 # Java edition (JavaFX)
├── src/pingui/           # Python edition (beta)
├── tests/                # pytest (beta)
├── docs/
│   ├── en/               # English documentation
│   └── …                 # Ukrainian documentation
└── CHANGELOG.md
```

## Documentation

| File | Purpose |
|------|---------|
| [java/README.en.md](java/README.en.md) | Launch, Gradle, GUI, YAML |
| [docs/en/README.md](docs/en/README.md) | Documentation index (EN) |
| [docs/README.md](docs/README.md) | Індекс документації (UA) |
| [docs/en/CHECKLIST.md](docs/en/CHECKLIST.md) | Checklist Linux / Windows / macOS |
| [docs/en/DEPLOYMENT.md](docs/en/DEPLOYMENT.md) | Deployment |
| [docs/en/JAVA.md](docs/en/JAVA.md) | Java edition architecture |
| [docs/en/ROADMAP.md](docs/en/ROADMAP.md) | Fix plan |
| [CHANGELOG.en.md](CHANGELOG.en.md) | Change history |

Python: `./pingui.sh` on branch **`beta`** (venv). Java: `cd java && ./gradlew check`.

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

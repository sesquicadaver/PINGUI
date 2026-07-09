> **Language:** English · [Українська](../README.md)

# PINGUI documentation

On **`beta`** — full package (Java phases 9–12 + Python). On **`main`** — stable Java GUI without P10–P12; see the Branches section in [README.en.md](../README.en.md).

## Navigation

| Document | Audience | Content |
|----------|----------|---------|
| [CHECKLIST.md](CHECKLIST.md) | Admin / DevOps | Checklist Linux / Windows / macOS |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Admin / DevOps | JDK 21, venv, cap_net_raw, systemd |
| [JAVA.md](JAVA.md) | Developer | Probe, monitor, UI, YAML |
| [ROADMAP.md](ROADMAP.md) | Developer | Official plan: phases 0–16 (Pro/NOC + telemetry) |
| [LIVING_SPEC.md](LIVING_SPEC.md) | Developer | Module → tests matrix |
| [ADR_IPV6.md](ADR_IPV6.md) | Developer | Dual-stack config ADR (V6-002) |
| [ADR_ALERTS.md](ADR_ALERTS.md) | Developer | Route-change alerts ADR (P10-001) |
| [ADR_DAEMON.md](ADR_DAEMON.md) | Developer | Headless daemon ADR (P12-001) |
| [ADR_PROBE_MODES.md](ADR_PROBE_MODES.md) | Developer | Probe modes trace/mtr/ping_only ADR (P13-001) |
| [SPIKE_IPV6.md](SPIKE_IPV6.md) | Developer | IPv6 scope (**planned**, phase 9 V6-*) |
| [SPIKE_PERSISTENCE.md](SPIKE_PERSISTENCE.md) | Developer | SQLite session schema (phase 11 P11-*) |
| [CONFIGURATION.md](CONFIGURATION.md) | Developer | YAML, CLI (Python) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Developer | Python layers |
| [MODULES.md](MODULES.md) | Developer | Module reference |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Developer | venv, local cycle |
| [TESTING.md](TESTING.md) | QA | pytest, coverage, CI |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contributor | PR, anti-stub |
| [MVP_SPEC.md](MVP_SPEC.md) | All | MVP requirements |
| [USER_GUIDE.md](USER_GUIDE.md) | User | End user guide |
| [../java/README.md](../java/README.md) | All | Java launcher, Gradle |
| [../.github/workflows/java.yml](../.github/workflows/java.yml) | CI | Java `./gradlew check` |
| [../.github/workflows/ci.yml](../.github/workflows/ci.yml) | CI | Python pytest (beta) |

## Recommended order

**Java:** [../README.md](../README.md) → CHECKLIST → [../java/README.md](../java/README.md)

**Python (beta):** DEVELOPMENT → TESTING → CONFIGURATION

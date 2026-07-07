# PINGUI Documentation

> **Language:** English · [Українська](../README.md)

On branch **`beta`** — full package (Java + Python). On **`main`** — Java docs for deployment.

## Navigation

| Document | Audience | Contents |
|----------|----------|----------|
| [CHECKLIST.md](CHECKLIST.md) | Admin / DevOps | Linux / Windows / macOS checklist |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Admin / DevOps | JDK 21, venv, cap_net_raw, systemd |
| [JAVA.md](JAVA.md) | Developer | Probe, monitor, UI, YAML |
| [ROADMAP.md](ROADMAP.md) | Developer | Fix plan (closed items) |
| [LIVING_SPEC.md](LIVING_SPEC.md) | Developer | Module → tests matrix |
| [ADR_IPV6.md](ADR_IPV6.md) | Developer | Dual-stack config ADR (V6-002) |
| [SPIKE_IPV6.md](SPIKE_IPV6.md) | Developer | IPv6 scope (**planned**, phase 9 V6-*) |
| [CONFIGURATION.md](CONFIGURATION.md) | Developer | YAML, CLI (Python) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Developer | Python layers |
| [MODULES.md](MODULES.md) | Developer | Module reference |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Developer | venv, local cycle |
| [TESTING.md](TESTING.md) | QA | pytest, coverage, CI |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contributor | PR, anti-stub |
| [MVP_SPEC.md](MVP_SPEC.md) | All | MVP requirements |
| [USER_GUIDE.md](USER_GUIDE.md) | User | GUI guide (Python) |
| [../java/README.en.md](../../java/README.en.md) | All | Java launcher, Gradle |
| [../.github/workflows/java.yml](../../.github/workflows/java.yml) | CI | Java `./gradlew check` |
| [../.github/workflows/ci.yml](../../.github/workflows/ci.yml) | CI | Python pytest (beta) |

## Recommended order

**Java:** [../../README.en.md](../../README.en.md) → CHECKLIST → [../../java/README.en.md](../../java/README.en.md)

**Python (beta):** DEVELOPMENT → TESTING → CONFIGURATION

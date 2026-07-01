# Документація PINGUI

На **`beta`** — повний пакет (Java + Python). На **`main`** — Java docs для запуску.

## Навігація

| Документ | Для кого | Зміст |
|----------|----------|-------|
| [CHECKLIST.md](CHECKLIST.md) | Адмін / DevOps | Checklist Linux / Windows / macOS |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Адмін / DevOps | JDK 21, venv, cap_net_raw, systemd |
| [JAVA.md](JAVA.md) | Розробник | Probe, monitor, UI, YAML |
| [ROADMAP.md](ROADMAP.md) | Розробник | План виправлень (закрито) |
| [LIVING_SPEC.md](LIVING_SPEC.md) | Розробник | Матриця модуль → тести |
| [SPIKE_IPV6.md](SPIKE_IPV6.md) | Розробник | IPv6 scope (**planned**, фаза 9 V6-*) |
| [CONFIGURATION.md](CONFIGURATION.md) | Розробник | YAML, CLI (Python) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Розробник | Шари Python |
| [MODULES.md](MODULES.md) | Розробник | Довідник модулів |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Розробник | venv, локальний цикл |
| [TESTING.md](TESTING.md) | QA | pytest, coverage, CI |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Контрибутор | PR, anti-stub |
| [MVP_SPEC.md](MVP_SPEC.md) | Усі | MVP вимоги |
| [../java/README.md](../java/README.md) | Усі | Java launcher, Gradle |
| [../.github/workflows/java.yml](../.github/workflows/java.yml) | CI | Java `./gradlew check` |
| [../.github/workflows/ci.yml](../.github/workflows/ci.yml) | CI | Python pytest (beta) |

## Рекомендований порядок

**Java:** [../README.md](../README.md) → CHECKLIST → [../java/README.md](../java/README.md)

**Python (beta):** DEVELOPMENT → TESTING → CONFIGURATION

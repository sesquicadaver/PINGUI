> **Мова:** Українська · [English](en/README.md)

# Документація PINGUI

На **`beta`** — повний пакет (Java фази 9–12 + Python). На **`main`** — стабільний Java GUI без P10–P12; див. розділ «Гілки» у [README.md](../README.md).

## Навігація

| Документ | Для кого | Зміст |
|----------|----------|-------|
| [CHECKLIST.md](CHECKLIST.md) | Адмін / DevOps | Checklist Linux / Windows / macOS |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Адмін / DevOps | JDK 21, venv, cap_net_raw, systemd |
| [JAVA.md](JAVA.md) | Розробник | Probe, monitor, UI, YAML |
| [ROADMAP.md](ROADMAP.md) | Розробник | Офіційний план: фази 0–16 (Pro/NOC + telemetry) |
| [LIVING_SPEC.md](LIVING_SPEC.md) | Розробник | Матриця модуль → тести |
| [ADR_IPV6.md](ADR_IPV6.md) | Розробник | Dual-stack config ADR (V6-002) |
| [ADR_ALERTS.md](ADR_ALERTS.md) | Розробник | Route-change alerts ADR (P10-001) |
| [ADR_DAEMON.md](ADR_DAEMON.md) | Розробник | Headless daemon ADR (P12-001) |
| [ADR_PROBE_MODES.md](ADR_PROBE_MODES.md) | Розробник | Probe modes trace/mtr/ping_only ADR (P13-001) |
| [SPIKE_IPV6.md](SPIKE_IPV6.md) | Розробник | IPv6 scope (**planned**, фаза 9 V6-*) |
| [SPIKE_PERSISTENCE.md](SPIKE_PERSISTENCE.md) | Розробник | SQLite session schema (фаза 11 P11-*) |
| [CONFIGURATION.md](CONFIGURATION.md) | Розробник | YAML, CLI (Python) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Розробник | Шари Python |
| [MODULES.md](MODULES.md) | Розробник | Довідник модулів |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Розробник | venv, локальний цикл |
| [TESTING.md](TESTING.md) | QA | pytest, coverage, CI |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Контрибутор | PR, anti-stub |
| [MVP_SPEC.md](MVP_SPEC.md) | Усі | MVP вимоги |
| [USER_GUIDE.md](USER_GUIDE.md) | Користувач | Керівництво користувача |
| [../java/README.md](../java/README.md) | Усі | Java launcher, Gradle |
| [../.github/workflows/java.yml](../.github/workflows/java.yml) | CI | Java `./gradlew check` |
| [../.github/workflows/ci.yml](../.github/workflows/ci.yml) | CI | Python pytest (beta) |

## Рекомендований порядок

**Java:** [../README.md](../README.md) → CHECKLIST → [../java/README.md](../java/README.md)

**Python (beta):** DEVELOPMENT → TESTING → CONFIGURATION → USER_GUIDE

## English

Повний англомовний набір: [en/README.md](en/README.md)

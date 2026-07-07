# Документація PINGUI

> **Мова:** Українська · [English](en/README.md)

На **`beta`** — повний пакет (Java + Python). На **`main`** — Java docs для запуску.

## Навігація

| Документ | Для кого | Зміст |
|----------|----------|-------|
| [CHECKLIST.md](CHECKLIST.md) | Адмін / DevOps | Checklist Linux / Windows / macOS |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Адмін / DevOps | JDK 21, venv, cap_net_raw, systemd |
| [JAVA.md](JAVA.md) | Розробник | Probe, monitor, UI, YAML |
| [ROADMAP.md](ROADMAP.md) | Розробник | План виправлень (закрито) |
| [LIVING_SPEC.md](LIVING_SPEC.md) | Розробник | Матриця модуль → тести |
| [ADR_IPV6.md](ADR_IPV6.md) | Розробник | Dual-stack config ADR (V6-002) |
| [SPIKE_IPV6.md](SPIKE_IPV6.md) | Розробник | IPv6 scope (**planned**, фаза 9 V6-*) |
| [CONFIGURATION.md](CONFIGURATION.md) | Розробник | YAML, CLI (Python) |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Розробник | Шари Python |
| [MODULES.md](MODULES.md) | Розробник | Довідник модулів |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Розробник | venv, локальний цикл |
| [TESTING.md](TESTING.md) | QA | pytest, coverage, CI |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Контрибутор | PR, anti-stub |
| [MVP_SPEC.md](MVP_SPEC.md) | Усі | MVP вимоги |
| [USER_GUIDE.md](USER_GUIDE.md) | Користувач | Керівництво користувача |
| [../java/README.md](../java/README.md) | Усі | Java launcher, Gradle (UK) |
| [../java/README.en.md](../java/README.en.md) | Усі | Java launcher, Gradle (EN) |
| [en/README.md](en/README.md) | Усі | English documentation index |
| [../.github/workflows/java.yml](../.github/workflows/java.yml) | CI | Java `./gradlew check` |
| [../.github/workflows/ci.yml](../.github/workflows/ci.yml) | CI | Python pytest (beta) |

## Рекомендований порядок

**Java:** [../README.md](../README.md) → CHECKLIST → [../java/README.md](../java/README.md)

**Python (beta):** DEVELOPMENT → TESTING → CONFIGURATION → USER_GUIDE

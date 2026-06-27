# Документація PINGUI

Повний пакет документації для Linux desktop-монітора маршрутів і RTT.

## Швидка навігація

| Документ | Для кого | Зміст |
|----------|----------|-------|
| [USER_GUIDE.md](USER_GUIDE.md) | Користувач | GUI, чекбокси, збереження списку, граф |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Адмін / DevOps | `pingui.sh`, venv, cap_net_raw, systemd |
| [CONFIGURATION.md](CONFIGURATION.md) | Адмін / розробник | YAML, CLI, змінні середовища |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Розробник | Шари, потоки даних, Qt-сигнали |
| [MODULES.md](MODULES.md) | Розробник | Довідник модулів і публічних API |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Розробник | venv, стиль коду, локальний цикл |
| [TESTING.md](TESTING.md) | Розробник / QA | pytest, coverage, маркери, CI |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Контрибутор | PR, anti-stub, Living Spec |
| [MVP_SPEC.md](MVP_SPEC.md) | Усі | Функціональні вимоги MVP |
| [LIVING_SPEC.md](LIVING_SPEC.md) | Усі | Матриця вимога → модуль → тести |
| [JAVA.md](JAVA.md) | Усі | Java cross-platform edition |
| [../java/README.md](../java/README.md) | Усі | Запуск Java, Gradle, CLI |

## Документи в корені репозиторію

| Файл | Призначення |
|------|-------------|
| [../README.md](../README.md) | Швидкий старт і огляд |
| [../ROADMAP.md](../ROADMAP.md) | Фази розробки та backlog |
| [../CHANGELOG.md](../CHANGELOG.md) | Історія змін |

## Рекомендований порядок читання

**Новий користувач:** README → USER_GUIDE → DEPLOYMENT

**Розробник:** README → ARCHITECTURE → DEVELOPMENT → TESTING → MODULES

**Cross-platform (Java):** README → [JAVA.md](JAVA.md) → [../java/README.md](../java/README.md)

**Рев'ю PR:** CONTRIBUTING → LIVING_SPEC → TESTING

## Версія та стан

- **Python MVP:** завершено (PyQt6, Linux, до 10 цілей)
- **Java edition:** MVP (`java/`, Java 21 + JavaFX, traceroute/tracert)
- **Python coverage gate:** ≥ 80%

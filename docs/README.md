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

## Документи в корені репозиторію

| Файл | Призначення |
|------|-------------|
| [../README.md](../README.md) | Швидкий старт і огляд |
| [../ROADMAP.md](../ROADMAP.md) | Фази розробки та backlog |
| [../CHANGELOG.md](../CHANGELOG.md) | Історія змін |

## Рекомендований порядок читання

**Новий користувач:** README → USER_GUIDE → DEPLOYMENT

**Розробник:** README → ARCHITECTURE → DEVELOPMENT → TESTING → MODULES

**Рев'ю PR:** CONTRIBUTING → LIVING_SPEC → TESTING

## Версія та стан

- **Версія пакету:** 0.1.0 (`pyproject.toml`)
- **MVP:** завершено (in-memory, PyQt6, до 10 цілей)
- **Coverage gate:** ≥ 80% (`fail_under` у `pyproject.toml`)

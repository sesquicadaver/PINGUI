# Документація PINGUI

Документація для запуску та експлуатації (гілка **main**).

Повний цикл розробки, тести, CI та специфікації — гілка **`beta`** (див. [README](../README.md)).

## Швидка навігація

| Документ | Для кого | Зміст |
|----------|----------|-------|
| [USER_GUIDE.md](USER_GUIDE.md) | Користувач | GUI, чекбокси, збереження списку, граф |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Адмін / DevOps | `pingui.sh`, venv, cap_net_raw, systemd |
| [CHECKLIST.md](CHECKLIST.md) | Адмін / DevOps | Checklist Linux / Windows / macOS |
| [CONFIGURATION.md](CONFIGURATION.md) | Адмін / розробник | YAML, CLI, змінні середовища |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Розробник | Шари, потоки даних, Qt-сигнали |
| [MODULES.md](MODULES.md) | Розробник | Довідник модулів і публічних API |
| [JAVA.md](JAVA.md) | Усі | Java cross-platform edition |
| [../java/README.md](../java/README.md) | Усі | Запуск Java, Gradle, CLI |

## Документи в корені репозиторію

| Файл | Призначення |
|------|-------------|
| [../README.md](../README.md) | Швидкий старт і огляд |
| [../CHANGELOG.md](../CHANGELOG.md) | Історія змін |

## Рекомендований порядок читання

**Новий користувач:** README → USER_GUIDE → DEPLOYMENT → CHECKLIST

**Архітектура:** README → ARCHITECTURE → MODULES

**Cross-platform (Java):** README → [CHECKLIST.md](CHECKLIST.md) → [JAVA.md](JAVA.md) → [../java/README.md](../java/README.md)

## Версія та стан

- **Python:** PyQt6, Linux, до 10 цілей
- **Java edition:** Java 21 + JavaFX, traceroute/tracert

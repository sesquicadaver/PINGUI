# Документація PINGUI (Java)

Документація для запуску Java-редакції на гілці **`main`**.

Python-редакція, тести, CI та специфікації — гілка **`beta`**.

## Навігація

| Документ | Для кого | Зміст |
|----------|----------|-------|
| [CHECKLIST.md](CHECKLIST.md) | Адмін / DevOps | Checklist Linux / Windows / macOS |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Адмін / DevOps | JDK 21, launcher, jpackage |
| [JAVA.md](JAVA.md) | Розробник | Probe, monitor, UI, YAML |
| [ROADMAP.md](ROADMAP.md) | Розробник | План виправлень, атомарні задачі |
| [LIVING_SPEC.md](LIVING_SPEC.md) | Розробник | Матриця модуль → тести |
| [ADR_IPV6.md](ADR_IPV6.md) | Розробник | Dual-stack config ADR (V6-002) |
| [SPIKE_IPV6.md](SPIKE_IPV6.md) | Розробник | IPv6 scope (**planned**, фаза 9 V6-*) |
| [../.github/workflows/java.yml](../.github/workflows/java.yml) | CI | Java check (Ubuntu + optional Windows) |
| [../java/README.md](../java/README.md) | Усі | Запуск, CLI, GUI |

## Рекомендований порядок

**Новий користувач:** [../README.md](../README.md) → CHECKLIST → [../java/README.md](../java/README.md)

**Розробник (beta):** перейти на гілку `beta` → [ROADMAP.md](ROADMAP.md) → `docs/DEVELOPMENT.md`, `docs/TESTING.md`

## Версія

- **Java 21 + JavaFX**, Linux / Windows / macOS
- До 10 цілей, in-memory session
- **Linux** — рекомендована ОС; **Windows** — повільний `tracert`, див. [DEPLOYMENT.md#рекомендація-щодо-ос](DEPLOYMENT.md#рекомендація-щодо-ос)

> **Мова:** Українська · [English](en/ADR_DAEMON.md)

# ADR: Java headless daemon (P12-001)

**Дата:** 2026-07-09  
**Статус:** accepted  
**Гілка (історично):** прийнято на `beta`; реалізація на `main` і `beta` після merge.

## Контекст

NOC-профілі потребують моніторингу маршруту без JavaFX (сервер, systemd). Python має `daemon`/`stop` (PY-030); Java до P12 запускав лише GUI або `--export-report`.

## Рішення

| Аспект | Політика |
|--------|----------|
| Процес | `MonitorService` + `SessionStore` у foreground; головний потік блокується до SIGTERM |
| CLI | `--daemon`, `--pid-file PATH`, `--stop`, `--status` |
| PID-файл | Запис PID після старту; видалення в shutdown hook |
| Конфіг | `--config` + YAML; CLI overrides як у GUI |
| SQLite | `--session-db` або YAML `persistence.session_db` |
| Alerts | `--alert-webhook`, `--desktop-alerts` (Linux) — як P10 |
| Reload | **SIGHUP → reload config** — out of scope v1 (немає ID у поточній черзі; deferred) |

## Альтернативи

1. **Окремий JAR без JavaFX** — відхилено: один artifact, умовний `main` branch.
2. **Fork + detach (double-fork)** — відхилено: systemd `Type=simple` достатньо; ускладнює логи.

## Наслідки

- `PinguiApplication.main` розгалужується: export / daemon / stop / status / GUI.
- `DaemonRunner` імпортує `ui.MonitorLifecycle` (дозволено — не нижній шар).
- Документація: `systemd/pingui-java.service.example`, DEPLOYMENT § Java daemon.

## Follow-ups

- SIGHUP reload `ProfilesConfig` без перезапуску — deferred (немає ID у поточній лінійній черзі).
- PY parity: `python -m pingui daemon` залишається окремим шляхом.

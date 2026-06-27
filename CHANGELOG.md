# Changelog

Формат базується на [Keep a Changelog](https://keepachangelog.com/uk/1.1.0/).
Версіонування: [Semantic Versioning](https://semver.org/lang/uk/).

## [Unreleased]

### Added

- **Java edition** (`java/`): cross-platform desktop monitor on Java 21 + JavaFX.
  - `ProcessRouteProbe` via traceroute/tracert (no cap_net_raw).
  - Gradle build, `pingui-java.sh`, JUnit tests, GitHub Actions `java-ci.yml`.
  - Docs: `java/README.md`, `docs/JAVA.md`.

### Added (prior)

- Повний пакет документації у `docs/` (архітектура, розгортання, тести, API).

## [0.1.0] - 2026-06-26

### Added

- Linux desktop GUI (PyQt6) для моніторингу до 10 цілей.
- ICMP traceroute через scapy (TTL 1..N, RTT, timeout hop `*`).
- Фоновий `LightweightMonitorWorker` (QThread); трасування лише enabled цілей.
- In-memory `SessionStore`: маршрути, ping history, previous route, last known IP.
- `GraphCanvas`: вертикальний граф, дві колонки (inactive / active).
- GUI CRUD: Додати, Змінити, Видалити, Зберегти → YAML.
- Єдина точка входу `pingui.sh` (--deploy, --destroy, GUI).
- CLI: `--config`, `--interval`, `--max-hops`, `--timeout`, `--verbose`.
- CI: GitHub Actions, ruff, mypy, pytest, coverage ≥ 80%.
- Скрипти: `ci_venv.sh`, `check_caps.sh`, `setup_caps.sh`, `check_imports.py`.
- Документи: README, ROADMAP, MVP_SPEC, LIVING_SPEC.

### Changed

- Замість `scripts/deploy.sh` — `pingui.sh` у корені.
- Тихий запуск GUI (logging ERROR, фільтр Qt messages).

### Removed

- Застарілий `scripts/run_dev.sh`.
- Тимчасові ТЗ-файли `1.txt`, `2.txt`, `3.txt` (замінені на `docs/MVP_SPEC.md`).
- Артефакти `.omx/` з git (локальний workspace).

### Fixed

- Coverage gate для `./pingui.sh --deploy` (~90% після розширення тестів).

[Unreleased]: https://github.com/sesquicadaver/PINGUI/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/sesquicadaver/PINGUI/releases/tag/v0.1.0

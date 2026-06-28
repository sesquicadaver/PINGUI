# Changelog

Формат базується на [Keep a Changelog](https://keepachangelog.com/uk/1.1.0/).
Версіонування: [Semantic Versioning](https://semver.org/lang/uk/).

## [Unreleased]

### Changed

- **Гілки:** `main` — лише Java-редакція та документація для запуску; `beta` — Python, тести, CI, специфікації.

### Added

- **Docs:** [docs/CHECKLIST.md](docs/CHECKLIST.md) — checklist розгортання Linux / Windows / macOS.

### Fixed

- **Java (Windows):** `pingui-java.bat` спрощено до обгортки над `gradlew.bat` (без пошуку JDK у cmd).
- **Java (Windows):** `pingui-java.bat` відмовляє без JDK 21 (parity з `pingui-java.sh`).
- **Java UI:** режим «Експерт» disabled на Windows/macOS (Expert ping — лише Linux iputils).
- **CLI (Python):** некоректний `--geoip-hints` і недоступний Timescale DSN → `Config error` замість traceback.
- **CLI (Java):** некоректні числові опції, config і GeoIP hints → `Config error` на stderr.

### Added

- **Java UI:** кілька профілів трасування в одному YAML (`ProfilesConfig`); режим «Експерт» з діалогом параметрів `ping(8)` на хост (кнопка Exten., chain-wide ping, валідація сумісності flags).
- **Java UI:** режими «Простий» / «Розширений»; у простому — метрики кінцевого hop (loss, min/avg/max ms) і постійна кольорова індикація рядка.

### Changed

- **Гілки:** `main` — робочий код і документація; `beta` — повний репозиторій розробки (тести, CI, specs).
- **Розділення launcherів:** `pingui.sh` — лише Python; Java — `java/pingui-java.sh` (Unix) / `java/pingui-java.bat` (Windows).

### Added

- **J-06 Java hop stats:** jitter/loss у graph labels (parity з B-06).
- **B-06 Hop stats:** jitter (RTT stdev) і loss % по hop у graph labels, SQLite persistence, CSV/HTML export.
- **B-05 Time-series:** optional InfluxDB/Timescale backends for RTT samples and route events (`--ts-backend`, `persistence/timeseries/`).
- **B-04 Folium geo-map:** вкладка «Карта» з folium-маркерами hop-ів (country centroids), CLI `--no-geo-map`.
- **B-03 GeoIP:** offline CIDR→country hints у підписах hop-нод (Python `geoip/country`, Java `GeoCountry`), CLI `--geoip-hints` / `--no-geoip`, `config/geoip_hints.yaml`.
- **Java parity sprint:** JavaFX `GraphCanvas` (vertical route graph, dual columns, ping colors).
- Unit tests: `SessionStore`, `RouteHistory`, `MonitorService`, `PingColor`, `RouteGraphLayout`.
- `--verbose` → `LoggingSetup` (SLF4J levels).

### Added (prior)
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

# Changelog

> **Мова:** Українська · [English](docs/en/README.md#documentation) *(English docs index)*

Формат базується на [Keep a Changelog](https://keepachangelog.com/uk/1.1.0/).
Версіонування: [Semantic Versioning](https://semver.org/lang/uk/).

## [Unreleased]

### Added

- **Alerts ADR (P10-001):** `docs/ADR_ALERTS.md` — channels (webhook, desktop), `RouteChangeEvent` JSON, rate limit, failure policy.
- **Java alerts foundation (P10-010…011):** `RouteChangeEvent`, `AlertDispatcher`, dispatch from `MonitorService` on route change.
- **Java alerts pipeline (P10-020…050):** webhook POST, desktop `notify-send`, YAML `alerts:` + CLI overrides, per-host rate limit, `RouteChangeNotifier`, tests and CHECKLIST smoke.
- **Expert ping default IPv4 (V6-054):** single AF mode (`-4` or `-6`); default `-4` for hostname/IPv4; no dual-stack expert ping.
- **Expert ping IPv6 resolve (V6-055):** hostname targets resolve to AAAA when expert ping uses `-6`.
- **Persistence SPIKE (P11-001):** `docs/SPIKE_PERSISTENCE.md` — Java SQLite schema v1 parity + v2 route events.
- **Persistence policy SPIKE (P11-002):** event menu, YAML `persistence.events`, defaults (state+route_change+probe_error), purge confirm, poll-cycle policy.
- **Java SessionDatabase (P11-010):** `io.pingui.persistence.SessionDatabase` — SQLite open/migrate/close, Python-parity `host_session`, `persistence_event` schema v3, unit tests.
- **Java persistence wire (P11-011):** `SessionStore` → `host_session`; `PersistenceEventWriter` + `MonitorService` → `route_change` / `probe_error` events (independent of alerts).
- **Java CLI `--session-db` (P11-012):** `PinguiApplication`, `AppOptions`, `MainController` — optional SQLite path; RAM-only when omitted.
- **Java PersistencePolicy (P11-013):** `PersistencePolicy`, `PersistencePolicyHolder` — gate in `PersistenceEventWriter`; pending policy applies after each poll cycle.
- **Java persistence GUI + config (P11-014…015):** `PersistenceSettingsDialog` (purge confirm), YAML `persistence.events`, CLI `--no-persist-route-change` / `--no-persist-probe-error`.
- **Java route history UI (P11-020…021):** `RouteHistoryPresenter` — 24h/7d `route_change` list; graph replay from selected event.
- **Java session export (P11-030):** `SessionReportExporter` — CSV/HTML from `--session-db`; headless CLI `--export-report PATH` (format by extension).
- **Java GUI SQLite connection (P11-016):** `PersistenceConfig`, `SessionDbResolver` — file picker in «База даних…», YAML `persistence.session_db`, menu active without CLI.
- **Java hop stats from history (P11-040):** `hop_stats` persist to SQLite on every probe; graph labels (`j:`/`loss:`) survive session reopen.
- **SQLite disk/retention docs (P11-050):** `docs/DEPLOYMENT.md` — no auto-TTL on `host_session`, manual event purge, sizing notes.

## [0.2.0] - 2026-07-09

### Added

- **Dual-stack IPv6 (phase 9):** Java + Python — RFC 5952 literals, subprocess `traceroute -6`, GeoIP `prefixes_v6`, dual-stack UI.
- **Python IPv6 (PY-S4):** `config.py`, `process_tracer.py`, `geoip/country.py` v6 longest-prefix.
- **Java raw ICMPv6 (V6-040…043):** `IcmpV6Packet`, dual `LinuxJnaIcmpTransport`, `probe: raw` hop-limit trace.
- **Expert ping v6 (V6-050…053):** auto `-6`, validator conflicts, `-F` flow-label UI gating.
- **Docs:** bilingual UK/EN; DEPLOYMENT `cap_net_raw` matrix (V6-045); CHECKLIST IPv6 + raw ICMP smoke steps.

### Fixed

- **IPv6 literal:** `DualStackRouteProbe` — `probe: auto` uses subprocess trace for v6; raw v6 available with `probe: raw`.
- **Expert/ping v6:** auto `-6` for IPv6 literal; `-4`/`-6` conflict with target type.
- **Expert ping UI:** mutual exclusion for address family and dependent flags in dialog.

### Changed

- **Version:** Java `0.2.0-SNAPSHOT`, Python `0.2.0` on `beta` branch.
- **Tests:** IPv6 fixtures (`unix_v6_*`, `win_v6_*`), `v4FixturesRemainGreen`, `IcmpV6PacketTest`, JaCoCo notes.

## [0.1.0 development notes] *(historical, included in 0.1.0 tag)*

### Changed

- **Tests:** B-064f — `PingExpertValidatorTest` (compatibility/value specs); `ExpertPingEnricherTest` з stub ping; прибрано JaCoCo exclusion для `ExpertPingEnricher`.
- **Tests:** B-064e — `HostEntryTest`; розширено `GeoCountryTest` (YAML validation, 0.0.0.0/0) та `ProfilesConfigTest` (type errors, save max hosts).
- **Tests:** B-064d — `GeoCountryTest` (longest-prefix, loopback/link-local, invalid hints, IPv6); `ProfilesConfigTest` (string host flags, invalid boolean, save round-trip).
- **Tests:** B-064c — розширено `HostsConfigTest`/`ProfileDocumentTest`/`GeoCountryTest`; покриття config/geoip ↑.
- **Java monitor:** `MonitorService` використовує `PingOnlyResolver` (live `SessionStore`) для ping-only режиму.
- **Tests:** B-064 — розширено `MonitorService`/`SessionStore`/`IcmpPacket` unit-тести; прибрано JaCoCo exclusion для `IcmpPacket`.
- **Tests:** B-064 — розширено config/monitor unit-тести; звужено JaCoCo exclusions (RoutePoller, HopStats, HostEntry, ProfileDocument, HostTargetStats, GeoCountry lookup).
- **Гілки:** sync `beta` → `main` — повний Java test suite + JaCoCo ≥80% (Sprint 11).
- **Гілки:** sync `main` → `beta` — Java roadmap sprints 1–9 (UI split, probe refactor, Checkstyle, build metadata; Sprint 10).
- **Java probe:** `ProcessRouteProbe` розділено — `TraceCommandBuilder` (Linux/macOS/Windows), `UnixTraceOutputParser`, `WindowsTraceOutputParser`.
- **Build:** Checkstyle (UnusedImports, RedundantImport) у `./gradlew check` (M-023).
- **Build:** About показує версію + git sha; `generateBuildProperties`; `layerCheck`.
- **IPv6:** SPIKE → wontfix; явна помилка для IPv6 literal (B-050/B-053).
- **CLI:** M-014 unit-тест — YAML `interval: 30` без `--interval`.
- **Java UI:** `MainController` розділено на координатори (~715 → ~387 рядків).
- **Build:** Spotless + `./gradlew check`.
- **Java CLI:** `CliProfileOverrides` — merge лише переданих прапорів.
- **Docs:** IPv4-only, ROADMAP, Windows warning, CHECKLIST smoke sections.
- **Гілки:** `main` — Java + docs + CI; `beta` — Python + Java, pytest, JaCoCo.

### Added

- **CI:** GitHub Actions [Java CI](.github/workflows/java.yml).
- **Tests:** JUnit 5 contract tests + beta JaCoCo coverage gate.
- **Docs:** [docs/LIVING_SPEC.md](docs/LIVING_SPEC.md), [docs/SPIKE_IPV6.md](docs/SPIKE_IPV6.md).
- **Java UI:** меню **Про** та **Довідка** (F1).
- **Java UI:** чекбокс «Ping only» на кожному хості — лише ping до цілі без traceroute (`ping_only` у YAML).
- **Java UI:** кнопка «Exten.» лишається видимою в ping-only при увімкненому «Експерт»; без чекбокса «ланцюжок».
- **Docs:** [docs/CHECKLIST.md](docs/CHECKLIST.md) — checklist розгортання Linux / Windows / macOS.

### Fixed

- **Java:** прибрано дубльований import у `RawIcmpRouteProbe`.
- **Java CLI:** старт без прапорів більше не затирає YAML defaults.
- **Java UI:** після створення/видалення профілю — чорний фрейм (sizeToScene не зменшує вікно на Linux; reload без applyViewMode).
- **Java (Windows):** парсер `tracert` — `<1 ms`, `hostname [IP]`, System32 fallback, `-d`, charset ОС.
- **Java (Windows):** виправлено timeout/deadlock `tracert` (drain stdout, `-w` ≥4000 ms, довший process wait).
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

[Unreleased]: https://github.com/sesquicadaver/PINGUI/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/sesquicadaver/PINGUI/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/sesquicadaver/PINGUI/releases/tag/v0.1.0

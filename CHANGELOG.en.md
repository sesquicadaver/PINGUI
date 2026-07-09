> **Language:** [Ukrainian](CHANGELOG.md) · English

# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.0] - 2026-07-09

### Added

- **Dual-stack IPv6 (phase 9):** Java + Python — RFC 5952 literals, subprocess `traceroute -6`, GeoIP `prefixes_v6`, dual-stack UI.
- **Java raw ICMPv6 (V6-040…043):** `IcmpV6Packet`, dual `LinuxJnaIcmpTransport`, `probe: raw` hop-limit trace.
- **Expert ping v6 (V6-050…053):** auto `-6`, validator conflicts, `-F` flow-label UI gating.
- **Docs:** bilingual UK/EN; DEPLOYMENT `cap_net_raw` matrix; CHECKLIST IPv6 smoke steps.

### Fixed

- **IPv6 literal:** `DualStackRouteProbe` — `probe: auto` uses subprocess trace for v6; raw v6 with `probe: raw`.
- **Expert/ping v6:** auto `-6` for IPv6 literal; `-4`/`-6` conflict with target type.

### Changed

- **Version:** Java `0.2.0-SNAPSHOT`, Python `0.2.0` on `beta`.

## [0.1.0 development notes] *(historical)*

### Fixed

- **Tests:** B-064f — `PingExpertValidatorTest` (compatibility/value specs); `ExpertPingEnricherTest` with stub ping; removed JaCoCo exclusion for `ExpertPingEnricher`.
- **Tests:** B-064e — `HostEntryTest`; extended `GeoCountryTest` (YAML validation, 0.0.0.0/0) and `ProfilesConfigTest` (type errors, save max hosts).
- **Tests:** B-064d — `GeoCountryTest` (longest-prefix, loopback/link-local, invalid hints, IPv6); `ProfilesConfigTest` (string host flags, invalid boolean, save round-trip).
- **Tests:** B-064c — extended `HostsConfigTest`/`ProfileDocumentTest`/`GeoCountryTest`; config/geoip coverage ↑.
- **Java monitor:** `MonitorService` uses `PingOnlyResolver` (live `SessionStore`) for ping-only mode.
- **Tests:** B-064 — extended `MonitorService`/`SessionStore`/`IcmpPacket` unit tests; removed JaCoCo exclusion for `IcmpPacket`.
- **Tests:** B-064 — extended config/monitor unit tests; narrowed JaCoCo exclusions (RoutePoller, HopStats, HostEntry, ProfileDocument, HostTargetStats, GeoCountry lookup).
- **Branches:** sync `beta` → `main` — full Java test suite + JaCoCo ≥80% (Sprint 11).
- **Branches:** sync `main` → `beta` — Java roadmap sprints 1–9 (UI split, probe refactor, Checkstyle, build metadata; Sprint 10).
- **Java probe:** split `ProcessRouteProbe` — `TraceCommandBuilder` (Linux/macOS/Windows), `UnixTraceOutputParser`, `WindowsTraceOutputParser`.
- **Build:** Checkstyle (UnusedImports, RedundantImport) in `./gradlew check` (M-023).
- **Build:** About shows version + git sha; `generateBuildProperties`; `layerCheck`.
- **IPv6:** SPIKE → wontfix; explicit error for IPv6 literal (B-050/B-053).
- **CLI:** M-014 unit test — YAML `interval: 30` without `--interval`.
- **Java UI:** split `MainController` into coordinators (~715 → ~387 lines).
- **Build:** Spotless + `./gradlew check`.
- **Java CLI:** `CliProfileOverrides` — merge only passed flags.
- **Docs:** IPv4-only, ROADMAP, Windows warning, CHECKLIST smoke sections.
- **Branches:** `main` — Java + docs + CI; `beta` — Python + Java, pytest, JaCoCo.

### Added

- **CI:** GitHub Actions [Java CI](.github/workflows/java.yml).
- **Tests:** JUnit 5 contract tests + beta JaCoCo coverage gate.
- **Docs:** [docs/LIVING_SPEC.md](docs/LIVING_SPEC.md), [docs/SPIKE_IPV6.md](docs/SPIKE_IPV6.md).
- **Java UI:** **About** and **Help** menu (F1).
- **Java UI:** **Ping only** checkbox per host — ping to target only without traceroute (`ping_only` in YAML).
- **Java UI:** **Exten.** button stays visible in ping-only when Expert enabled; no chain checkbox.
- **Docs:** [docs/CHECKLIST.md](docs/CHECKLIST.md) — deployment checklist Linux / Windows / macOS.

### Fixed

- **Java:** removed duplicate import in `RawIcmpRouteProbe`.
- **Java CLI:** start without flags no longer overwrites YAML defaults.
- **Java UI:** black frame after profile create/delete (sizeToScene on Linux; reload without applyViewMode).
- **Java (Windows):** `tracert` parser — `<1 ms`, `hostname [IP]`, System32 fallback, `-d`, OS charset.
- **Java (Windows):** fixed `tracert` timeout/deadlock (drain stdout, `-w` ≥4000 ms, longer process wait).
- **Java (Windows):** `pingui-java.bat` fails without JDK 21 (parity with `pingui-java.sh`).
- **Java UI:** Expert mode disabled on Windows/macOS (Expert ping — Linux iputils only).
- **CLI (Python):** invalid `--geoip-hints` and unavailable Timescale DSN → `Config error` instead of traceback.
- **CLI (Java):** invalid numeric options, config and GeoIP hints → `Config error` on stderr.

### Added

- **Java UI:** multiple tracing profiles in one YAML (`ProfilesConfig`); Expert mode with `ping(8)` params dialog per host (Exten. button, chain-wide ping, flag compatibility validation).
- **Java UI:** Simple / Extended modes; in Simple — final hop metrics (loss, min/avg/max ms) and persistent row color.

### Changed

- **Branches:** `main` — working code and docs; `beta` — full dev repo (tests, CI, specs).
- **Launcher split:** `pingui.sh` — Python only; Java — `java/pingui-java.sh` (Unix) / `java/pingui-java.bat` (Windows).

### Added

- **J-06 Java hop stats:** jitter/loss in graph labels (parity with B-06).
- **B-06 Hop stats:** jitter (RTT stdev) and loss % per hop in graph labels, SQLite persistence, CSV/HTML export.
- **B-05 Time-series:** optional InfluxDB/Timescale backends for RTT samples and route events (`--ts-backend`, `persistence/timeseries/`).
- **B-04 Folium geo-map:** «Map» tab with folium hop markers (country centroids), CLI `--no-geo-map`.
- **B-03 GeoIP:** offline CIDR→country hints in hop labels (Python `geoip/country`, Java `GeoCountry`), CLI `--geoip-hints` / `--no-geoip`, `config/geoip_hints.yaml`.
- **Java parity sprint:** JavaFX `GraphCanvas` (vertical route graph, dual columns, ping colors).
- Unit tests: `SessionStore`, `RouteHistory`, `MonitorService`, `PingColor`, `RouteGraphLayout`.
- `--verbose` → `LoggingSetup` (SLF4J levels).

### Added (prior)

- `ProcessRouteProbe` via traceroute/tracert (no cap_net_raw).
- Gradle build, `pingui-java.sh`, JUnit tests, GitHub Actions `java-ci.yml`.
- Docs: `java/README.md`, `docs/JAVA.md`.

### Added (prior)

- Full documentation package in `docs/` (architecture, deployment, tests, API).

## [0.1.0] - 2026-06-26

### Added

- Linux desktop GUI (PyQt6) for monitoring up to 10 targets.
- ICMP traceroute via scapy (TTL 1..N, RTT, timeout hop `*`).
- Background `LightweightMonitorWorker` (QThread); trace enabled targets only.
- In-memory `SessionStore`: routes, ping history, previous route, last known IP.
- `GraphCanvas`: vertical graph, two columns (inactive / active).
- GUI CRUD: Add, Change, Delete, Save → YAML.
- Single entry point `pingui.sh` (--deploy, --destroy, GUI).
- CLI: `--config`, `--interval`, `--max-hops`, `--timeout`, `--verbose`.
- CI: GitHub Actions, ruff, mypy, pytest, coverage ≥ 80%.
- Scripts: `ci_venv.sh`, `check_caps.sh`, `setup_caps.sh`, `check_imports.py`.
- Documents: README, ROADMAP, MVP_SPEC, LIVING_SPEC.

### Changed

- Replaced `scripts/deploy.sh` with `pingui.sh` at repo root.
- Quiet GUI launch (logging ERROR, Qt message filter).

### Removed

- Legacy `scripts/run_dev.sh`.
- Temporary spec files `1.txt`, `2.txt`, `3.txt` (replaced by `docs/MVP_SPEC.md`).
- `.omx/` artifacts from git (local workspace).

### Fixed

- Coverage gate for `./pingui.sh --deploy` (~90% after test expansion).

[Unreleased]: https://github.com/sesquicadaver/PINGUI/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/sesquicadaver/PINGUI/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/sesquicadaver/PINGUI/releases/tag/v0.1.0

> **Мова:** Українська · [English](en/LIVING_SPEC.md)

# Living Specification — PINGUI Java (`main`)

Матриця «модуль → unit-тести». Оновлюй при додаванні фіч.

| Модуль / ТЗ | Клас | Тести |
|-------------|------|-------|
| Парсинг Unix trace | `UnixTraceOutputParser`, `ProcessRouteProbe` | `ProcessRouteProbeTest` (unix + `unix_v6_*`, `v4FixturesRemainGreen`) |
| Парсинг Windows tracert | `WindowsTraceOutputParser`, `ProcessRouteProbe` | `ProcessRouteProbeTest` (win + `win_v6_*` fixtures) |
| Trace argv (OS) | `TraceCommandBuilder`, `TraceTarget`, `LinuxTracerouteCommand`, `MacTracerouteCommand`, `WindowsTracertCommand` | `TraceTargetTest`, `ProcessRouteProbeCommandTest` (v6 `-6`) |
| Валідація хостів IPv4/IPv6 | `HostsConfig`, `HostAddressParser`, `HostEntry` | `HostsConfigTest`, `HostAddressParserTest`, `HostEntryTest` |
| CLI interval vs YAML (M-014) | `CliProfileOverrides`, `ProfilesConfig` | `PinguiApplicationTest.m014_*` |
| Build metadata | `AppInfo`, `generateBuildProperties` | `AppInfoTest` |
| Layer deps (no ui in config) | `scripts/check-layer-deps.sh` | `./gradlew layerCheck` |
| GeoIP hints | `GeoCountry` | `GeoCountryTest` (longest-prefix, LAN/IPv6, invalid YAML, 0.0.0.0/0) |
| YAML profiles v2 + legacy | `ProfilesConfig`, `ProfileDocument` | `ProfilesConfigTest` (host flags, type errors, save max hosts), `ProfileDocumentTest` |
| CLI override профілю | `CliProfileOverrides`, `PinguiApplication` | `PinguiApplicationTest` |
| Monitor polling | `MonitorService`, `RoutePoller`, `ExpertPingEnricher` | `MonitorServiceTest`, `ExpertPingEnricherTest` (stub ping) |
| Session metrics | `SessionStore`, `HostTargetStats` | `SessionStoreTest`, `HopStatsTest` |
| Raw ICMP packet | `IcmpPacket` | `IcmpPacketTest` |
| Expert ping flags | `PingExpertValidator`, `ProcessExpertPing` | `PingExpertValidatorTest` (compatibility, value specs), `ProcessExpertPingTest` |
| GUI / MonitorService | `MainController`, `MonitorService` | *(manual / TestFX — backlog)* |
| UI coordinators | `ProfileUiCoordinator`, `HostListPresenter`, `MonitorLifecycle`, `ViewModeController`, `RouteGraphPresenter` | `./gradlew check`; B-035 manual smoke |
| CI gate | `.github/workflows/java.yml` | `./gradlew check` (Spotless + Checkstyle + layerCheck + test) |
| Doc parity UK/EN | `scripts/check_doc_parity.py` | `python3 scripts/check_doc_parity.py` (CI + `./scripts/ci_venv.sh`) |
| JaCoCo coverage | `build.gradle.kts` `jacocoTestCoverageVerification` | `./gradlew check` (≥80%; tightened exclusions B-064; ExpertPingEnricher included B-064f) |
| Static imports | `config/checkstyle/checkstyle.xml` | `./gradlew checkstyleMain` / `checkstyleTest` |

**Прогін локально:** `cd java && ./gradlew check`

**CI:** push/PR на `main` / `beta` → workflow [Java CI](../.github/workflows/java.yml)

Фікстури trace: `java/src/test/resources/trace/`

---

## Python (`beta`)

Матриця «модуль → тести» для Python-редакції. Оновлюй при додаванні фіч (PY-013).

| Модуль / ТЗ | Клас / модуль | Тести |
|-------------|---------------|-------|
| YAML hosts (IPv4/IPv6) | `config.py` | `test_config.py`, `test_config_resolve.py` |
| ICMP probe + v6 process trace | `icmp/raw_socket.py`, `icmp/tracer.py`, `icmp/process_tracer.py` | `test_raw_socket.py`, `test_process_tracer.py`, `contract/test_tracer.py`, `integration/test_tracer_network.py` |
| Polling / route change | `monitor/polling.py`, `route_change.py` | `test_polling.py`, `test_route_change.py`, `test_route_history.py` |
| Session store | `monitor/session_store.py` | `test_session_store.py`, `contract/test_worker_store.py` |
| Monitor loop | `monitor/monitor_loop.py` | `test_monitor_loop.py` |
| Daemon runner | `monitor/daemon_runner.py` | `test_daemon_runner.py` |
| Alert dispatcher | `monitor/alert_dispatcher.py` | `contract/test_alert_webhook.py` |
| Alert rate limit | `monitor/alert_rate_limiter.py` | `test_alert_rate_limiter.py` |
| Desktop notifier | `monitor/desktop_notifier.py` | `test_desktop_notifier.py` |
| Route change event | `models.py` (`RouteChangeEvent`) | `test_route_change_event.py` |
| Hop stats | `monitor/hop_stats.py` | `test_hop_stats.py` |
| Worker | `monitor/worker.py` | `test_worker.py`, `integration/test_worker_run.py` |
| SQLite persistence | `persistence/session_db.py` | `test_session_db.py` |
| Time-series | `persistence/timeseries/` | `test_timeseries.py` |
| Export reports | `export/session_report.py` | `test_session_export.py`, `test_main_export.py` |
| GeoIP | `geoip/country.py`, `map_builder.py` | `test_geoip_country.py` (v4/v6/LAN), `test_geo_map.py` |
| GUI | `ui/main_window.py`, `graph_canvas.py` | `test_graph_canvas.py`, `integration/test_ui_smoke.py` |
| CLI entry | `__main__.py` | `test_main.py`, `test_main_cli_validation.py`, `test_main_export.py`, `test_main_subcommands.py`, `test_main_dispatch.py` |
| Import graph | `scripts/check_imports.py` | `python scripts/check_imports.py` |
| Doc parity UK/EN | `scripts/check_doc_parity.py` | `test_doc_parity.py`, CI |
| CI gate Python | `scripts/ci_venv.sh`, `.github/workflows/ci.yml` | `./pingui.sh --deploy` або `bash scripts/ci_venv.sh` |

**Прогін локально (Python):** `bash scripts/ci_venv.sh` або `./pingui.sh --deploy`

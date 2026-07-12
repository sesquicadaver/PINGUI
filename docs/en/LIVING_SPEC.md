> **Language:** English · [Українська](../LIVING_SPEC.md)

# Living Specification — PINGUI Java (`main`)

Module → unit test matrix. Update when adding features.

| Module / Spec | Class | Tests |
|---------------|-------|-------|
| Unix trace parsing | `UnixTraceOutputParser`, `ProcessRouteProbe` | `ProcessRouteProbeTest` (unix + `unix_v6_*`, `v4FixturesRemainGreen`) |
| Windows tracert parsing | `WindowsTraceOutputParser`, `ProcessRouteProbe` | `ProcessRouteProbeTest` (win + `win_v6_*` fixtures) |
| Trace argv (OS) | `TraceCommandBuilder`, `TraceTarget`, `LinuxTracerouteCommand`, `MacTracerouteCommand`, `WindowsTracertCommand` | `TraceTargetTest`, `ProcessRouteProbeCommandTest` (v6 `-6`) |
| IPv4/IPv6 host validation | `HostsConfig`, `HostAddressParser`, `HostEntry` | `HostsConfigTest`, `HostAddressParserTest`, `HostEntryTest` |
| CLI interval vs YAML (M-014) | `CliProfileOverrides`, `ProfilesConfig` | `PinguiApplicationTest.m014_*` |
| Build metadata | `AppInfo`, `generateBuildProperties` | `AppInfoTest` |
| Layer deps (no ui in config) | `scripts/check-layer-deps.sh` | `./gradlew layerCheck` |
| GeoIP hints | `GeoCountry` | `GeoCountryTest` (longest-prefix, LAN/IPv6, invalid YAML, 0.0.0.0/0) |
| YAML profiles v2 + legacy | `ProfilesConfig`, `ProfileDocument` | `ProfilesConfigTest` (host flags, type errors, save max hosts), `ProfileDocumentTest` |
| CLI profile override | `CliProfileOverrides`, `PinguiApplication` | `PinguiApplicationTest` |
| Monitor polling | `MonitorService`, `RoutePoller`, `ExpertPingEnricher` | `MonitorServiceTest`, `ExpertPingEnricherTest` (stub ping) |
| MTR per-hop probe (P13-010) | `MtrProbe`, `MtrProbeState`, `RoutePoller.pollHostMtr` | `MtrProbeTest`, `RoutePollerTest.pollHostMtrDetectsIncrementalRouteChange` |
| YAML `probe_mode` (P13-011) | `HostProbeMode`, `ProfilesConfig`, `MonitorService` | `HostProbeModeTest`, `HostEntryProbeModeTest`, `ProfilesConfigTest.loadProbeModeOnProfileAndHost` |
| Smart poll interval (P13-020) | `HostPollSchedule`, `MonitorService`, `HostEntry`, `SessionStore` | `HostPollScheduleTest`, `HostEntryTest.effectiveIntervalUsesModeDefaultsAndOverride`, `ProfilesConfigTest.loadHostIntervalOverride`, `MonitorServiceTest.pollsHostsOnIndependentSchedules` |
| Burst on route change (P13-021) | `BurstSchedulePolicy`, `MonitorService` | `BurstSchedulePolicyTest`, `MonitorServiceTest.acceleratesPollingAfterRouteChange` |
| Trace concurrency cap (P13-030) | `TraceConcurrencyLimiter`, `TracingProfile`, `MonitorService` | `TraceConcurrencyLimiterTest`, `ProfilesConfigTest.loadMaxConcurrentTraces`, `MonitorServiceTest.limitsConcurrentTracePolls`, `MonitorServiceTest.pingOnlyPollsWhileTraceSlotExhausted` |
| Windows preset YAML (P13-040) | `config/hosts.windows.example.yaml`, `ProfilesConfig` | `ProfilesConfigTest.loadWindowsExamplePreset` |
| MTR vs trace docs (P13-050) | `docs/JAVA.md`, `docs/ADR_PROBE_MODES.md` | Docs parity (`check_doc_parity.py`); § probe_mode + MTR limitations in JAVA.md |
| Route diff panel (P14-010) | `RouteDiff`, `RouteDiffPresenter`, `RouteGraphPresenter`, `GraphCanvas` | `RouteDiffTest`, `RouteDiffPresenterTest`, `RouteGraphPresenterTest.liveRedrawShowsDiffAgainstPreviousRoute` |
| Host tags YAML (P14-020) | `HostTags`, `HostEntry`, `ProfilesConfig`, `HostItem`, `MainController` | `HostTagsTest`, `HostEntryTest.withTagsPreservesOtherFields`, `ProfilesConfigTest.loadHostTagsRoundTrip` |
| Tag filter chips + edit (P14-021) | `HostListPresenter`, `HostTagsDialog`, `SessionStore.setTags` | `HostTagsDialogTest`, `HostListPresenterTest`, `SessionStoreTest.setTagsUpdatesSessionAndToHostEntries` |
| ASN hop labels (P14-030) | `AsnLookup`, `AsnInfo`, `IpLiterals`, `PingColor`, `AppOptions` | `AsnLookupTest`, `IpLiteralsTest`, `PingColorTest.nodeLabelUsesAvgPing`, `PinguiApplicationTest` ASN flags |
| rDNS hop labels (P14-031) | `DnsResolver`, `PingColor`, `MainController`, `GraphCanvas` | `DnsResolverTest`, `PingColorTest.nodeLabelIncludesCachedRdns` |
| Expert ping presets (P14-040) | `PingPresets`, `PingPreset`, `PingExpertDialog`, `ping_presets.yaml` | `PingPresetsTest` |
| USER_GUIDE pro/NOC (P14-050) | `docs/USER_GUIDE.md`, `docs/en/USER_GUIDE.md`, `docs/en/DEPLOYMENT.md` | `scripts/check_doc_parity.py` |
| Observability boundaries (P15-001) | `docs/ADR_OBSERVABILITY.md`, `docs/en/ADR_OBSERVABILITY.md` | Docs parity (`check_doc_parity.py`); Prometheus pull vs TS push |
| Prometheus `/metrics` (P15-010) | `PrometheusExporter`, `MetricsHttpServer`, `DaemonRunner`, `MonitorService` | `PrometheusExporterTest`, `MetricsHttpServerTest`, `DaemonRunnerTest.startWithMetricsPortServesPrometheus`, `MonitorServiceTest.updatesPrometheusExporterOnPollAndIsolatesFailures` |
| CLI `--metrics-port` (P15-011) | `PinguiApplication.parseOptions`, `AppOptions.metricsPort` | `PinguiApplicationTest.parseOptions_metricsPort*` |
| Influx/Timescale writer (P15-020) | `persistence/timeseries/*`, `CliTimeSeriesOverrides`, `SessionStore`, `DaemonRunner`, `MainController` | `TimeSeriesBackendsTest`, `SessionStoreTest.forwardsRouteAndPingSamplesToTimeSeriesBackend`, `PinguiApplicationTest.parseOptions_timeSeriesFlags` |
| Python persistence events (PY-P11) | `persistence/policy.py`, `persistence/events.py`, `session_db.py`, `__main__.py` | `test_persistence_events.py` |
| Route-change alerts | `RouteChangeEvent`, `AlertDispatcher`, `AlertDispatchers`, `WebhookAlertDispatcher`, `AlertRateLimiter`, `RouteChangeNotifier` | `RouteChangeEventTest`, `MonitorServiceTest.dispatchesAlertOnRouteChange`, `WebhookAlertDispatcherTest`, `AlertRateLimiterTest`, `AlertDispatchersTest`, `ProfilesConfigTest.loadAlertsSection` |
| Session metrics | `SessionStore`, `HostTargetStats` | `SessionStoreTest`, `HopStatsTest` |
| SQLite session (P11-010) | `SessionDatabase`, `SessionJsonCodec` | `SessionDatabaseTest` |
| Persistence wire (P11-011) | `SessionStore`, `PersistenceEventWriter`, `MonitorService` | `SessionStorePersistenceTest`, `PersistenceEventWriterTest`, `MonitorServiceTest.persistsRouteChangeAndProbeErrorEvents` |
| CLI `--session-db` (P11-012) | `PinguiApplication`, `AppOptions`, `MainController` | `PinguiApplicationTest.parseOptions_sessionDbPath` |
| Persistence policy (P11-013) | `PersistencePolicy`, `PersistencePolicyHolder`, `PersistenceEventWriter`, `MonitorService` | `PersistencePolicyTest`, `PersistencePolicyHolderTest`, `PersistenceEventWriterTest`, `MonitorServiceTest.appliesPersistencePolicyAfterPollCycle` |
| Persistence GUI + YAML (P11-014…015) | `PersistenceSettingsDialog`, `PersistenceEventsConfig`, `CliPersistenceOverrides`, `ProfilesConfig` | `PersistencePolicySupportTest`, `ProfilesConfigTest.loadPersistenceEventsSection`, `PinguiApplicationTest.parseOptions_noPersistRouteChange` |
| Route history UI (P11-020…021) | `RouteHistoryPresenter`, `RouteGraphPresenter`, `SessionDatabase.listEvents` | `SessionDatabaseTest.listRouteChangeEventsFiltersByHostAndTime`, `RouteHistoryPresenterTest` |
| Raw ICMP packet | `IcmpPacket`, `IcmpV6Packet` | `IcmpPacketTest`, `IcmpV6PacketTest` |
| Expert ping flags | `PingExpertValidator`, `ProcessExpertPing`, `ExpertPingArgs`, `HostAddressResolver` | `PingExpertValidatorTest`, `ExpertPingArgsTest`, `ProcessExpertPingTest`, `ExpertPingUiRulesTest`, `HostAddressResolverTest`, `PingTargetResolverTest` |
| GUI / MonitorService | `MainController`, `MonitorService` | *(manual / TestFX — backlog)* |
| UI coordinators | `ProfileUiCoordinator`, `HostListPresenter`, `MonitorLifecycle`, `ViewModeController`, `RouteGraphPresenter` | `./gradlew check`; B-035 manual smoke |
| CI gate | `.github/workflows/java.yml` | `./gradlew check` (Spotless + Checkstyle + layerCheck + test) |
| Doc parity UK/EN | `scripts/check_doc_parity.py` | `python3 scripts/check_doc_parity.py` (CI + `./scripts/ci_venv.sh`) |
| JaCoCo coverage | `build.gradle.kts` `jacocoTestCoverageVerification` | `./gradlew check` (≥80%; tightened exclusions B-064; ExpertPingEnricher included B-064f) |
| Static imports | `config/checkstyle/checkstyle.xml` | `./gradlew checkstyleMain` / `checkstyleTest` |

**Run locally:** `cd java && ./gradlew check`

**CI:** push/PR to `main` / `beta` → workflow [Java CI](../../.github/workflows/java.yml)

Trace fixtures: `java/src/test/resources/trace/`

---

## Python (`beta`)

Module → test matrix for the Python edition. Update when adding features (PY-013).

| Module / Spec | Class / module | Tests |
|---------------|----------------|-------|
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
| CI gate Python | `scripts/ci_venv.sh`, `.github/workflows/ci.yml` | `./pingui.sh --deploy` or `bash scripts/ci_venv.sh` |

**Run locally (Python):** `bash scripts/ci_venv.sh` or `./pingui.sh --deploy`

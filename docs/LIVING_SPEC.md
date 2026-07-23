> **Мова:** Українська · [English](en/LIVING_SPEC.md)

# Living Specification — PINGUI Java (`main`)

Матриця «модуль → unit-тести». Оновлюй при додаванні фіч.

| Модуль / ТЗ | Клас | Тести |
|-------------|------|-------|
| Trace parser coverage (P19-003) | `UnixTraceOutputParser`, `WindowsTraceOutputParser`, `LinuxTracerouteCommand`, `MacTracerouteCommand`, `WindowsTracertCommand` | `UnixTraceOutputParserTest`, `WindowsTraceOutputParserTest`, `TraceCommandBuildersTest` (fixtures in `src/test/resources/trace/`) |
| Trace argv (OS) | `TraceCommandBuilder`, `TraceTarget`, `LinuxTracerouteCommand`, `MacTracerouteCommand`, `WindowsTracertCommand` | `TraceTargetTest`, `TraceCommandBuildersTest`, `ProcessRouteProbeCommandTest` (v6 `-6`) |
| Валідація хостів IPv4/IPv6 | `HostsConfig`, `HostAddressParser`, `HostEntry` | `HostsConfigTest`, `HostAddressParserTest`, `HostEntryTest` |
| CLI interval vs YAML (M-014) | `CliProfileOverrides`, `ProfilesConfig` | `PinguiApplicationTest.m014_*` |
| Build metadata | `AppInfo`, `generateBuildProperties`, `jpackageAppVersion` | `AppInfoTest`, `./gradlew jpackage*` uses Gradle `version` |
| Version source (P19-001) | `build.gradle.kts` `version`, `AppInfo`, `ReadOnlyApiJson` | `AppInfoTest.version_matchesBuildPropertiesWhenGenerated`; `ReadOnlyApiContractTest.openApiStubDocumentsRequiredPaths` |
| Layer deps (no ui in config) | `scripts/check-layer-deps.sh` | `./gradlew layerCheck` |
| GeoIP hints | `GeoCountry` | `GeoCountryTest` (longest-prefix, LAN/IPv6, invalid YAML, 0.0.0.0/0) |
| YAML profiles v2 + legacy | `ProfilesConfig`, `ProfileDocument` | `ProfilesConfigTest` (host flags, type errors, save max hosts), `ProfileDocumentTest` |
| CLI override профілю | `CliProfileOverrides`, `PinguiApplication` | `PinguiApplicationTest` |
| Monitor polling | `MonitorService`, `RoutePoller`, `ExpertPingEnricher` | `MonitorServiceTest`, `ExpertPingEnricherTest` (stub ping) |
| MTR per-hop probe (P13-010) | `MtrProbe`, `MtrProbeState`, `RoutePoller.pollHostMtr` | `MtrProbeTest`, `RoutePollerTest.pollHostMtrDetectsIncrementalRouteChange` |
| YAML `probe_mode` (P13-011) | `HostProbeMode`, `ProfilesConfig`, `MonitorService` | `HostProbeModeTest`, `HostEntryProbeModeTest`, `ProfilesConfigTest.loadProbeModeOnProfileAndHost` |
| Smart poll interval (P13-020) | `HostPollSchedule`, `MonitorService`, `HostEntry`, `SessionStore` | `HostPollScheduleTest`, `HostEntryTest.effectiveIntervalUsesModeDefaultsAndOverride`, `ProfilesConfigTest.loadHostIntervalOverride`, `MonitorServiceTest.pollsHostsOnIndependentSchedules` |
| Burst on route change (P13-021) | `BurstSchedulePolicy`, `MonitorService` | `BurstSchedulePolicyTest`, `MonitorServiceTest.acceleratesPollingAfterRouteChange` |
| Trace concurrency cap (P13-030) | `TraceConcurrencyLimiter`, `TracingProfile`, `MonitorService` | `TraceConcurrencyLimiterTest` (default = `MAX_HOSTS`), `ProfilesConfigTest.loadMaxConcurrentTraces`, `MonitorServiceTest.limitsConcurrentTracePolls`, `MonitorServiceTest.defaultConcurrencyAllowsFullSessionOfTraces`, `MonitorServiceTest.pingOnlyPollsWhileTraceSlotExhausted` |
| Windows preset YAML (P13-040 / P16-043) | `config/hosts.windows.example.yaml`, `ProfilesConfig` | `ProfilesConfigTest.loadWindowsExamplePreset` |
| MTR vs trace docs (P13-050) | `docs/JAVA.md`, `docs/ADR_PROBE_MODES.md` | Docs parity (`check_doc_parity.py`); § probe_mode + MTR limitations in JAVA.md |
| Route diff panel (P14-010) | `RouteDiff`, `RouteDiffPresenter`, `RouteGraphPresenter`, `GraphCanvas` | `RouteDiffTest`, `RouteDiffPresenterTest`, `RouteGraphPresenterTest.liveRedrawShowsDiffAgainstPreviousRoute` |
| Host tags YAML (P14-020) | `HostTags`, `HostEntry`, `ProfilesConfig`, `HostItem`, `MainController` | `HostTagsTest`, `HostEntryTest.withTagsPreservesOtherFields`, `ProfilesConfigTest.loadHostTagsRoundTrip` |
| Tag filter chips + edit (P14-021) | `HostListPresenter`, `HostTagsDialog`, `SessionStore.setTags` | `HostTagsDialogTest`, `HostListPresenterTest`, `SessionStoreTest.setTagsUpdatesSessionAndToHostEntries` |
| ASN hop labels (P14-030) | `AsnLookup`, `AsnInfo`, `IpLiterals`, `PingColor`, `AppOptions` | `AsnLookupTest`, `IpLiteralsTest`, `PingColorTest.nodeLabelUsesAvgPing`, `PinguiApplicationTest` ASN flags |
| rDNS hop labels (P14-031) | `DnsResolver`, `PingColor`, `MainController`, `GraphCanvas` | `DnsResolverTest`, `PingColorTest.nodeLabelIncludesCachedRdns` |
| Expert ping presets (P14-040) | `PingPresets`, `PingPreset`, `PingExpertDialog`, `ping_presets.yaml` | `PingPresetsTest` |
| USER_GUIDE pro/NOC (P14-050) | `docs/USER_GUIDE.md`, `docs/en/USER_GUIDE.md`, `docs/en/DEPLOYMENT.md` | `scripts/check_doc_parity.py` |
| Observability boundaries (P15-001) | `docs/ADR_OBSERVABILITY.md`, `docs/en/ADR_OBSERVABILITY.md` | Docs parity (`check_doc_parity.py`); Prometheus pull vs TS push |
| Prometheus `/metrics` (P15-010) | `PrometheusExporter`, `MetricsHttpServer`, `DaemonRunner` | `PrometheusExporterTest`, `MetricsHttpServerTest`, `DaemonRunnerTest.startWithMetricsPortServesPrometheus` |
| CLI `--metrics-port` (P15-011) | `PinguiApplication.parseOptions`, `AppOptions.metricsPort` | `PinguiApplicationTest.parseOptions_metricsPort*` |
| Influx/Timescale writer (P15-020) | `persistence/timeseries/*`, `CliTimeSeriesOverrides`, `SessionStore`, `DaemonRunner`, `MainController` | `TimeSeriesBackendsTest`, `SessionStoreTest.forwardsRouteAndPingSamplesToTimeSeriesBackend`, `PinguiApplicationTest.parseOptions_timeSeriesFlags` |
| Scheduled CSV/HTML export (P15-030) | `ScheduledExport`, `ExportSchedulePeriod`, `PinguiApplication` | `ScheduledExportTest`, `PinguiApplicationTest.parseOptions_exportSchedule*` |
| Read-only REST API (P15-040) | `api/ReadOnlyApiServer`, `DaemonRunner`, `AppOptions.apiPort` | `ReadOnlyApiServerTest`, `PinguiApplicationTest.parseOptions_apiPort*` |
| Reverse proxy + TLS docs (P15-041) | `docs/DEPLOYMENT.md`, `docs/en/DEPLOYMENT.md` | `scripts/check_doc_parity.py` |
| API contract tests (P15-050) | `api/ReadOnlyApiServer`, inline JSON string contracts | `ReadOnlyApiContractTest`, `JsonStringsTest`, `DaemonRunnerTest.startWithApiPortServesHosts` |
| Telemetry ADR (P16-001) | `docs/ADR_TELEMETRY.md`, `docs/en/ADR_TELEMETRY.md` | Docs parity (`check_doc_parity.py`); bus → sinks; межі P10/P15 |
| LOG sinks SPIKE (P16-002) | `docs/SPIKE_LOG_SINKS.md`, `docs/en/SPIKE_LOG_SINKS.md` | Docs parity; v1 = syslog TCP + GELF |
| Telemetry models (P16-010) | `MetricSample`, `TelemetryEvent`; `models.py` | `MetricSampleTest`, `TelemetryEventTest`, `test_telemetry_models.py` |
| Sink registry (P16-011) | `TelemetrySink`, `SinkRegistry`, `NoOpTelemetrySink` | `SinkRegistryTest` |
| Telemetry bus (P16-012) | `TelemetryBus`, `DropPolicy` | `TelemetryBusTest` |
| Monitor → bus (P16-013) | `MonitorService.setTelemetryBus`; `telemetry_emit.py` | `MonitorServiceTelemetryTest`, `test_monitor_telemetry.py` |
| Metric names (P16-014) | `MetricNames.java`; `metric_names.py` | `MetricNamesTest`, `test_metric_names.py` |
| Sqlite telemetry sink (P16-020) | `SqliteTelemetrySink`; schema v4 | `SqliteTelemetrySinkTest` |
| JSONL rotate sink (P16-021) | `JsonlRotateSink` | `JsonlRotateSinkTest` |
| Telemetry retention (P16-022) | `TelemetryRetentionJob` | `TelemetryRetentionJobTest` |
| Telemetry dump (P16-023) | `TelemetryDump` | `TelemetryDumpTest` |
| Syslog sink (P16-030) | `SyslogSink` | `SyslogSinkTest` (mock TCP) |
| GELF sink (P16-031) | `GelfSink` | `GelfSinkTest` |
| Loki push sink (P16-032) | `LokiPushSink` | `LokiPushSinkTest` |
| events_only LOG policy (P16-033) | `SinkConfig` | `SinkConfigTest` |
| RTT aggregates → LOG (P16-034) | `AggregateTelemetryJob` | `AggregateTelemetryJobTest` |
| YAML telemetry (P16-040) | `TelemetryConfig`; `ProfilesConfig`; `telemetry_config.py` | `ProfilesConfigTest.loadTelemetry*`, `test_telemetry_config.py` |
| CLI telemetry overrides (P16-041) | `CliTelemetryOverrides`; `PinguiApplication`; `__main__.py` | `PinguiApplicationTest.parseOptions_telemetrySyslogAndJsonlOverrideProfile` |
| Telemetry secret redaction (P16-042) | `TelemetryConfig.redactUrl` / `toRedactedString` | `TelemetryConfigRedactionTest` |
| Windows telemetry preset (P16-043) | `hosts.windows.example.yaml` `telemetry:` | `ProfilesConfigTest.loadWindowsExamplePreset` |
| Webhook as TelemetrySink (P16-050) | `WebhookTelemetrySink`; `WebhookAlertDispatcher` | `WebhookTelemetrySinkTest`; `WebhookAlertDispatcherTest` |
| Prometheus as TelemetrySink (P16-051) | `PrometheusTelemetrySink`; `DaemonRunner` | `PrometheusTelemetrySinkTest`; `MonitorServiceTest.updatesPrometheusViaTelemetrySinkOnPoll` |
| Influx/Timescale TelemetrySink (P16-052) | `InfluxTelemetrySink`; Python daemon/GUI | `test_influx_telemetry_sink.py` |
| CONFIGURATION telemetry (P16-060) | `docs/CONFIGURATION.md` § telemetry | Full YAML/CLI field tables; docs parity |
| DEPLOYMENT LOG-server (P16-061) | `docs/DEPLOYMENT.md` § LOG-server | rsyslog/Graylog/Loki + retention; docs parity |
| LIVING_SPEC telemetry matrix (P16-070) | цей документ § фаза 16 | Матриця bus → sinks → тести нижче |
| Daemon sink wiring + CHECKLIST smoke (P16-071) | `TelemetrySinkInstaller`, `DaemonRunner`; `docs/CHECKLIST.md` | `TelemetrySinkInstallerTest`, `DaemonRunnerTest.startRegistersSqliteAndSyslogFromTelemetryConfig` |
| Syslog/GELF field contract (P16-072) | `SyslogSink`, `GelfSink`; `TelemetryLogFieldFixture` | `SyslogGelfContractTest` (mock TCP + shared fields) |
| OTLP/HTTP export (P16-080) | `OtlpHttpTelemetrySink`; `TelemetryConfig.otlp` | `OtlpHttpTelemetrySinkTest`; Profiles/CLI otlp |
| GUI telemetry bus wire (P16-090) | `TelemetryAttachment`, `MainController`, `DaemonRunner` | `TelemetryAttachmentTest`; `DaemonRunnerTest.startRegistersSqliteAndSyslogFromTelemetryConfig` |
| Telemetry settings dialog (P16-091/092) | `TelemetrySettingsDialog`, `MainController` | `TelemetrySettingsDialogTest` |
| Python LOG sinks stance (P16-093) | `__main__._note_python_log_sinks`; `docs/CONFIGURATION.md` | `test_main_telemetry_note.py` |
| Help/About + GUI telemetry smoke (P16-094) | `AppMenuDialogs`; `docs/CHECKLIST.md` | `AppMenuDialogsTest` |
| Expert preset UX copy (P17-010) | `PingPreset`, `PingPresets`, `ping_presets.yaml`, `PingExpertDialog` | `PingPresetTest`, `PingPresetsTest` |
| MtuDiscovery engine (P17-020) | `MtuDiscovery`, `MtuDiscoveryConfig`, `ProcessMtuProbeRunner` | `MtuDiscoveryTest`, `ProcessMtuProbeRunnerTest` |
| MTU wizard UI (P17-021) | `MtuDiscoveryDialog`, `HostListPresenter`, `PingExpertDialog` | `MtuDiscoveryDialogTest`, `MtuDiscoveryTest` (progress) |
| Expert Self-check DF/DSCP/Burst (P17-030) | `PresetSelfCheck`, `PresetSelfCheckUi`, `PingExpertDialog` | `PresetSelfCheckTest`, `PresetSelfCheckUiTest` |
| Ping only toggle stats (P18-010) | `SessionStore.setProbeMode`, `MonitorService.pollHostOnce`, `HostListPresenter` | `SessionStoreTest.setProbeModeClearsHopStatsAndPingHistory`, `MonitorServiceTest.discardsStaleTraceOutcomeAfterPingOnlyToggle`, `MonitorServiceTest.discardsStaleTraceWhenMonitorFlippedBeforeSessionResolver` |
| Legacy pingOnly removal (P19-004) | `MonitorService` (`probeModes` only), `HostListPresenter` | `MonitorServiceTest.setHostProbeModeGuardsUnknownHost`, `hostProbeModeResolverOverridesMap`, stale-discard tests without `PingOnlyResolver` |
| HostRegistry slice (P19-005) | `HostRegistry`, `MonitorService` | `HostRegistryTest`; existing `MonitorServiceTest` regression |
| PostgreSQL optional (P19-006) | `build.gradle.kts` (`compileOnly` + `-PwithPostgresql`), `TimescaleTimeSeriesBackend.requirePostgresqlDriver` | `TimeSeriesBackendsTest.requirePostgresqlDriverSucceedsWhenOnTestClasspath`; `installDist` lib без `postgresql*.jar` |
| Simple-mode feedback (P20-001) | `UserFeedback`, `UiFeedbackRouter`, `ViewModeController`, `MainController`, `HostListPresenter`, `ProfileUiCoordinator` | `UiFeedbackRouterTest`, `ViewModeControllerTest`, `HostListPresenterTest.addHostValidationFailureCallsErrorFeedback` |
| Confirm delete host/profile (P20-002) | `ConfirmDialogs`, `HostListPresenter`, `ProfileUiCoordinator` | `HostListPresenterTest.removeHostCancelDoesNotMutate`, `removeHostOkDeletesSelectedHost`, `ProfileUiCoordinatorTest` |
| Dirty / unsaved (P20-003) | `ConfigDirtyState`, `ConfirmDialogs.confirmUnsaved`, `MainController`, `HostListPresenter`, `ProfileUiCoordinator` | `ConfigDirtyStateTest`, `ProfileUiCoordinatorTest` (Cancel/Save/Discard), `HostListPresenterTest.addHostMarksDirtyOnSuccess` |
| Route diff visual (P20-004) | `RouteDiffStyle`, `RouteDiffPresenter` | `RouteDiffStyleTest`, `RouteDiffPresenterTest.showChangedRowExposesKindForStyledCell` |
| Export from menu (P20-005) | `SessionExportUi`, `SessionReportExporter`, `MainController` | `SessionExportUiTest`, `SessionReportExporterTest.isHtmlReportMatchesHtmlExtensionsOnly`, `exportChoosesFormatByExtension`, `AppMenuDialogsTest` |
| Keyboard accelerators (P20-006) | `AppAccelerators`, `MainController`, `AppMenuDialogs` | `AppAcceleratorsTest`, `AppMenuDialogsTest` |
| Empty states (P20-007) | `EmptyStateHints`, `RouteHistoryPresenter`, `ViewModeController`, `MainController` | `EmptyStateHintsTest`, `RouteHistoryPresenterTest` (placeholders), `ViewModeControllerTest` |
| Self-check ProgressBar (P20-008) | `PresetSelfCheck`, `PresetSelfCheckUi`, `PingExpertDialog` | `PresetSelfCheckTest.reportsProgressAfterEachPreset`, `PresetSelfCheckUiTest.progressFractionAndStatusLine` |
| Wire log_aggregates (P20-009) | `AggregateTelemetryJob`, `TelemetryBus`, `TelemetryAttachment`, `TelemetrySettingsDialog` | `TelemetryBusTest.enabledAggregatesEmitRttAggregateOnClose`, `TelemetryAttachmentTest.attachEnablesAggregateJobWhenLogAggregatesTrue` |
| Profile params GUI (P20-010) | `ProfileParamsSettingsDialog`, `TracingProfile`, `MainController` | `ProfileParamsSettingsDialogTest`, `AppMenuDialogsTest` |
| Alerts settings GUI (P20-011) | `AlertsSettingsDialog`, `AlertConfig`, `MainController`, `AlertDispatchers` | `AlertsSettingsDialogTest`, `AppMenuDialogsTest` |
| ADR якісних алертів (P21-001) | `docs/ADR_ALERT_RULES.md`, `docs/en/ADR_ALERT_RULES.md`; related `ADR_ALERTS` | Doc parity; контракт lifecycle `endpoint_down` |
| AlertRuleEngine endpoint_down (P21-002) | `AlertRuleEngine`, `QualityAlertEvent`, `EndpointDownRuleConfig`, `MonitorService` | `AlertRuleEngineTest`, `MonitorServiceTest.dispatchesEndpointDownAfterConsecutiveUnreachablePolls` |
| latency_high (P23) | `LatencyHighRuleConfig`, `AlertRuleEngine`, `MonitorService`, `AlertsSettingsDialog`, `PersistenceEventType.LATENCY_HIGH` | `AlertRuleEngineTest.latencyHigh*`, `ProfilesConfigTest.saveAndReloadLatencyHighRules`, `AlertsSettingsDialogTest.buildConfigEnablesLatencyHighCriticalDefaults` |
| YAML/GUI alerts.rules (P21-003) | `AlertConfig`, `EndpointDownRuleConfig`, `ProfilesConfig`, `AlertsSettingsDialog`, `MonitorLifecycle` | `ProfilesConfigTest`, `AlertsSettingsDialogTest`, `AppMenuDialogsTest` |
| Host problem indicator ADR (P22-001) | `docs/ADR_HOST_PROBLEM_INDICATOR.md` | docs review / ROADMAP P22 |
| HostProblemSummary (P22-002) | `AlertRuleEngine`, `HostProblemSummary`, `MonitorService` | `AlertRuleEngineTest`, `MonitorServiceTest` |
| endpoint_down SQLite (P22-003) | `PersistenceEventType`, `PersistenceEventWriter`, `MonitorService` | `PersistenceEventWriterTest`, `MonitorServiceTest` |
| Host problem icon (P22-004) | `HostItem`, `HostListCell`, `ProblemDetailsDialog`, `HostListPresenter` | `ProblemDetailsDialogTest`, `HostItemProblemTest` |
| Session DB auto-name (P22-005) | `LocalIpv4`, `SessionDbAutoName`, `PersistenceSettingsDialog` | `LocalIpv4Test`, `SessionDbAutoNameTest` |
| Graph UX (P20-012) | `GraphCanvas`, `RouteGraphInteraction`, `RouteGraphLayout`, `MainController` | `RouteGraphInteractionTest`, `RouteGraphLayoutTest`, `AppMenuDialogsTest` |
| Python persistence events (PY-P11) | `persistence/policy.py`, `persistence/events.py`, `session_db.py`, `__main__.py` | `test_persistence_events.py` |
| Route-change alerts | `RouteChangeEvent`, `AlertDispatcher`, `AlertDispatchers`, `WebhookAlertDispatcher`, `DesktopAlertDispatcher`, `DesktopAlertSink`, `JavaFxDesktopAlertSink`, `AlertRateLimiter`, `RouteChangeNotifier` | `RouteChangeEventTest`, `MonitorServiceTest.dispatchesAlertOnRouteChange`, `WebhookAlertDispatcherTest`, `DesktopAlertDispatcherTest`, `AlertRateLimiterTest`, `AlertDispatchersTest`, `ProfilesConfigTest.loadAlertsSection` |
| Session metrics | `SessionStore`, `HostTargetStats` | `SessionStoreTest`, `HopStatsTest` |
| SQLite session (P11-010) | `SessionDatabase`, `SessionJsonCodec` | `SessionDatabaseTest` |
| Persistence wire (P11-011) | `SessionStore`, `PersistenceEventWriter`, `MonitorService` | `SessionStorePersistenceTest`, `PersistenceEventWriterTest`, `MonitorServiceTest.persistsRouteChangeAndProbeErrorEvents` |
| CLI `--session-db` (P11-012) | `PinguiApplication`, `AppOptions`, `MainController` | `PinguiApplicationTest.parseOptions_sessionDbPath` |
| Persistence policy (P11-013) | `PersistencePolicy`, `PersistencePolicyHolder`, `PersistenceEventWriter`, `MonitorService` | `PersistencePolicyTest`, `PersistencePolicyHolderTest`, `PersistenceEventWriterTest`, `MonitorServiceTest.appliesPersistencePolicyAfterPollCycle` |
| Persistence GUI + YAML (P11-014…015) | `PersistenceSettingsDialog`, `PersistenceEventsConfig`, `CliPersistenceOverrides`, `ProfilesConfig` | `PersistencePolicySupportTest`, `ProfilesConfigTest.loadPersistenceEventsSection`, `PinguiApplicationTest.parseOptions_noPersistRouteChange` |
| Route history UI (P11-020…021) | `RouteHistoryPresenter`, `RouteGraphPresenter`, `SessionDatabase.listEvents` | `SessionDatabaseTest.listRouteChangeEventsFiltersByHostAndTime`, `RouteHistoryPresenterTest` |
| Graph host source (stale replay) | `RouteGraphPresenter`, `MainController.viewHost` | `RouteGraphPresenterTest.staleReplayForOtherHostFallsBackToLiveSelection` |
| Session export (P11-030) | `SessionReportExporter`, `PinguiApplication.runExportReport` | `SessionReportExporterTest`, `SessionDatabaseTest.listHostsReturnsSortedNames`, `PinguiApplicationTest.parseOptions_exportReportPath` |
| GUI SQLite connection (P11-016) | `PersistenceConfig`, `SessionDbResolver`, `PersistenceSettingsDialog`, `MainController` | `SessionDbResolverTest`, `ProfilesConfigTest.loadPersistenceSessionDbSection` |
| Hop stats labels from history (P11-040) | `SessionStore`, `PingColor`, `RouteGraphPresenter` | `SessionStorePersistenceTest.hopStatsPersistAcrossReopen`, `PingColorTest.nodeLabelIncludesHopStats` |
| Default target ping + baseline history | `DefaultTargetPingEnricher`, `MonitorService`, `RouteHistoryItem` | `DefaultTargetPingEnricherTest`, `MonitorServiceTest.persistsBaselineRouteChangeOnFirstPoll` |
| Java headless daemon (P12) | `DaemonRunner`, `DaemonPidFile`, `PinguiApplication` | `DaemonPidFileTest`, `PinguiApplicationTest` daemon/stop/status |
| SQLite disk/retention (P11-050) | `docs/DEPLOYMENT.md` | Manual smoke; purge via GUI; no auto-TTL on `host_session` |
| Raw ICMP packet | `IcmpPacket`, `IcmpV6Packet` | `IcmpPacketTest`, `IcmpV6PacketTest` |
| Expert ping flags | `PingExpertValidator`, `ProcessExpertPing`, `ExpertPingArgs`, `HostAddressResolver` | `PingExpertValidatorTest`, `ExpertPingArgsTest`, `ProcessExpertPingTest`, `ExpertPingUiRulesTest`, `HostAddressResolverTest`, `PingTargetResolverTest` |
| GUI / MonitorService | `MainController`, `MonitorService` | *(manual / TestFX — backlog)* |
| UI coordinators | `ProfileUiCoordinator`, `HostListPresenter`, `MonitorLifecycle`, `ViewModeController`, `RouteGraphPresenter` | `./gradlew check`; B-035 manual smoke |
| CI gate | `.github/workflows/java.yml` | `./gradlew check` (ubuntu + windows jobs block merge; Monocle headless for FX UI tests) |
| Windows CI blocking (P19-002) | `.github/workflows/java.yml` `check-windows` | No `continue-on-error`; both matrix jobs required on `main`/`beta` |
| Doc parity UK/EN | `scripts/check_doc_parity.py` | `python3 scripts/check_doc_parity.py` (CI + `./scripts/ci_venv.sh`) |
| JaCoCo coverage | `build.gradle.kts` `jacocoTestCoverageVerification` | `./gradlew check` (≥80%; parsers + command builders included P19-003) |
| Static imports | `config/checkstyle/checkstyle.xml` | `./gradlew checkstyleMain` / `checkstyleTest` |

**Прогін локально:** `cd java && ./gradlew check`

**CI:** push/PR на `main` / `beta` → workflow [Java CI](../.github/workflows/java.yml)

Фікстури trace: `java/src/test/resources/trace/`

---

## Фаза 16 — Телеметрія (огляд bus → sinks)

Потік: **Monitor / jobs** → `TelemetryBus` → `SinkRegistry` → sinks. Скрап Prometheus і webhook alerts ідуть через sink-адаптери (P16-050/051), без dual-emit з MonitorService.

| Шар | Модулі | Покриття |
|-----|--------|----------|
| Моделі + імена | `MetricSample`, `TelemetryEvent`, `MetricNames` | `MetricSampleTest`, `TelemetryEventTest`, `MetricNamesTest` (+ Python `test_telemetry_models.py`, `test_metric_names.py`) |
| Bus / registry | `TelemetryBus`, `DropPolicy`, `SinkRegistry`, `NoOpTelemetrySink` | `TelemetryBusTest`, `SinkRegistryTest` |
| Wire від poll | `MonitorService.setTelemetryBus` | `MonitorServiceTelemetryTest` (+ `test_monitor_telemetry.py`) |
| Local sinks | `SqliteTelemetrySink`, `JsonlRotateSink`, `TelemetryRetentionJob`, `TelemetryDump` | `SqliteTelemetrySinkTest`, `JsonlRotateSinkTest`, `TelemetryRetentionJobTest`, `TelemetryDumpTest` |
| LOG sinks | `SyslogSink`, `GelfSink`, `LokiPushSink`, `SinkConfig`, `AggregateTelemetryJob` | `SyslogSinkTest`, `GelfSinkTest`, `LokiPushSinkTest`, `SinkConfigTest`, `AggregateTelemetryJobTest`, `SyslogGelfContractTest` |
| Config / CLI | `TelemetryConfig`, `ProfilesConfig`, `CliTelemetryOverrides` | `ProfilesConfigTest.loadTelemetry*`, `PinguiApplicationTest.parseOptions_telemetry*`, `TelemetryConfigRedactionTest` (+ `test_telemetry_config.py`) |
| Bridge sinks | `WebhookTelemetrySink`, `PrometheusTelemetrySink`, `InfluxTelemetrySink`, `OtlpHttpTelemetrySink` | `WebhookTelemetrySinkTest`, `PrometheusTelemetrySinkTest`, `OtlpHttpTelemetrySinkTest`, `test_influx_telemetry_sink.py`; `MonitorServiceTest.updatesPrometheusViaTelemetrySinkOnPoll` |
| Docs | CONFIGURATION § telemetry, DEPLOYMENT § LOG-server | `scripts/check_doc_parity.py` |

Рядки P16-001…070 у таблиці вище — детальна матриця ТЗ → клас → тест.

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
| Telemetry models | `models.py` (`MetricSample`, `TelemetryEvent`) | `test_telemetry_models.py` |
| Telemetry YAML | `telemetry_config.py` | `test_telemetry_config.py` |
| Monitor → bus emit | `telemetry_emit.py`; `monitor_loop.py` | `test_monitor_telemetry.py` |
| Metric names | `metric_names.py` | `test_metric_names.py` |
| Influx TelemetrySink | `persistence/timeseries/influx_telemetry_sink.py` | `test_influx_telemetry_sink.py` |
| Export reports | `export/session_report.py` | `test_session_export.py`, `test_main_export.py` |
| GeoIP | `geoip/country.py`, `map_builder.py` | `test_geoip_country.py` (v4/v6/LAN), `test_geo_map.py` |
| GUI | `ui/main_window.py`, `graph_canvas.py` | `test_graph_canvas.py`, `integration/test_ui_smoke.py` |
| CLI entry | `__main__.py` | `test_main.py`, `test_main_cli_validation.py`, `test_main_export.py`, `test_main_subcommands.py`, `test_main_dispatch.py` |
| Import graph | `scripts/check_imports.py` | `python scripts/check_imports.py` |
| Doc parity UK/EN | `scripts/check_doc_parity.py` | `test_doc_parity.py`, CI |
| CI gate Python | `scripts/ci_venv.sh`, `.github/workflows/ci.yml` | `./pingui.sh --deploy` або `bash scripts/ci_venv.sh` |

**Прогін локально (Python):** `bash scripts/ci_venv.sh` або `./pingui.sh --deploy`

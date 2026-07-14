package io.pingui.daemon;

import io.pingui.AppOptions;
import io.pingui.CliProfileOverrides;
import io.pingui.CliTelemetryOverrides;
import io.pingui.api.ReadOnlyApiServer;
import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.config.SessionDbResolver;
import io.pingui.config.TracingProfile;
import io.pingui.geoip.AsnLookup;
import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.observability.MetricsHttpServer;
import io.pingui.observability.PrometheusExporter;
import io.pingui.observability.PrometheusTelemetrySink;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.timeseries.TimeSeriesBackend;
import io.pingui.persistence.timeseries.TimeSeriesBackends;
import io.pingui.persistence.timeseries.TimeSeriesConfigException;
import io.pingui.telemetry.SinkRegistry;
import io.pingui.telemetry.TelemetryBus;
import io.pingui.ui.HostViewRules;
import io.pingui.ui.MonitorLifecycle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Headless MonitorService loop without JavaFX (P12-010). */
public final class DaemonRunner implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DaemonRunner.class);

    private final AppOptions options;
    private final Path pidFile;
    private ProfileDocument profileDocument;
    private SessionStore store;
    private MonitorService monitor;
    private SinkRegistry telemetryRegistry;
    private TelemetryBus telemetryBus;
    private MetricsHttpServer metricsServer;
    private ReadOnlyApiServer apiServer;
    private final CountDownLatch running = new CountDownLatch(1);
    private volatile boolean closed;

    public DaemonRunner(AppOptions options, Path pidFile) {
        this.options = options;
        this.pidFile = pidFile;
    }

    public void start() throws IOException {
        if (DaemonPidFile.isRunning(pidFile)) {
            throw new IllegalStateException("Daemon already running (PID file: " + pidFile.toAbsolutePath() + ")");
        }
        profileDocument = ProfilesConfig.load(options.configPath());
        applyCliOverridesToActiveProfile();
        GeoCountry.configure(options.geoipEnabled(), options.geoipHintsPath());
        AsnLookup.configure(options.asnEnabled(), options.asnHintsPath(), options.asnTimeoutMs());
        TracingProfile active = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(active.hosts());
        store = SessionStore.fromEntries(sessionHosts, openSessionDatabase(active), active.hostProbeMode());
        attachTimeSeries(store);
        monitor = createMonitor(active, sessionHosts);
        attachTelemetryBus(monitor);
        startMetricsIfConfigured();
        startApiIfConfigured();
        DaemonPidFile.write(pidFile, ProcessHandle.current().pid());
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeQuietly, "pingui-daemon-shutdown"));
        LOG.info(
                "PINGUI daemon started (profile={}, hosts={}, pid={})",
                profileDocument.activeProfile(),
                sessionHosts.size(),
                ProcessHandle.current().pid());
        if (monitor.enabledHosts().isEmpty()) {
            LOG.warn("No enabled hosts in profile — enable targets in YAML or daemon will idle");
        }
    }

    /** Blocks until shutdown hook or external stop. */
    public void await() throws InterruptedException {
        running.await();
    }

    /** Exposed for tests — metrics HTTP server when {@code metricsPort} is set. */
    public Optional<MetricsHttpServer> metricsServer() {
        return Optional.ofNullable(metricsServer);
    }

    /** Exposed for tests — read-only API when {@code apiPort} is set. */
    public Optional<ReadOnlyApiServer> apiServer() {
        return Optional.ofNullable(apiServer);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (apiServer != null) {
            apiServer.close();
            apiServer = null;
        }
        if (metricsServer != null) {
            metricsServer.close();
            metricsServer = null;
        }
        if (monitor != null) {
            monitor.close();
            monitor = null;
        }
        if (telemetryBus != null) {
            telemetryBus.close();
            telemetryBus = null;
        }
        if (telemetryRegistry != null) {
            telemetryRegistry.close();
            telemetryRegistry = null;
        }
        if (store != null) {
            store.close();
            store = null;
        }
        try {
            DaemonPidFile.deleteIfExists(pidFile);
        } catch (IOException ex) {
            LOG.warn("Failed to remove PID file {}: {}", pidFile, ex.getMessage());
        }
        running.countDown();
        LOG.info("PINGUI daemon stopped");
    }

    private void startMetricsIfConfigured() throws IOException {
        Optional<Integer> port = options.metricsPort();
        if (port.isEmpty()) {
            return;
        }
        PrometheusExporter exporter = new PrometheusExporter();
        metricsServer = MetricsHttpServer.start(exporter, port.get());
        if (telemetryRegistry != null) {
            telemetryRegistry.register(new PrometheusTelemetrySink(exporter));
            LOG.info("Prometheus telemetry sink registered (scrape :{})", port.get());
        } else {
            LOG.warn("Metrics port set but telemetry registry missing — scrape empty until bus wired");
        }
    }

    private void startApiIfConfigured() throws IOException {
        Optional<Integer> port = options.apiPort();
        if (port.isEmpty()) {
            return;
        }
        apiServer = ReadOnlyApiServer.start(store, port.get());
    }

    private void closeQuietly() {
        try {
            close();
        } catch (RuntimeException ex) {
            LOG.warn("Daemon shutdown error: {}", ex.getMessage());
        }
    }

    private void applyCliOverridesToActiveProfile() {
        CliProfileOverrides profileOverrides = options.profileOverrides();
        CliTelemetryOverrides telemetryOverrides = options.telemetryOverrides();
        if (profileOverrides.isEmpty() && telemetryOverrides.isEmpty()) {
            return;
        }
        TracingProfile active = profileDocument.active();
        TracingProfile merged = profileOverrides.applyTo(active);
        merged = merged.withTelemetry(telemetryOverrides.applyTo(merged.telemetry()));
        profileDocument.putProfile(profileDocument.activeProfile(), merged);
    }

    private SessionDatabase openSessionDatabase(TracingProfile profile) {
        return SessionDbResolver.resolve(
                        options.sessionDbPath(), profile.persistence().sessionDb(), Optional.empty())
                .map(SessionDatabase::new)
                .orElse(null);
    }

    private void attachTelemetryBus(MonitorService service) {
        telemetryRegistry = new SinkRegistry();
        telemetryBus = new TelemetryBus(telemetryRegistry);
        service.setTelemetryBus(telemetryBus);
    }

    private void attachTimeSeries(SessionStore sessionStore) {
        try {
            TimeSeriesBackend backend = TimeSeriesBackends.create(options.timeSeriesOverrides());
            if (backend != null) {
                sessionStore.setTimeSeriesBackend(backend);
                LOG.info("Time-series backend enabled");
            }
        } catch (TimeSeriesConfigException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private MonitorService createMonitor(TracingProfile profile, List<HostEntry> sessionHosts) {
        MonitorService service = MonitorLifecycle.create(
                profile,
                profileDocument.activeProfile(),
                store,
                new MonitorService.Listener() {
                    @Override
                    public void onDataReceived(String host, RouteSnapshot snapshot) {
                        if (!store.containsHost(host)) {
                            return;
                        }
                        store.updateRoute(host, snapshot);
                        store.appendPingSamples(host, snapshot);
                    }

                    @Override
                    public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {
                        if (!oldIps.isEmpty()) {
                            LOG.warn("Route change [{}]: {} -> {}", host, oldIps, newIps);
                        } else if (options.verbose()) {
                            LOG.debug("Baseline route [{}]: {}", host, newIps);
                        }
                    }

                    @Override
                    public void onProbeError(String host, String message) {
                        LOG.warn("Probe error [{}]: {}", host, message);
                    }
                },
                options.alertOverrides().applyTo(profile.alerts()),
                store.database(),
                sessionHosts);
        PersistencePolicy policy =
                options.persistenceOverrides().applyTo(profile.persistence()).toPolicy();
        service.setPendingPersistencePolicy(policy);
        service.persistencePolicy().applyPendingAfterCycle();
        return service;
    }
}

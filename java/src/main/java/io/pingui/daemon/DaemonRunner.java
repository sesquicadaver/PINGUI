package io.pingui.daemon;

import io.pingui.AppOptions;
import io.pingui.CliProfileOverrides;
import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.config.SessionDbResolver;
import io.pingui.config.TracingProfile;
import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.SessionDatabase;
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
        TracingProfile active = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(active.hosts());
        store = SessionStore.fromEntries(sessionHosts, openSessionDatabase(active), active.hostProbeMode());
        monitor = createMonitor(active, sessionHosts);
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (monitor != null) {
            monitor.close();
            monitor = null;
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

    private void closeQuietly() {
        try {
            close();
        } catch (RuntimeException ex) {
            LOG.warn("Daemon shutdown error: {}", ex.getMessage());
        }
    }

    private void applyCliOverridesToActiveProfile() {
        CliProfileOverrides overrides = options.profileOverrides();
        if (overrides.isEmpty()) {
            return;
        }
        TracingProfile active = profileDocument.active();
        profileDocument.putProfile(profileDocument.activeProfile(), overrides.applyTo(active));
    }

    private SessionDatabase openSessionDatabase(TracingProfile profile) {
        return SessionDbResolver.resolve(
                        options.sessionDbPath(), profile.persistence().sessionDb(), Optional.empty())
                .map(SessionDatabase::new)
                .orElse(null);
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

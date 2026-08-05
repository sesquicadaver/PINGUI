package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.CliProfileOverrides;
import io.pingui.CliTelemetryOverrides;
import io.pingui.config.HostEntry;
import io.pingui.config.PingPresets;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.config.SessionDbResolver;
import io.pingui.config.TracingProfile;
import io.pingui.dns.DnsResolver;
import io.pingui.geoip.AsnLookup;
import io.pingui.geoip.GeoCountry;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.timeseries.TimeSeriesBackends;
import io.pingui.persistence.timeseries.TimeSeriesConfigException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Heavy GUI startup I/O off the JavaFX Application Thread (P24-009).
 *
 * <p>Loads profiles YAML, GeoIP/ASN/presets hints, opens SQLite session DB, and builds {@link
 * SessionStore}. Does <b>not</b> create {@link io.pingui.monitor.MonitorService} (polling starts in
 * its constructor — attach on FX after {@code Stage.show}).
 */
public final class StartupBootstrap {
    /** Test hook: called with phase ids ({@code profile}, {@code geoip}, {@code sqlite}, {@code done}). */
    static volatile Consumer<String> phaseListener = phase -> {};

    /** Test hook: blocks each phase until the latch is counted down (null = no delay). */
    static volatile CountDownLatch delayLatch;

    /** Test hook: captures the thread name that performed heavy I/O. */
    static final AtomicReference<String> lastHeavyThread = new AtomicReference<>();

    private StartupBootstrap() {}

    /**
     * Result of background load — ready for {@link MainController#attachBootstrap(Result)} on the FX
     * thread.
     */
    public record Result(
            ProfileDocument document,
            SessionStore store,
            List<HostEntry> sessionHosts,
            boolean sqliteOpened,
            String heavyThreadName) {}

    /** Loads config + enrichment tables + session store. Must not run on the FX thread. */
    public static Result load(AppOptions options) throws IOException {
        lastHeavyThread.set(Thread.currentThread().getName());
        awaitDelay();
        phaseListener.accept("profile");
        ProfileDocument document = ProfilesConfig.load(options.configPath());
        applyCliOverrides(options, document);

        awaitDelay();
        phaseListener.accept("geoip");
        GeoCountry.configure(options.geoipEnabled(), options.geoipHintsPath());
        AsnLookup.configure(options.asnEnabled(), options.asnHintsPath(), options.asnTimeoutMs());
        DnsResolver.configure(true);
        PingPresets.configure(PingPresets.resolvePath(options.configPath()));

        awaitDelay();
        phaseListener.accept("sqlite");
        SessionDatabase database = openSessionDatabase(options, document);
        List<HostEntry> sessionHosts =
                HostViewRules.sessionEntries(document.active().hosts());
        SessionStore store = SessionStore.fromEntries(
                sessionHosts, database, document.active().hostProbeMode());
        attachTimeSeries(store, options);

        phaseListener.accept("done");
        return new Result(
                document,
                store,
                sessionHosts,
                database != null,
                Thread.currentThread().getName());
    }

    static void resetTestHooks() {
        phaseListener = phase -> {};
        delayLatch = null;
        lastHeavyThread.set(null);
    }

    private static void awaitDelay() {
        CountDownLatch latch = delayLatch;
        if (latch == null) {
            return;
        }
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("StartupBootstrap test delay timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("StartupBootstrap interrupted", ex);
        }
    }

    private static void applyCliOverrides(AppOptions options, ProfileDocument document) {
        CliProfileOverrides profileOverrides = options.profileOverrides();
        CliTelemetryOverrides telemetryOverrides = options.telemetryOverrides();
        if (profileOverrides.isEmpty() && telemetryOverrides.isEmpty()) {
            return;
        }
        TracingProfile active = document.active();
        TracingProfile merged = profileOverrides.applyTo(active);
        merged = merged.withTelemetry(telemetryOverrides.applyTo(merged.telemetry()));
        document.putProfile(document.activeProfile(), merged);
    }

    private static SessionDatabase openSessionDatabase(AppOptions options, ProfileDocument document) {
        Optional<Path> path = SessionDbResolver.resolve(
                options.sessionDbPath(), document.active().persistence().sessionDb(), Optional.empty());
        return path.map(SessionDatabase::new).orElse(null);
    }

    private static void attachTimeSeries(SessionStore sessionStore, AppOptions options) {
        try {
            var backend = TimeSeriesBackends.create(options.timeSeriesOverrides());
            if (backend != null) {
                sessionStore.setTimeSeriesBackend(backend);
            }
        } catch (TimeSeriesConfigException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }
}

package io.pingui;

import io.pingui.config.TelemetryConfig;
import io.pingui.monitor.MonitorService;
import io.pingui.persistence.SessionDatabase;
import io.pingui.telemetry.SinkRegistry;
import io.pingui.telemetry.TelemetryBus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns {@link SinkRegistry} + {@link TelemetryBus} + installer resources for one monitor
 * lifecycle (P16-090 / P16-071).
 *
 * <p>Close order after {@link MonitorService#close()}: this attachment (bus → registry → owned
 * DBs), then the session store. Prefer {@link #replace} when re-wiring so the previous attachment
 * is closed before a new bus thread starts.
 */
public final class TelemetryAttachment implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryAttachment.class);

    private final SinkRegistry registry;
    private final TelemetryBus bus;
    private final TelemetrySinkInstaller.Result install;
    private boolean closed;

    private TelemetryAttachment(SinkRegistry registry, TelemetryBus bus, TelemetrySinkInstaller.Result install) {
        this.registry = registry;
        this.bus = bus;
        this.install = install;
    }

    /**
     * Closes {@code previous} (if any), then {@link #attach}es a new attachment.
     *
     * @return new attachment; never null
     */
    public static TelemetryAttachment replace(
            TelemetryAttachment previous,
            MonitorService service,
            TelemetryConfig config,
            Optional<SessionDatabase> sessionDb) {
        if (previous != null) {
            previous.close();
        }
        return attach(service, config, sessionDb);
    }

    /**
     * Installs sinks from {@code config}, starts a bus, and attaches it to {@code service}.
     *
     * @param sessionDb optional session SQLite (reused when {@code telemetry.sqlite} matches)
     */
    public static TelemetryAttachment attach(
            MonitorService service, TelemetryConfig config, Optional<SessionDatabase> sessionDb) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(sessionDb, "sessionDb");

        SinkRegistry registry = new SinkRegistry();
        TelemetrySinkInstaller.Result install = TelemetrySinkInstaller.install(registry, config, sessionDb);
        TelemetryBus bus = new TelemetryBus(registry);
        service.setTelemetryBus(bus);
        if (!install.registeredIds().isEmpty()) {
            LOG.info("Telemetry attached ({})", String.join(",", install.registeredIds()));
        }
        return new TelemetryAttachment(registry, bus, install);
    }

    public SinkRegistry registry() {
        return registry;
    }

    public List<String> registeredIds() {
        return install.registeredIds();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            bus.close();
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry bus close failed: {}", ex.getMessage());
        }
        try {
            registry.close();
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry registry close failed: {}", ex.getMessage());
        }
        install.closeOwned();
    }
}

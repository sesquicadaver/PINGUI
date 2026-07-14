package io.pingui.persistence;

import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import io.pingui.telemetry.TelemetrySink;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local SQLite archive sink for telemetry samples/events (P16-020 / ADR_TELEMETRY).
 *
 * <p>Default off — callers register explicitly. Does not close the owned {@link SessionDatabase}.
 * Failures are logged; methods never throw into the poll / bus path.
 */
public final class SqliteTelemetrySink implements TelemetrySink {
    public static final String ID = "sqlite";

    private static final Logger LOG = Logger.getLogger(SqliteTelemetrySink.class.getName());

    private final SessionDatabase database;

    public SqliteTelemetrySink(SessionDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onSample(MetricSample sample) {
        if (sample == null) {
            return;
        }
        try {
            database.insertTelemetrySample(sample);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "SqliteTelemetrySink sample failed for " + sample.host(), ex);
        }
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        try {
            database.insertTelemetryEvent(event);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "SqliteTelemetrySink event failed for " + event.host(), ex);
        }
    }

    /** Owned DB stays open for session/GUI use; sink close is a no-op. */
    @Override
    public void close() {
        // intentionally empty
    }
}

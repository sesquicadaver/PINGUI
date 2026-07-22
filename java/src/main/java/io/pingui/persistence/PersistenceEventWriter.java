package io.pingui.persistence;

import io.pingui.monitor.QualityAlertEvent;
import io.pingui.monitor.RouteChangeEvent;
import java.time.Instant;
import java.util.Objects;

/** Writes discrete events to SQLite (P11-011); policy gate (P11-013 / P22-003). */
public final class PersistenceEventWriter {
    private final SessionDatabase database;
    private final PersistencePolicyHolder policyHolder;

    public PersistenceEventWriter(SessionDatabase database) {
        this(database, new PersistencePolicyHolder());
    }

    public PersistenceEventWriter(SessionDatabase database, PersistencePolicyHolder policyHolder) {
        this.database = Objects.requireNonNull(database, "database");
        this.policyHolder = policyHolder != null ? policyHolder : new PersistencePolicyHolder();
    }

    public PersistencePolicyHolder policyHolder() {
        return policyHolder;
    }

    public void writeRouteChange(RouteChangeEvent event) {
        if (event == null || !policyHolder.active().allows(PersistenceEventType.ROUTE_CHANGE)) {
            return;
        }
        ensureHostRow(event.host());
        database.insertEvent(
                PersistenceEventType.ROUTE_CHANGE, event.host(), event.profile(), event.toJson(), event.timestamp());
    }

    public boolean hasRouteChangeEvents(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return !database.listEvents(PersistenceEventType.ROUTE_CHANGE, host, Instant.EPOCH, 1)
                .isEmpty();
    }

    public void writeProbeError(String host, String message) {
        if (host == null || host.isBlank() || !policyHolder.active().allows(PersistenceEventType.PROBE_ERROR)) {
            return;
        }
        ensureHostRow(host);
        String payload = probeErrorPayload(host, message);
        database.insertEvent(PersistenceEventType.PROBE_ERROR, host, null, payload, Instant.now());
    }

    /**
     * Persists {@code endpoint_down} FIRING/RESOLVED (P22-003). Survives UI ack; default allowed when DB
     * is connected.
     */
    public void writeQualityAlert(QualityAlertEvent event) {
        if (event == null || !policyHolder.active().allows(PersistenceEventType.ENDPOINT_DOWN)) {
            return;
        }
        ensureHostRow(event.host());
        database.insertEvent(
                PersistenceEventType.ENDPOINT_DOWN, event.host(), event.profile(), event.toJson(), event.timestamp());
    }

    private void ensureHostRow(String host) {
        if (database.load(host) == null) {
            database.save(host, new io.pingui.model.Models.HostSessionData());
        }
    }

    static String probeErrorPayload(String host, String message) {
        return "{\"message\":" + quote(message == null ? "" : message) + ",\"host\":" + quote(host) + "}";
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}

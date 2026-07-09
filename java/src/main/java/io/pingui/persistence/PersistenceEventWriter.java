package io.pingui.persistence;

import io.pingui.monitor.RouteChangeEvent;
import java.time.Instant;

/** Writes discrete events to SQLite (P11-011); policy gate arrives in P11-013. */
public final class PersistenceEventWriter {
    private final SessionDatabase database;

    public PersistenceEventWriter(SessionDatabase database) {
        this.database = database;
    }

    public void writeRouteChange(RouteChangeEvent event) {
        if (database == null || event == null) {
            return;
        }
        ensureHostRow(event.host());
        database.insertEvent(
                PersistenceEventType.ROUTE_CHANGE, event.host(), event.profile(), event.toJson(), event.timestamp());
    }

    public void writeProbeError(String host, String message) {
        if (database == null || host == null || host.isBlank()) {
            return;
        }
        ensureHostRow(host);
        String payload = probeErrorPayload(host, message);
        database.insertEvent(PersistenceEventType.PROBE_ERROR, host, null, payload, Instant.now());
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

package io.pingui.persistence;

/** Discrete event types stored in {@code persistence_event} (SPIKE P11-002). */
public enum PersistenceEventType {
    ROUTE_CHANGE("route_change"),
    PROBE_ERROR("probe_error");

    private final String id;

    PersistenceEventType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PersistenceEventType fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PersistenceException("event_type must be non-empty");
        }
        for (PersistenceEventType type : values()) {
            if (type.id.equals(raw)) {
                return type;
            }
        }
        throw new PersistenceException("Unsupported event_type: " + raw);
    }
}

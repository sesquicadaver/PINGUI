package io.pingui.persistence;

/** Which discrete persistence events are written (SPIKE P11-002; gate P11-013 / P22-003). */
public record PersistencePolicy(boolean routeChange, boolean probeError) {

    public static PersistencePolicy defaults() {
        return new PersistencePolicy(true, true);
    }

    public static PersistencePolicy of(boolean routeChange, boolean probeError) {
        return new PersistencePolicy(routeChange, probeError);
    }

    public boolean allows(PersistenceEventType type) {
        return switch (type) {
            case ROUTE_CHANGE -> routeChange;
            case PROBE_ERROR -> probeError;
                // ADR_HOST_PROBLEM_INDICATOR / P29-002: quality + ack default on when session DB connected.
            case ENDPOINT_DOWN, LATENCY_HIGH, PROBLEM_ACK -> true;
                // P29-004 will write DNS_CHANGE; allow persistence when DB is connected.
            case DNS_CHANGE -> true;
        };
    }
}

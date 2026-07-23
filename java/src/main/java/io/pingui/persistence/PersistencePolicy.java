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
                // ADR_HOST_PROBLEM_INDICATOR: quality incidents default on whenever session DB is connected.
            case ENDPOINT_DOWN, LATENCY_HIGH -> true;
        };
    }
}

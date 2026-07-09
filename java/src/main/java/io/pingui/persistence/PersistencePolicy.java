package io.pingui.persistence;

/** Which discrete persistence events are written (SPIKE P11-002; gate P11-013). */
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
        };
    }
}

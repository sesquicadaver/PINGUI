package io.pingui.config;

import io.pingui.persistence.PersistencePolicy;

/** Per-profile persistence event toggles (SPIKE P11-002 / YAML persistence.events). */
public record PersistenceEventsConfig(boolean routeChange, boolean probeError) {

    public PersistenceEventsConfig {
        // booleans only
    }

    public static PersistenceEventsConfig defaults() {
        return new PersistenceEventsConfig(true, true);
    }

    public PersistencePolicy toPolicy() {
        return PersistencePolicy.of(routeChange, probeError);
    }

    public boolean isDefault() {
        return routeChange && probeError;
    }
}

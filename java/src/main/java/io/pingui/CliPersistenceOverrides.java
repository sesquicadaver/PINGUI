package io.pingui;

import io.pingui.config.PersistenceEventsConfig;
import java.util.Optional;

/** CLI overrides for persistence events; empty fields keep YAML values (SPIKE P11-002 § priority). */
public record CliPersistenceOverrides(Optional<Boolean> routeChange, Optional<Boolean> probeError) {

    public static CliPersistenceOverrides none() {
        return new CliPersistenceOverrides(Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return routeChange.isEmpty() && probeError.isEmpty();
    }

    public PersistenceEventsConfig applyTo(PersistenceEventsConfig yaml) {
        return new PersistenceEventsConfig(
                routeChange.orElse(yaml.routeChange()), probeError.orElse(yaml.probeError()));
    }
}

package io.pingui;

import io.pingui.config.PersistenceConfig;
import io.pingui.config.PersistenceEventsConfig;
import java.util.Optional;

/** CLI overrides for persistence; empty fields keep YAML values (SPIKE P11-002 § priority). */
public record CliPersistenceOverrides(Optional<Boolean> routeChange, Optional<Boolean> probeError) {

    public static CliPersistenceOverrides none() {
        return new CliPersistenceOverrides(Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return routeChange.isEmpty() && probeError.isEmpty();
    }

    public PersistenceConfig applyTo(PersistenceConfig yaml) {
        PersistenceEventsConfig events = new PersistenceEventsConfig(
                routeChange.orElse(yaml.routeChange()), probeError.orElse(yaml.probeError()));
        return yaml.withEvents(events);
    }
}

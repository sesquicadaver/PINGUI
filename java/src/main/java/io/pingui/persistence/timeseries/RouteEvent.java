package io.pingui.persistence.timeseries;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Route snapshot or route-change marker (Python RouteEvent parity). */
public record RouteEvent(String targetHost, List<String> routeIps, boolean routeChanged, Instant observedAt) {
    public RouteEvent {
        Objects.requireNonNull(targetHost, "targetHost");
        Objects.requireNonNull(routeIps, "routeIps");
        Objects.requireNonNull(observedAt, "observedAt");
        routeIps = List.copyOf(routeIps);
    }
}

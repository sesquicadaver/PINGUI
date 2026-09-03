package io.pingui.monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One compact row in a per-host incident timeline (P29-002). */
public record IncidentTimelineEntry(
        long id,
        IncidentTimelineKind kind,
        String state,
        Instant observedAt,
        Duration duration,
        String detail,
        Optional<RouteChangeEvent> routeReplay) {
    public IncidentTimelineEntry {
        Objects.requireNonNull(kind, "kind");
        state = state == null ? "" : state;
        Objects.requireNonNull(observedAt, "observedAt");
        duration = duration == null || duration.isNegative() ? Duration.ZERO : duration;
        detail = detail == null ? "" : detail;
        routeReplay = routeReplay == null ? Optional.empty() : routeReplay;
    }

    /** True when selecting this row should replay a route on the graph. */
    public boolean canReplayRoute() {
        return routeReplay.isPresent();
    }
}

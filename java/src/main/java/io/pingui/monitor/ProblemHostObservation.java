package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One degraded host input for {@link ProblemCorrelator} (P29-001). */
public record ProblemHostObservation(String host, List<HopNode> route, Instant problemStartedAt) {
    public ProblemHostObservation {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host required");
        }
        route = route == null ? List.of() : List.copyOf(route);
        Objects.requireNonNull(problemStartedAt, "problemStartedAt");
    }
}

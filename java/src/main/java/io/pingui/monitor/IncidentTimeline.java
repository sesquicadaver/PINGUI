package io.pingui.monitor;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Compact per-host incident timeline (P29-002). Entries are newest-first. */
public record IncidentTimeline(String host, List<IncidentTimelineEntry> entries, Duration totalIncidentDuration) {
    public IncidentTimeline {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host required");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        totalIncidentDuration = totalIncidentDuration == null || totalIncidentDuration.isNegative()
                ? Duration.ZERO
                : totalIncidentDuration;
    }
}

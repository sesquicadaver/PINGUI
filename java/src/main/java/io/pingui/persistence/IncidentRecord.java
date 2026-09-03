package io.pingui.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One row from {@code incident} (P30-002). Duration is derived from columns — no JSON parsing.
 */
public record IncidentRecord(
        long id,
        long hostId,
        String hostAddress,
        String kind,
        String severity,
        String state,
        Instant startedAt,
        Instant endedAt,
        Instant acknowledgedAt,
        int occurrences,
        Double peakValue,
        String detailsJson) {

    public static final String STATE_FIRING = "firing";
    public static final String STATE_RESOLVED = "resolved";

    public static final String KIND_ENDPOINT_DOWN = "endpoint_down";
    public static final String KIND_LATENCY_HIGH = "latency_high";

    public static final String SEVERITY_CRITICAL = "critical";
    public static final String SEVERITY_WARNING = "warning";

    public IncidentRecord {
        Objects.requireNonNull(hostAddress, "hostAddress");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(startedAt, "startedAt");
        if (occurrences < 1) {
            throw new IllegalArgumentException("occurrences must be >= 1");
        }
        detailsJson = detailsJson == null || detailsJson.isBlank() ? "{}" : detailsJson;
    }

    public boolean active() {
        return STATE_FIRING.equals(state) && endedAt == null;
    }

    /** Resolved duration, or empty while still firing / missing end. */
    public Optional<Duration> duration() {
        if (endedAt == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(startedAt, endedAt));
    }

    public static String severityForKind(String kind) {
        if (KIND_ENDPOINT_DOWN.equals(kind)) {
            return SEVERITY_CRITICAL;
        }
        return SEVERITY_WARNING;
    }
}

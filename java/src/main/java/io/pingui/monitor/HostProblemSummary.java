package io.pingui.monitor;

import java.time.Duration;
import java.time.Instant;

/**
 * Session-scoped quality problem view for the host-row badge (P22-002 / P23 /
 * ADR_HOST_PROBLEM_INDICATOR).
 */
public record HostProblemSummary(
        String host,
        String rule,
        boolean unread,
        int fireCount,
        Duration maxDuration,
        Instant lastStartedAt,
        Instant lastResolvedAt,
        String lastState,
        String description) {
    public static final String STATE_FIRING = "firing";
    public static final String STATE_RESOLVED = "resolved";
    public static final String STATE_OK = "ok";
    public static final String DESCRIPTION_ENDPOINT_DOWN = "endpoint_down: target unreachable";
    public static final String DESCRIPTION_LATENCY_HIGH = "latency_high: rtt ≥ 2×AVG";

    public HostProblemSummary {
        host = requireNonBlank(host, "host");
        rule = requireNonBlank(rule, "rule");
        if (fireCount < 0) {
            throw new IllegalArgumentException("fireCount must be >= 0");
        }
        maxDuration = maxDuration != null ? maxDuration : Duration.ZERO;
        if (maxDuration.isNegative()) {
            throw new IllegalArgumentException("maxDuration must be >= 0");
        }
        lastState = requireNonBlank(lastState, "lastState");
        if (!STATE_FIRING.equals(lastState) && !STATE_RESOLVED.equals(lastState) && !STATE_OK.equals(lastState)) {
            throw new IllegalArgumentException("lastState must be firing, resolved, or ok");
        }
        description = description == null || description.isBlank() ? DESCRIPTION_ENDPOINT_DOWN : description;
    }

    /** True when the host-row badge should be shown. */
    public boolean showBadge() {
        return unread;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " required");
        }
        return value;
    }
}

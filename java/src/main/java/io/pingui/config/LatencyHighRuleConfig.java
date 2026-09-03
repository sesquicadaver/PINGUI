package io.pingui.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Profile-level {@code latency_high} rule (P23 / P26-008 / ADR_ALERT_RULES v2).
 *
 * <p>Default signal: {@code rtt ≥ multiplier × AVG} where AVG is an EWMA of successful terminal
 * RTTs (engine α), with {@code fail_after} consecutive bad pings (no time window). Optional {@code
 * thresholdMs} adds an absolute OR condition.
 */
public record LatencyHighRuleConfig(
        boolean enabled, double multiplier, int failAfter, int clearAfter, int cooldownMinutes, Double thresholdMs) {
    public static final double DEFAULT_MULTIPLIER = 2.0;

    public LatencyHighRuleConfig {
        if (multiplier <= 0.0 || Double.isNaN(multiplier) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("multiplier must be > 0");
        }
        if (failAfter < 1) {
            throw new IllegalArgumentException("failAfter must be >= 1");
        }
        if (clearAfter < 1) {
            throw new IllegalArgumentException("clearAfter must be >= 1");
        }
        if (cooldownMinutes < 0) {
            throw new IllegalArgumentException("cooldownMinutes must be >= 0");
        }
        if (thresholdMs != null && (thresholdMs <= 0.0 || thresholdMs.isNaN() || thresholdMs.isInfinite())) {
            throw new IllegalArgumentException("thresholdMs must be > 0 when set");
        }
    }

    /** Disabled critical defaults (ADR). */
    public static LatencyHighRuleConfig disabled() {
        return critical(false);
    }

    /** Critical preset: 2×AVG, fail_after=3, clear_after=2, cooldown=15. */
    public static LatencyHighRuleConfig critical(boolean enabled) {
        return new LatencyHighRuleConfig(enabled, DEFAULT_MULTIPLIER, 3, 2, 15, null);
    }

    /**
     * Approximate wall time from the start of a consecutive high streak to the FIRING edge: {@code
     * fail_after × pollInterval} (after warm-up baseline exists; excludes cooldown gaps).
     *
     * @throws IllegalArgumentException if {@code pollInterval} is null, zero, or negative
     */
    public static Duration approximateFiringEta(int failAfter, Duration pollInterval) {
        if (failAfter < 1) {
            throw new IllegalArgumentException("failAfter must be >= 1");
        }
        Objects.requireNonNull(pollInterval, "pollInterval");
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval must be > 0");
        }
        return pollInterval.multipliedBy(failAfter);
    }

    /** ETA for this rule's {@link #failAfter()} and the given profile poll interval. */
    public Duration approximateFiringEta(Duration pollInterval) {
        return approximateFiringEta(failAfter, pollInterval);
    }

    public boolean isDefaultDisabled() {
        return equals(disabled());
    }

    public boolean hasAbsoluteThreshold() {
        return thresholdMs != null;
    }
}

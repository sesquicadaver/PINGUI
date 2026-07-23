package io.pingui.config;

/**
 * Profile-level {@code latency_high} rule (P23 / ADR_ALERT_RULES v2).
 *
 * <p>Default signal: {@code rtt ≥ multiplier × AVG} with {@code fail_after} consecutive bad pings
 * (no time window). Optional {@code thresholdMs} adds an absolute OR condition.
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

    public boolean isDefaultDisabled() {
        return equals(disabled());
    }

    public boolean hasAbsoluteThreshold() {
        return thresholdMs != null;
    }
}

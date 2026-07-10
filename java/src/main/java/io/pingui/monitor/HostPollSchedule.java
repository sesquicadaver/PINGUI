package io.pingui.monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

/** Per-host poll cadence by {@link HostProbeMode} (P13-020). */
public final class HostPollSchedule {
    /** Scheduler resolution for due checks (seconds). */
    public static final double TICK_SECONDS = 0.25;

    public static final double PING_ONLY_DEFAULT_SECONDS = 1.5;
    public static final double MTR_DEFAULT_SECONDS = 10.0;

    private HostPollSchedule() {}

    /** Mode-aware default when no per-host override is set. */
    public static double effectiveInterval(
            HostProbeMode mode, double profileIntervalSeconds, OptionalDouble hostOverride) {
        if (hostOverride.isPresent()) {
            double value = hostOverride.getAsDouble();
            if (value <= 0) {
                throw new IllegalArgumentException("interval must be positive");
            }
            return value;
        }
        return switch (mode) {
            case PING_ONLY -> PING_ONLY_DEFAULT_SECONDS;
            case MTR -> MTR_DEFAULT_SECONDS;
            case TRACE -> profileIntervalSeconds;
        };
    }

    public static boolean isDue(Instant lastPollAt, Instant now, double intervalSeconds) {
        if (lastPollAt == null) {
            return true;
        }
        long elapsedMs = Duration.between(lastPollAt, now).toMillis();
        return elapsedMs >= Math.max(1L, Math.round(intervalSeconds * 1000.0));
    }
}

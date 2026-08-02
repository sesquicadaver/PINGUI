package io.pingui.monitor;

/**
 * Per-host poll liveness counters for the current probe mode (session-ephemeral).
 *
 * <p>{@code attempts} counts every completed poll (success or failure); {@code errors} counts
 * probe failures. Reset when the host probe mode changes — not when selecting another list row.
 */
public record HostPollCounters(long attempts, long errors) {
    public static final HostPollCounters ZERO = new HostPollCounters(0, 0);

    public HostPollCounters {
        if (attempts < 0 || errors < 0) {
            throw new IllegalArgumentException("attempts/errors must be >= 0");
        }
        if (errors > attempts) {
            throw new IllegalArgumentException("errors must be <= attempts");
        }
    }

    /** Error rate in percent of attempts; {@code 0} when no attempts yet. */
    public double errorPct() {
        if (attempts == 0) {
            return 0.0;
        }
        return errors * 100.0 / attempts;
    }
}

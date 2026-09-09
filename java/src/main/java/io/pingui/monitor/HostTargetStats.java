package io.pingui.monitor;

/**
 * Aggregated ping metrics for the terminal hop of a monitored host.
 *
 * @param lossPct sliding-window loss percent
 * @param minMs minimum RTT in the sample window ({@code null} when empty)
 * @param avgMs average RTT in the sample window ({@code null} when empty)
 * @param maxMs maximum RTT in the sample window ({@code null} when empty)
 * @param timeout {@code true} when the current target sample timed out / is unreachable
 * @param lastMs current (latest) RTT sample when the target is reachable; otherwise {@code null}
 */
public record HostTargetStats(
        double lossPct, Double minMs, Double avgMs, Double maxMs, boolean timeout, Double lastMs) {
    /** Backward-compatible ctor without an explicit current sample. */
    public HostTargetStats(double lossPct, Double minMs, Double avgMs, Double maxMs, boolean timeout) {
        this(lossPct, minMs, avgMs, maxMs, timeout, null);
    }
}

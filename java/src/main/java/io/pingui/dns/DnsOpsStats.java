package io.pingui.dns;

/**
 * Operator-visible forward-DNS queue counters (P34-007).
 *
 * @param queueCapacity bounded executor queue size
 * @param queued current tasks waiting in the executor queue
 * @param inFlight hosts with an active lookup (includes coalesced waiters' shared future)
 * @param rejectedCount submissions refused because the queue was full
 * @param droppedCount alias of rejected submissions (lossy overflow for ops status)
 * @param coalescedCount lookups that joined an in-flight resolve for the same host
 * @param timeoutCount lookups that hit the hard Future timeout
 */
public record DnsOpsStats(
        int queueCapacity,
        int queued,
        int inFlight,
        long rejectedCount,
        long droppedCount,
        long coalescedCount,
        long timeoutCount) {

    public static DnsOpsStats empty(int queueCapacity) {
        return new DnsOpsStats(Math.max(0, queueCapacity), 0, 0, 0L, 0L, 0L, 0L);
    }

    /** True when the operator should notice DNS pressure. */
    public boolean hasPressure() {
        return rejectedCount > 0L || droppedCount > 0L || timeoutCount > 0L;
    }
}

package io.pingui.dns;

/**
 * Combined forward-resolve and DNS-control outer-queue counters for operators (P34-007 / P35-005).
 *
 * @param resolve inner {@link BoundedForwardDnsLookup} queue
 * @param control outer {@link DnsControlDispatcher} backlog (one pending job per host)
 */
public record DnsOpsSnapshot(DnsOpsStats resolve, DnsOpsStats control) {
    public DnsOpsSnapshot {
        resolve = resolve != null ? resolve : DnsOpsStats.empty(0);
        control = control != null ? control : DnsOpsStats.empty(0);
    }

    public static DnsOpsSnapshot empty() {
        return new DnsOpsSnapshot(DnsOpsStats.empty(0), DnsOpsStats.empty(0));
    }

    /** True when either layer signals overflow / timeout pressure. */
    public boolean hasPressure() {
        return resolve.hasPressure() || control.hasPressure();
    }

    /** Aggregate drop/reject/timeout counts for the compact App Status line. */
    public long droppedTotal() {
        return resolve.droppedCount() + control.droppedCount();
    }

    public long rejectedTotal() {
        return resolve.rejectedCount() + control.rejectedCount();
    }

    public long timeoutTotal() {
        return resolve.timeoutCount() + control.timeoutCount();
    }
}

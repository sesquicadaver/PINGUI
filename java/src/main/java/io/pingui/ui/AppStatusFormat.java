package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import java.time.Duration;
import java.time.Instant;

/** Formats the durable monitoring summary line (P31-006 / pingui-evo-gui §6). */
public final class AppStatusFormat {
    private AppStatusFormat() {}

    /**
     * Builds {@code Monitoring: active · 8/10 enabled · Last cycle: 2 s ago} and optional DNS ops
     * pressure (P31-006 / P34-007 / P35-005).
     */
    public static String monitoring(
            boolean active, int enabledCount, int totalCount, Instant lastCycleAt, Instant now) {
        return monitoring(active, enabledCount, totalCount, lastCycleAt, now, (io.pingui.dns.DnsOpsSnapshot) null);
    }

    public static String monitoring(
            boolean active,
            int enabledCount,
            int totalCount,
            Instant lastCycleAt,
            Instant now,
            io.pingui.dns.DnsOpsStats dnsOps) {
        return monitoring(
                active,
                enabledCount,
                totalCount,
                lastCycleAt,
                now,
                dnsOps == null ? null : new io.pingui.dns.DnsOpsSnapshot(dnsOps, io.pingui.dns.DnsOpsStats.empty(0)));
    }

    public static String monitoring(
            boolean active,
            int enabledCount,
            int totalCount,
            Instant lastCycleAt,
            Instant now,
            io.pingui.dns.DnsOpsSnapshot dnsOps) {
        String state = active ? UiI18n.get("status.mon.active") : UiI18n.get("status.mon.inactive");
        String cycle = formatCycleAge(lastCycleAt, now != null ? now : Instant.now());
        String base =
                UiI18n.get("status.mon.summary", state, Math.max(0, enabledCount), Math.max(0, totalCount), cycle);
        if (dnsOps == null || !dnsOps.hasPressure()) {
            return base;
        }
        return base
                + " · "
                + UiI18n.get(
                        "status.mon.dns_ops", dnsOps.droppedTotal(), dnsOps.rejectedTotal(), dnsOps.timeoutTotal());
    }

    static String formatCycleAge(Instant lastCycleAt, Instant now) {
        if (lastCycleAt == null) {
            return UiI18n.get("status.mon.cycle_na");
        }
        long seconds = Math.max(0L, Duration.between(lastCycleAt, now).getSeconds());
        return UiI18n.get("status.mon.cycle_ago", seconds);
    }
}

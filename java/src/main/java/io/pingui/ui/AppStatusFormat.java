package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import java.time.Duration;
import java.time.Instant;

/** Formats the durable monitoring summary line (P31-006 / pingui-evo-gui §6). */
public final class AppStatusFormat {
    private AppStatusFormat() {}

    /**
     * Builds {@code Monitoring: active · 8/10 enabled · Last cycle: 2 s ago}.
     *
     * @param active monitor scheduler still running
     * @param enabledCount enabled hosts
     * @param totalCount hosts in session
     * @param lastCycleAt latest successful poll instant, or {@code null}
     * @param now clock for relative age
     */
    public static String monitoring(
            boolean active, int enabledCount, int totalCount, Instant lastCycleAt, Instant now) {
        String state = active ? UiI18n.get("status.mon.active") : UiI18n.get("status.mon.inactive");
        String cycle = formatCycleAge(lastCycleAt, now != null ? now : Instant.now());
        return UiI18n.get("status.mon.summary", state, Math.max(0, enabledCount), Math.max(0, totalCount), cycle);
    }

    static String formatCycleAge(Instant lastCycleAt, Instant now) {
        if (lastCycleAt == null) {
            return UiI18n.get("status.mon.cycle_na");
        }
        long seconds = Math.max(0L, Duration.between(lastCycleAt, now).getSeconds());
        return UiI18n.get("status.mon.cycle_ago", seconds);
    }
}

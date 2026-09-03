package io.pingui.monitor;

/**
 * Unified severity for host row, badge, timeline, alerts, and future sort (P31-004 /
 * pingui-evo-gui §4).
 *
 * <p>{@link #sortRank()} is ascending by urgency (0 = most severe) for problems-first ordering.
 */
public enum Severity {
    CRITICAL(0),
    WARNING(1),
    NOTICE(2),
    INFO(3),
    MUTED(4);

    private final int sortRank;

    Severity(int sortRank) {
        this.sortRank = sortRank;
    }

    /** Lower rank sorts first when problems-first is enabled (P31-005). */
    public int sortRank() {
        return sortRank;
    }
}

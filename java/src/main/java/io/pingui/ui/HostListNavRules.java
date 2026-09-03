package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.EndpointState;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.ToIntFunction;

/** Pure filter/sort/counter rules for the host list (P31-005). */
public final class HostListNavRules {
    private HostListNavRules() {}

    /** Case-insensitive match on address and tag text. */
    public static boolean matchesText(HostItem item, String query) {
        if (item == null) {
            return false;
        }
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        String host = item.getHost() != null ? item.getHost().toLowerCase(Locale.ROOT) : "";
        if (host.contains(needle)) {
            return true;
        }
        String tags = item.tagsTextProperty().get();
        return tags != null && tags.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Comparator for visible rows.
     *
     * @param configIndex stable config-order index (lower first)
     */
    public static Comparator<HostItem> comparator(
            HostListSortMode mode, boolean problemsFirst, ToIntFunction<HostItem> configIndex) {
        HostListSortMode safe = mode != null ? mode : HostListSortMode.CONFIG;
        ToIntFunction<HostItem> index = configIndex != null ? configIndex : item -> 0;
        return (a, b) -> {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            int cmp = 0;
            if (problemsFirst) {
                cmp = Integer.compare(a.severity().sortRank(), b.severity().sortRank());
            }
            if (cmp == 0) {
                cmp = switch (safe) {
                    case CONFIG -> Integer.compare(index.applyAsInt(a), index.applyAsInt(b));
                    case SEVERITY -> Integer.compare(
                            a.severity().sortRank(), b.severity().sortRank());
                    case RTT -> compareOptionalAsc(a.avgRttMs(), b.avgRttMs());
                    case LOSS -> compareOptionalAsc(a.lossPct(), b.lossPct());
                    case LAST_CHANGE -> Long.compare(b.lastRouteChangeEpochMs(), a.lastRouteChangeEpochMs());
                };
            }
            if (cmp == 0) {
                cmp = Integer.compare(index.applyAsInt(a), index.applyAsInt(b));
            }
            if (cmp == 0) {
                String ha = a.getHost() != null ? a.getHost() : "";
                String hb = b.getHost() != null ? b.getHost() : "";
                cmp = ha.compareToIgnoreCase(hb);
            }
            return cmp;
        };
    }

    /** Counts endpoint DOWN / DEGRADED among enabled hosts. */
    public static EndpointCounts countEndpoints(Iterable<HostItem> items) {
        int total = 0;
        int down = 0;
        int degraded = 0;
        if (items != null) {
            for (HostItem item : items) {
                if (item == null) {
                    continue;
                }
                total++;
                if (!item.isEnabled()) {
                    continue;
                }
                EndpointState state = item.endpointState();
                if (state == EndpointState.DOWN) {
                    down++;
                } else if (state == EndpointState.DEGRADED) {
                    degraded++;
                }
            }
        }
        return new EndpointCounts(total, down, degraded);
    }

    public static String formatCounters(EndpointCounts counts) {
        EndpointCounts safe = counts != null ? counts : new EndpointCounts(0, 0, 0);
        return UiI18n.get("host.counters", safe.total(), safe.down(), safe.degraded());
    }

    /** Null / missing numeric metrics sort after known values (ascending). */
    static int compareOptionalAsc(Double a, Double b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return Double.compare(a, b);
    }

    /** Snapshot for header counters. */
    public record EndpointCounts(int total, int down, int degraded) {}
}

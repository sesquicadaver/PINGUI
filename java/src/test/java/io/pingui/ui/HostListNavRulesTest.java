package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostPollCounters;
import io.pingui.monitor.HostTargetStats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostListNavRulesTest {
    @Test
    void matchesTextOnHostAndTags() {
        HostItem item = new HostItem("8.8.8.8", true, false, List.of("dc", "vpn"));
        assertTrue(HostListNavRules.matchesText(item, "8.8"));
        assertTrue(HostListNavRules.matchesText(item, "VPN"));
        assertTrue(HostListNavRules.matchesText(item, ""));
        assertFalse(HostListNavRules.matchesText(item, "missing"));
    }

    @Test
    void problemsFirstThenSeverityOrdersCriticalBeforeInfo() {
        HostItem critical = new HostItem("down.example", true);
        critical.applyMetrics(new HostTargetStats(100.0, null, null, null, true), HostPollCounters.ZERO);
        HostItem ok = new HostItem("ok.example", true);
        ok.applyMetrics(new HostTargetStats(0.0, 1.0, 2.0, 3.0, false), new HostPollCounters(1, 0));

        Comparator<HostItem> cmp = HostListNavRules.comparator(
                HostListSortMode.CONFIG, true, item -> item.getHost().startsWith("ok") ? 0 : 1);
        List<HostItem> rows = new ArrayList<>(List.of(ok, critical));
        rows.sort(cmp);
        assertEquals("down.example", rows.get(0).getHost());
    }

    @Test
    void rttSortAscendingPutsLowerFirst() {
        HostItem slow = new HostItem("slow", true);
        slow.applyMetrics(new HostTargetStats(0.0, 40.0, 50.0, 60.0, false), new HostPollCounters(1, 0));
        HostItem fast = new HostItem("fast", true);
        fast.applyMetrics(new HostTargetStats(0.0, 1.0, 5.0, 9.0, false), new HostPollCounters(1, 0));

        Comparator<HostItem> cmp = HostListNavRules.comparator(HostListSortMode.RTT, false, item -> 0);
        List<HostItem> rows = new ArrayList<>(List.of(slow, fast));
        rows.sort(cmp);
        assertEquals("fast", rows.get(0).getHost());
    }

    @Test
    void lastChangeSortPutsRecentFirst() {
        HostItem older = new HostItem("older", true);
        older.markRouteChanged();
        HostItem newer = new HostItem("newer", true);
        newer.markRouteChanged();
        assertTrue(newer.lastRouteChangeEpochMs() >= older.lastRouteChangeEpochMs());

        Comparator<HostItem> cmp = HostListNavRules.comparator(HostListSortMode.LAST_CHANGE, false, item -> 0);
        List<HostItem> rows = new ArrayList<>(List.of(older, newer));
        rows.sort(cmp);
        assertEquals("newer", rows.get(0).getHost());
    }

    @Test
    void countEndpointsTalliesDownAndDegraded() {
        HostItem down = new HostItem("down", true);
        down.applyMetrics(new HostTargetStats(100.0, null, null, null, true), HostPollCounters.ZERO);
        HostItem degraded = new HostItem("deg", true);
        degraded.applyMetrics(new HostTargetStats(15.0, 10.0, 20.0, 30.0, false), new HostPollCounters(1, 0));
        HostItem up = new HostItem("up", true);
        up.applyMetrics(new HostTargetStats(0.0, 1.0, 2.0, 3.0, false), new HostPollCounters(1, 0));
        HostItem disabled = new HostItem("off", false);

        HostListNavRules.EndpointCounts counts = HostListNavRules.countEndpoints(List.of(down, degraded, up, disabled));
        assertEquals(4, counts.total());
        assertEquals(1, counts.down());
        assertEquals(1, counts.degraded());
        String formatted = HostListNavRules.formatCounters(counts);
        assertTrue(formatted.contains("4"));
        assertTrue(formatted.contains("1"));
    }
}

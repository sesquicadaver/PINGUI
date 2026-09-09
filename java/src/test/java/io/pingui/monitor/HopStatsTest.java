package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class HopStatsTest {
    @Test
    void lossInWindowRequiresTwoProbes() {
        HopProbeStats stats = new HopProbeStats();
        HopStats.recordProbe(stats, new HopNode(1, "1.1.1.1", 10.0, false));
        assertNull(HopStats.lossPctInWindow(stats));
        assertNull(HopStats.summarizeForPollResult(stats).lossPct());
        HopStats.recordProbe(stats, Models.timeout(1));
        assertEquals(50.0, HopStats.lossPctInWindow(stats));
        assertEquals(50.0, HopStats.summarizeForPollResult(stats).lossPct());
    }

    @Test
    void lossUsesSlidingWindowNotLifetimeCounters() {
        HopProbeStats stats = new HopProbeStats();
        // Fill a full success window, then roll in enough timeouts to drop old successes.
        for (int i = 0; i < HopStats.LOSS_WINDOW_SIZE; i++) {
            HopStats.recordProbe(stats, new HopNode(1, "1.1.1.1", 10.0, false));
        }
        assertEquals(0.0, HopStats.lossPctInWindow(stats));
        assertEquals(HopStats.LOSS_WINDOW_SIZE, stats.getProbes());
        for (int i = 0; i < HopStats.LOSS_WINDOW_SIZE; i++) {
            HopStats.recordProbe(stats, Models.timeout(1));
        }
        // Lifetime still shows 50% (50 ok + 50 fail), but the window is 100% loss.
        assertEquals(2 * HopStats.LOSS_WINDOW_SIZE, stats.getProbes());
        assertEquals(HopStats.LOSS_WINDOW_SIZE, stats.getSuccesses());
        assertEquals(100.0, HopStats.lossPctInWindow(stats));
        assertEquals(100.0, HopStats.lossPct(stats));
        assertEquals(HopStats.LOSS_WINDOW_SIZE, stats.getWindowProbes());
        assertEquals(0, stats.getWindowSuccesses());
    }

    @Test
    void copyPreservesAttemptWindow() {
        HopProbeStats stats = new HopProbeStats();
        HopStats.recordProbe(stats, new HopNode(1, "1.1.1.1", 10.0, false));
        HopStats.recordProbe(stats, Models.timeout(1));
        HopProbeStats copy = stats.copy();
        assertEquals(50.0, HopStats.lossPctInWindow(copy));
        HopStats.recordProbe(copy, new HopNode(1, "1.1.1.1", 11.0, false));
        assertEquals(50.0, HopStats.lossPctInWindow(stats));
        assertEquals(1.0 / 3.0 * 100.0, HopStats.lossPctInWindow(copy), 1e-9);
    }

    @Test
    void jitterIsPopulationStddevOverRttWindow() {
        assertNull(HopStats.jitterMs(List.of()));
        assertNull(HopStats.jitterMs(List.of(10.0)));
        assertEquals(2.0, HopStats.jitterMs(List.of(10.0, 14.0)));
    }

    @Test
    void lossCountsTimeouts() {
        HopProbeStats stats = new HopProbeStats();
        HopStats.recordProbe(stats, new HopNode(1, "1.1.1.1", 10.0, false));
        HopStats.recordProbe(stats, Models.timeout(1));
        assertEquals(50.0, HopStats.lossPct(stats));
    }

    @Test
    void sessionStoreRecordsHopStats() {
        SessionStore store = new SessionStore(List.of("h"));
        store.appendPingSamples(
                "h",
                new RouteSnapshot("h", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 5.0, false), Models.timeout(2))));
        HopStatsSummary hop1 = store.hopStatsSummary("h", 1);
        HopStatsSummary hop2 = store.hopStatsSummary("h", 2);
        assertNotNull(hop1);
        assertEquals(0.0, hop1.lossPct());
        assertNotNull(hop2);
        assertEquals(100.0, hop2.lossPct());
    }

    @Test
    void targetStatsAggregatesTerminalHop() {
        HopProbeStats stats = new HopProbeStats();
        HopStats.recordProbe(stats, new HopNode(1, "8.8.8.8", 10.0, false));
        HopStats.recordProbe(stats, new HopNode(1, "8.8.8.8", 20.0, false));
        var result = HopStats.targetStats(new HopNode(1, "8.8.8.8", 15.0, false), stats);
        assertNotNull(result);
        assertEquals(0.0, result.lossPct());
        assertEquals(10.0, result.minMs());
        assertEquals(20.0, result.maxMs());
        assertEquals(15.0, result.avgMs());
        assertEquals(15.0, result.lastMs());
        assertFalse(result.timeout());
    }

    @Test
    void targetStatsNullWhenNoProbes() {
        assertNull(HopStats.targetStats(new HopNode(1, "8.8.8.8", null, false), new HopProbeStats()));
    }
}

package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void jitterRequiresTwoSamples() {
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
}

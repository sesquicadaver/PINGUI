package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteHistoryTest {
    @Test
    void routeWithLastKnownIpsReplacesTimeout() {
        Map<Integer, HopNode> lastKnown = new HashMap<>();
        lastKnown.put(2, new HopNode(2, "2.2.2.2", 20.0, false));
        List<HopNode> route = List.of(new HopNode(1, "1.1.1.1", 10.0, false), Models.timeout(2));
        List<HopNode> enriched = RouteHistory.routeWithLastKnownIps(route, lastKnown);
        assertEquals("2.2.2.2", enriched.get(1).ip());
    }
}

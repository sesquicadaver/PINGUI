package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteHistoryPresenterTest {
    @Test
    void ipsToRouteBuildsReachableHopNodes() {
        List<io.pingui.model.Models.HopNode> nodes = RouteHistoryPresenter.ipsToRoute(List.of("10.0.0.1", "8.8.8.8"));
        assertEquals(2, nodes.size());
        assertEquals(1, nodes.get(0).hop());
        assertEquals("10.0.0.1", nodes.get(0).ip());
        assertEquals(2, nodes.get(1).hop());
        assertEquals("8.8.8.8", nodes.get(1).ip());
    }
}

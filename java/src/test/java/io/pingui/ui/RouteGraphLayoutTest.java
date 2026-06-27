package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.ui.RouteGraphLayout.GraphNode;
import io.pingui.ui.RouteGraphLayout.GraphScene;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteGraphLayoutTest {
    @Test
    void dualColumnsDoNotOverlap() {
        List<HopNode> prev = List.of(new HopNode(1, "9.9.9.9", 1.0, false));
        List<HopNode> current = List.of(new HopNode(1, "1.1.1.1", 1.0, false));
        GraphScene scene = RouteGraphLayout.buildScene(current, prev, ip -> null);
        List<GraphNode> inactive =
                scene.nodes().stream().filter(n -> n.id().startsWith("prev_")).toList();
        List<GraphNode> active =
                scene.nodes().stream().filter(n -> n.id().startsWith("act_")).toList();
        assertTrue(RouteGraphLayout.columnsSeparated(inactive, active));
    }

    @Test
    void emptyRouteProducesEmptyScene() {
        GraphScene scene = RouteGraphLayout.buildScene(List.of(), List.of(), ip -> null);
        assertTrue(scene.nodes().isEmpty());
    }

    @Test
    void routesDifferDetectsChange() {
        List<HopNode> a = List.of(new HopNode(1, "10.0.0.1", 1.0, false));
        List<HopNode> b = List.of(new HopNode(1, "192.168.1.1", 1.0, false));
        assertTrue(RouteGraphLayout.routesDiffer(b, a));
        assertFalse(RouteGraphLayout.routesDiffer(a, a));
    }

    @Test
    void verticalLayoutTopToBottom() {
        var pair = RouteGraphLayout.columnLayouts(false);
        assertNotNull(pair.active());
        List<Double> ys = RouteGraphLayout.layoutYForChain(3);
        assertEquals(3, ys.size());
        assertTrue(ys.get(0) > ys.get(1));
        assertTrue(ys.get(1) > ys.get(2));
    }
}

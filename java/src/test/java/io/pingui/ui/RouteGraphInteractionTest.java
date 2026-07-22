package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.dns.DnsResolver;
import io.pingui.model.Models.HopNode;
import io.pingui.ui.RouteGraphInteraction.ViewTransform;
import io.pingui.ui.RouteGraphLayout.GraphNode;
import io.pingui.ui.RouteGraphLayout.GraphScene;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouteGraphInteractionTest {
    @BeforeEach
    void disableLiveRdns() {
        DnsResolver.configureForTests(true, Duration.ofMinutes(5), java.time.Clock.systemUTC(), addr -> null);
    }

    @AfterEach
    void resetRdns() {
        DnsResolver.resetForTests();
    }

    @Test
    void zoomAtKeepsWorldPointStableAndClamps() {
        ViewTransform base = ViewTransform.identity();
        ViewTransform zoomed = base.zoomAt(100, 50, RouteGraphInteraction.ZOOM_STEP);
        assertTrue(zoomed.zoom() > 1.0);
        assertEquals(100.0, zoomed.toWorldX(100), 1e-9);
        assertEquals(50.0, zoomed.toWorldY(50), 1e-9);

        ViewTransform maxed = zoomed;
        for (int i = 0; i < 40; i++) {
            maxed = maxed.zoomAt(100, 50, RouteGraphInteraction.ZOOM_STEP);
        }
        assertEquals(RouteGraphInteraction.MAX_ZOOM, maxed.zoom(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new ViewTransform(0.1, 0, 0));
    }

    @Test
    void findNodeAtHitsHopCenterAndBuildsTooltip() {
        GraphScene scene =
                RouteGraphLayout.buildScene(List.of(new HopNode(1, "8.8.8.8", 5.0, false)), List.of(), ip -> 5.0);
        GraphNode hop = scene.nodes().stream()
                .filter(node -> "8.8.8.8".equals(node.hopIp()))
                .findFirst()
                .orElseThrow();
        double width = 400;
        double height = 300;
        double worldX = hop.x() * width;
        double worldY = hop.y() * height;
        Optional<GraphNode> hit = RouteGraphInteraction.findNodeAt(scene, width, height, worldX, worldY);
        assertTrue(hit.isPresent());
        assertEquals("8.8.8.8", hit.get().hopIp());
        String tip = RouteGraphInteraction.tooltipFor(hit.get());
        assertTrue(tip.contains("8.8.8.8") || tip.contains("Hop 1"));
        assertTrue(tip.contains("Подвійний клік"));
        assertFalse(RouteGraphInteraction.findNodeAt(scene, width, height, 0, 0).isPresent());
    }
}

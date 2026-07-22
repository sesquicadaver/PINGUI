package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteDiffTest {

    @Test
    void compareDetectsChangedAddedRemoved() {
        List<HopNode> oldRoute =
                List.of(new HopNode(1, "10.0.0.1", 5.0, false), new HopNode(2, "8.8.8.8", 12.0, false));
        List<HopNode> newRoute = List.of(
                new HopNode(1, "192.168.1.1", 7.5, false),
                new HopNode(2, "8.8.8.8", 11.0, false),
                new HopNode(3, "1.1.1.1", 20.0, false));

        List<RouteDiff.Row> rows = RouteDiff.compare(oldRoute, newRoute);
        assertEquals(3, rows.size());
        assertEquals(RouteDiff.Kind.CHANGED, rows.get(0).kind());
        assertEquals(2.5, rows.get(0).deltaRttMs());
        assertEquals(RouteDiff.Kind.UNCHANGED, rows.get(1).kind());
        assertEquals(RouteDiff.Kind.ADDED, rows.get(2).kind());
        assertTrue(RouteDiff.hasChanges(rows));
    }

    @Test
    void compareDetectsRemovedHops() {
        List<HopNode> oldRoute = List.of(new HopNode(1, "10.0.0.1", 1.0, false), new HopNode(2, "8.8.8.8", 2.0, false));
        List<HopNode> newRoute = List.of(new HopNode(1, "10.0.0.1", 1.0, false));
        List<RouteDiff.Row> rows = RouteDiff.compare(oldRoute, newRoute);
        assertEquals(RouteDiff.Kind.REMOVED, rows.get(1).kind());
        assertTrue(rows.get(1).summary().contains("→ —"));
        assertTrue(RouteDiffStyle.cellText(rows.get(1)).startsWith("− "));
    }

    @Test
    void deltaRttNullWhenMissing() {
        RouteDiff.Row row = new RouteDiff.Row(1, "a", "b", null, 5.0, RouteDiff.Kind.CHANGED);
        assertNull(row.deltaRttMs());
        assertFalse(row.summary().contains("Δ"));
    }

    @Test
    void emptyRoutesProduceEmptyDiff() {
        assertTrue(RouteDiff.compare(List.of(), List.of()).isEmpty());
        assertFalse(RouteDiff.hasChanges(List.of()));
    }

    @Test
    void summaryShowsWasToBecame() {
        RouteDiff.Row row = new RouteDiff.Row(1, "10.0.0.1", "192.168.0.1", 4.0, 6.0, RouteDiff.Kind.CHANGED);
        String summary = row.summary();
        assertTrue(summary.contains("10.0.0.1 → 192.168.0.1"));
        assertTrue(summary.contains("Δ+2.0 ms"));
    }
}

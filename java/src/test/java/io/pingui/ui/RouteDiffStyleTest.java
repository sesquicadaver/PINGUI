package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

class RouteDiffStyleTest {
    @Test
    void prefixesAreDistinctPerKind() {
        assertEquals("= ", RouteDiffStyle.prefix(RouteDiff.Kind.UNCHANGED));
        assertEquals("~ ", RouteDiffStyle.prefix(RouteDiff.Kind.CHANGED));
        assertEquals("+ ", RouteDiffStyle.prefix(RouteDiff.Kind.ADDED));
        assertEquals("− ", RouteDiffStyle.prefix(RouteDiff.Kind.REMOVED));
    }

    @Test
    void textFillsDifferForChangeKinds() {
        Color changed = RouteDiffStyle.textFill(RouteDiff.Kind.CHANGED);
        Color added = RouteDiffStyle.textFill(RouteDiff.Kind.ADDED);
        Color removed = RouteDiffStyle.textFill(RouteDiff.Kind.REMOVED);
        Color unchanged = RouteDiffStyle.textFill(RouteDiff.Kind.UNCHANGED);
        assertNotEquals(changed, added);
        assertNotEquals(added, removed);
        assertNotEquals(removed, changed);
        assertNotEquals(unchanged, changed);
    }

    @Test
    void cellTextStartsWithPrefixThenSummary() {
        RouteDiff.Row row = new RouteDiff.Row(1, "10.0.0.1", "192.168.0.1", 4.0, 6.0, RouteDiff.Kind.CHANGED);
        String cell = RouteDiffStyle.cellText(row);
        assertTrue(cell.startsWith("~ "));
        assertTrue(cell.contains(row.summary()));
        assertTrue(cell.startsWith(RouteDiffStyle.prefix(row.kind())));
    }

    @Test
    void nullKindAndRowAreSafe() {
        assertEquals("", RouteDiffStyle.prefix(null));
        assertEquals(Color.BLACK, RouteDiffStyle.textFill(null));
        assertEquals(null, RouteDiffStyle.cellText(null));
    }
}

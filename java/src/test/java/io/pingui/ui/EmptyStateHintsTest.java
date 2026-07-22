package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmptyStateHintsTest {
    @Test
    void noSqliteHintPointsToDatabaseMenu() {
        String hint = EmptyStateHints.noSqlite();
        assertTrue(hint.contains("SQLite"));
        assertTrue(hint.contains("База даних"));
    }

    @Test
    void simpleNoLogPointsToExtended() {
        assertTrue(EmptyStateHints.simpleNoLog().contains("Розширений"));
    }

    @Test
    void emptyHistoryAndNoHostAreDistinct() {
        assertTrue(EmptyStateHints.emptyHistory().contains("маршруту"));
        assertTrue(EmptyStateHints.noHostSelected().contains("ціль"));
        assertFalse(EmptyStateHints.emptyHistory().equals(EmptyStateHints.noHostSelected()));
    }

    @Test
    void replaceableSimpleStatusAllowsIdleOnly() {
        assertTrue(EmptyStateHints.isReplaceableSimpleStatus(null));
        assertTrue(EmptyStateHints.isReplaceableSimpleStatus(""));
        assertTrue(EmptyStateHints.isReplaceableSimpleStatus(EmptyStateHints.waitingForData()));
        assertTrue(EmptyStateHints.isReplaceableSimpleStatus(EmptyStateHints.simpleNoLog()));
        assertFalse(EmptyStateHints.isReplaceableSimpleStatus("Додано ціль: 8.8.8.8"));
        assertFalse(EmptyStateHints.isReplaceableSimpleStatus("Probe [x]: fail"));
    }
}

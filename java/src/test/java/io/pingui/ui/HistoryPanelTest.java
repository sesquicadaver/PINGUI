package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.ui.view.HistoryPanel;
import org.junit.jupiter.api.Test;

class HistoryPanelTest {
    @Test
    void withoutPersistenceShowsOnlyDbHint() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            HistoryPanel panel = new HistoryPanel();
            panel.setPersistenceEnabled(false);
            assertTrue(panel.dbHintLabel().isVisible());
            assertTrue(panel.dbHintLabel().isManaged());
            assertTrue(panel.dbHintLabel().getText().equals(EmptyStateHints.noSqlite()));
            assertFalse(panel.historyLabel().isVisible());
            assertFalse(panel.historyList().isVisible());
            assertFalse(panel.historyFilterBar().isVisible());
            assertFalse(panel.historyRangeBar().isVisible());
        });
    }

    @Test
    void withPersistenceShowsHistoryChrome() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            HistoryPanel panel = new HistoryPanel();
            panel.setPersistenceEnabled(true);
            assertFalse(panel.dbHintLabel().isVisible());
            assertFalse(panel.dbHintLabel().isManaged());
            assertTrue(panel.historyLabel().isVisible());
            assertTrue(panel.historyList().isVisible());
            assertTrue(panel.historyFilterBar().isVisible());
            assertTrue(panel.historyRangeBar().isVisible());
        });
    }
}

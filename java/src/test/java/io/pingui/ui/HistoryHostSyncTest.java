package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.Test;

class HistoryHostSyncTest {

    @Test
    void shouldRefreshHistoryOnlyForFilteredHost() {
        assertTrue(HistoryHostSync.shouldRefreshHistoryForRouteChange("8.8.8.8", "8.8.8.8"));
        assertFalse(HistoryHostSync.shouldRefreshHistoryForRouteChange("1.1.1.1", "8.8.8.8"));
        assertFalse(HistoryHostSync.shouldRefreshHistoryForRouteChange("8.8.8.8", null));
        assertFalse(HistoryHostSync.shouldRefreshHistoryForRouteChange("8.8.8.8", "  "));
    }

    @Test
    void findItemReturnsMatchingHost() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            var items = FXCollections.observableArrayList(new HostItem("8.8.8.8", true), new HostItem("1.1.1.1", true));
            assertEquals("1.1.1.1", HistoryHostSync.findItem(items, "1.1.1.1").getHost());
            assertNull(HistoryHostSync.findItem(items, "9.9.9.9"));
        });
    }

    @Test
    void syncHostListFromFilterSelectsMatchingRow() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            var items = FXCollections.observableArrayList(new HostItem("8.8.8.8", true), new HostItem("1.1.1.1", true));
            ListView<HostItem> hostList = new ListView<>(items);
            hostList.getSelectionModel().select(0);

            HistoryHostSync sync = new HistoryHostSync();
            sync.syncHostListFromFilter("1.1.1.1", items, hostList);

            assertEquals(
                    "1.1.1.1", hostList.getSelectionModel().getSelectedItem().getHost());
            assertFalse(sync.isSyncing());
        });
    }

    @Test
    void syncFilterFromHostListUpdatesComboValue() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            final String[] filter = {"8.8.8.8"};
            HistoryHostSync sync = new HistoryHostSync();
            sync.syncFilterFromHostList("1.1.1.1", filter[0], value -> filter[0] = value);
            assertEquals("1.1.1.1", filter[0]);
            assertFalse(sync.isSyncing());
        });
    }

    @Test
    void syncSkipsWhenAlreadyAligned() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            var items = FXCollections.observableArrayList(new HostItem("8.8.8.8", true));
            ListView<HostItem> hostList = new ListView<>(items);
            hostList.getSelectionModel().select(0);

            HistoryHostSync sync = new HistoryHostSync();
            sync.syncHostListFromFilter("8.8.8.8", items, hostList);
            assertNotNull(hostList.getSelectionModel().getSelectedItem());
            assertEquals(
                    "8.8.8.8", hostList.getSelectionModel().getSelectedItem().getHost());
        });
    }
}

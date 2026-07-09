package io.pingui.ui;

import java.util.function.Consumer;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;

/** Keeps host list selection and history host filter aligned without listener feedback loops. */
final class HistoryHostSync {
    private boolean syncing;

    boolean isSyncing() {
        return syncing;
    }

    void runWhileSyncing(Runnable action) {
        syncing = true;
        try {
            action.run();
        } finally {
            syncing = false;
        }
    }

    void syncFilterFromHostList(String selectedHost, String filterValue, Consumer<String> setFilter) {
        if (syncing || selectedHost == null || selectedHost.isBlank()) {
            return;
        }
        if (selectedHost.equals(filterValue)) {
            return;
        }
        runWhileSyncing(() -> setFilter.accept(selectedHost));
    }

    void syncHostListFromFilter(String filterHost, ObservableList<HostItem> items, ListView<HostItem> hostList) {
        if (syncing || filterHost == null || filterHost.isBlank()) {
            return;
        }
        HostItem match = findItem(items, filterHost);
        if (match == null) {
            return;
        }
        if (hostList.getSelectionModel().getSelectedItem() == match) {
            return;
        }
        runWhileSyncing(() -> hostList.getSelectionModel().select(match));
    }

    static HostItem findItem(ObservableList<HostItem> items, String host) {
        for (HostItem item : items) {
            if (host.equals(item.getHost())) {
                return item;
            }
        }
        return null;
    }

    static boolean shouldRefreshHistoryForRouteChange(String changedHost, String filterHost) {
        return filterHost != null && !filterHost.isBlank() && filterHost.equals(changedHost);
    }
}

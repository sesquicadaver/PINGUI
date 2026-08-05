package io.pingui.ui;

import io.pingui.ui.view.MainView;
import javafx.collections.ObservableList;

/** History filter ↔ host list sync listeners for the main window. */
final class HistoryPanelWiring {
    private HistoryPanelWiring() {}

    static void wire(
            ObservableList<HostItem> hostItems,
            MainView mainView,
            HostListPresenter hostListPresenter,
            HistoryHostSync historyHostSync,
            Runnable syncHistoryHostFilter,
            Runnable redrawRouteGraph) {
        hostItems.addListener(
                (javafx.collections.ListChangeListener<? super HostItem>) change -> syncHistoryHostFilter.run());
        mainView.hostList().getSelectionModel().selectedItemProperty().addListener((obs, oldItem, item) -> {
            historyHostSync.syncFilterFromHostList(
                    item != null ? item.getHost() : null,
                    mainView.historyHostFilter().getValue(),
                    mainView.historyHostFilter()::setValue);
        });
        mainView.historyHostFilter().valueProperty().addListener((obs, oldHost, newHost) -> {
            if (historyHostSync.isSyncing()) {
                return;
            }
            hostListPresenter.ensureHostVisibleForTagFilter(newHost);
            historyHostSync.syncHostListFromFilter(newHost, hostItems, mainView.hostList());
            redrawRouteGraph.run();
        });
    }
}

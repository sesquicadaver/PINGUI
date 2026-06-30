package io.pingui.ui;

import io.pingui.monitor.SessionStore;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.scene.control.ListView;

/** Route graph rendering for extended view mode. */
final class RouteGraphPresenter {
    private final GraphCanvas graphCanvas;
    private final ListView<HostItem> hostList;
    private final Supplier<SessionStore> store;
    private final BooleanSupplier extendedView;
    private final BooleanSupplier easterEggActive;

    RouteGraphPresenter(
            GraphCanvas graphCanvas,
            ListView<HostItem> hostList,
            Supplier<SessionStore> store,
            BooleanSupplier extendedView,
            BooleanSupplier easterEggActive) {
        this.graphCanvas = graphCanvas;
        this.hostList = hostList;
        this.store = store;
        this.extendedView = extendedView;
        this.easterEggActive = easterEggActive;
    }

    void redrawIfExtended() {
        if (!extendedView.getAsBoolean() || easterEggActive.getAsBoolean()) {
            return;
        }
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            graphCanvas.renderRoute(java.util.List.of(), ip -> null, java.util.List.of());
            return;
        }
        String host = selected.getHost();
        SessionStore session = store.get();
        graphCanvas.renderRoute(
                session.get(host).getCurrentRoute(),
                ip -> session.avgPing(host, ip),
                session.inactiveRoute(host),
                hop -> session.hopStatsSummary(host, hop));
    }

    void showStaticMessage(String message) {
        graphCanvas.renderStaticView(message);
    }
}

package io.pingui.ui;

import io.pingui.monitor.RouteChangeEvent;
import io.pingui.monitor.SessionStore;
import java.util.List;
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
    private final RouteDiffPresenter routeDiffPresenter;
    private RouteChangeEvent replayEvent;

    RouteGraphPresenter(
            GraphCanvas graphCanvas,
            ListView<HostItem> hostList,
            Supplier<SessionStore> store,
            BooleanSupplier extendedView,
            BooleanSupplier easterEggActive,
            RouteDiffPresenter routeDiffPresenter) {
        this.graphCanvas = graphCanvas;
        this.hostList = hostList;
        this.store = store;
        this.extendedView = extendedView;
        this.easterEggActive = easterEggActive;
        this.routeDiffPresenter = routeDiffPresenter;
    }

    void redrawIfExtended() {
        if (!extendedView.getAsBoolean() || easterEggActive.getAsBoolean()) {
            return;
        }
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        // Replay must match the selected target; otherwise the graph shows another host's path
        // (e.g. history row for 1.1.1.1 while kernel.org is selected in the host list).
        if (replayEvent != null) {
            if (selected != null && replayEvent.host().equals(selected.getHost())) {
                // Replay uses reachable IP sequences from RouteChangeEvent (timeouts not stored).
                List<io.pingui.model.Models.HopNode> newRoute = RouteHistoryPresenter.ipsToRoute(replayEvent.newIps());
                List<io.pingui.model.Models.HopNode> oldRoute = RouteHistoryPresenter.ipsToRoute(replayEvent.oldIps());
                graphCanvas.renderRoute(newRoute, ip -> null, oldRoute, hop -> null);
                routeDiffPresenter.show(oldRoute, newRoute);
                return;
            }
            replayEvent = null;
        }
        if (selected == null) {
            graphCanvas.renderRoute(java.util.List.of(), ip -> null, java.util.List.of());
            routeDiffPresenter.clear();
            return;
        }
        String host = selected.getHost();
        SessionStore session = store.get();
        var current = session.get(host).getCurrentRoute();
        var previous = session.inactiveRoute(host);
        graphCanvas.renderRoute(
                current, ip -> session.avgPing(host, ip), previous, hop -> session.hopStatsSummary(host, hop));
        routeDiffPresenter.show(previous, current);
    }

    void showStaticMessage(String message) {
        replayEvent = null;
        routeDiffPresenter.clear();
        graphCanvas.renderStaticView(message);
    }

    void replayRouteChange(RouteChangeEvent event) {
        replayEvent = event;
        redrawIfExtended();
    }

    void clearReplay() {
        replayEvent = null;
        redrawIfExtended();
    }

    boolean isReplaying() {
        return replayEvent != null;
    }
}

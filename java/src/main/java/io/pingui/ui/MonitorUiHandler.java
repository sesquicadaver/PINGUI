package io.pingui.ui;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.PollSampleScope;
import io.pingui.ui.view.MainView;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Live monitor callbacks: status tick, route graph redraw, history on route change. */
final class MonitorUiHandler {
    private final Supplier<io.pingui.monitor.SessionStore> store;
    private final HostListPresenter hostListPresenter;
    private final HostInspectorPresenter hostInspectorPresenter;
    private final Supplier<ViewModeController> viewModeController;
    private final BooleanSupplier easterEggActive;
    private final MainView mainView;
    private final RouteGraphPresenter routeGraphPresenter;
    private final RouteHistoryPresenter routeHistoryPresenter;
    private final UserFeedback userFeedback;
    private final AppStatusPresenter appStatus;
    private final Runnable redrawRouteGraph;

    MonitorUiHandler(
            Supplier<io.pingui.monitor.SessionStore> store,
            HostListPresenter hostListPresenter,
            HostInspectorPresenter hostInspectorPresenter,
            Supplier<ViewModeController> viewModeController,
            BooleanSupplier easterEggActive,
            MainView mainView,
            RouteGraphPresenter routeGraphPresenter,
            RouteHistoryPresenter routeHistoryPresenter,
            UserFeedback userFeedback,
            AppStatusPresenter appStatus,
            Runnable redrawRouteGraph) {
        this.store = store;
        this.hostListPresenter = hostListPresenter;
        this.hostInspectorPresenter = hostInspectorPresenter;
        this.viewModeController = viewModeController;
        this.easterEggActive = easterEggActive;
        this.mainView = mainView;
        this.routeGraphPresenter = routeGraphPresenter;
        this.routeHistoryPresenter = routeHistoryPresenter;
        this.userFeedback = userFeedback;
        this.appStatus = appStatus;
        this.redrawRouteGraph = redrawRouteGraph;
    }

    void handleData(String host, RouteSnapshot snapshot) {
        handleData(host, snapshot, PollSampleScope.FULL);
    }

    void handleData(String host, RouteSnapshot snapshot, PollSampleScope sampleScope) {
        var sessionStore = store.get();
        if (!sessionStore.containsHost(host)) {
            return;
        }
        sessionStore.updateRoute(host, snapshot);
        sessionStore.appendPingSamples(host, snapshot, sampleScope != null ? sampleScope : PollSampleScope.FULL);
        HostItem item = hostListPresenter.findItem(host);
        if (item != null) {
            item.clearRouteChangedLatch();
            hostListPresenter.syncMetrics(item);
            hostListPresenter.syncProblem(item);
            hostInspectorPresenter.refreshIfHost(host);
        }
        if (snapshot != null) {
            appStatus.notePollCycle(snapshot.timestamp());
        } else {
            appStatus.refreshMonitoring();
        }
        ViewModeController mode = viewModeController.get();
        if (mode.isExtended() && !easterEggActive.getAsBoolean()) {
            String activeHost = viewHost();
            if (activeHost != null && host.equals(activeHost)) {
                redrawRouteGraph.run();
            }
        }
    }

    void handleRouteChanged(String host, List<String> oldIps, List<String> newIps) {
        if (!store.get().containsHost(host)) {
            return;
        }
        HostItem item = hostListPresenter.findItem(host);
        if (item != null && !oldIps.isEmpty()) {
            item.markRouteChanged();
            hostListPresenter.syncRouteState(item);
            hostInspectorPresenter.refreshIfHost(host);
        }
        ViewModeController mode = viewModeController.get();
        if (mode.isExtended() && !easterEggActive.getAsBoolean()) {
            if (!oldIps.isEmpty()) {
                String oldStr = String.join(" -> ", oldIps);
                userFeedback.info(
                        io.pingui.i18n.UiI18n.get("status.route_change", host, oldStr, String.join(" -> ", newIps)));
            }
            routeHistoryPresenter.onRouteChanged(host);
            String activeHost = viewHost();
            if (activeHost != null && host.equals(activeHost)) {
                routeGraphPresenter.clearReplay();
                routeHistoryPresenter.clearSelection();
                redrawRouteGraph.run();
            }
        }
    }

    private String viewHost() {
        HostItem selected = mainView.hostList().getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected.getHost();
        }
        String filterHost = mainView.historyHostFilter().getValue();
        return filterHost != null && !filterHost.isBlank() ? filterHost : null;
    }
}

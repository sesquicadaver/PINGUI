package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.TelemetryAttachment;
import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.i18n.UiI18n;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.ui.view.MainView;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.stage.Window;

/** Wires MainController coordinators once (P26-005 shell extraction). */
final class MainCoordinators {
    final ViewModeController viewMode;
    final UserFeedback userFeedback;
    final StageGeometryCoordinator stageGeometry;
    final PersistenceSessionCoordinator persistenceSession;
    final ProfileUiCoordinator profileUi;
    final RouteGraphPresenter routeGraph;
    final EasterEggController easterEgg;
    final HostListPresenter hostList;
    final HostInspectorPresenter hostInspector;
    final RouteHistoryPresenter routeHistory;
    final MonitorUiHandler monitorUi;
    final SettingsDialogsCoordinator settingsDialogs;

    private MainCoordinators(
            ViewModeController viewMode,
            UserFeedback userFeedback,
            StageGeometryCoordinator stageGeometry,
            PersistenceSessionCoordinator persistenceSession,
            ProfileUiCoordinator profileUi,
            RouteGraphPresenter routeGraph,
            EasterEggController easterEgg,
            HostListPresenter hostList,
            HostInspectorPresenter hostInspector,
            RouteHistoryPresenter routeHistory,
            MonitorUiHandler monitorUi,
            SettingsDialogsCoordinator settingsDialogs) {
        this.viewMode = viewMode;
        this.userFeedback = userFeedback;
        this.stageGeometry = stageGeometry;
        this.persistenceSession = persistenceSession;
        this.profileUi = profileUi;
        this.routeGraph = routeGraph;
        this.easterEgg = easterEgg;
        this.hostList = hostList;
        this.hostInspector = hostInspector;
        this.routeHistory = routeHistory;
        this.monitorUi = monitorUi;
        this.settingsDialogs = settingsDialogs;
    }

    static MainCoordinators wire(Wiring wiring) {
        ViewModeController viewMode = new ViewModeController(
                wiring.mainView.graphPanel(),
                wiring.mainView.leftPanel(),
                wiring.mainView.root(),
                wiring.mainView.mainSplit(),
                wiring.mainView.logArea(),
                wiring.mainView.statusLabel(),
                wiring.redrawRouteGraph,
                wiring.showEasterEggCanvas,
                wiring.easterEggActive);
        UserFeedback userFeedback = new UiFeedbackRouter(
                () -> viewMode.isExtended(),
                wiring.mainView.statusLabel()::setText,
                message -> wiring.mainView
                        .logArea()
                        .appendText("[" + wiring.timeFmt.format(java.time.Instant.now()) + "] " + message + "\n"),
                wiring.showSimpleErrorAlert);
        StageGeometryCoordinator stageGeometry =
                new StageGeometryCoordinator(wiring.mainView, () -> viewMode, wiring.onSceneShown);
        PersistenceSessionCoordinator persistenceSession = new PersistenceSessionCoordinator(
                wiring.options,
                wiring.profileDocument,
                wiring.setStore,
                wiring.store,
                wiring.installMonitor,
                wiring.closeMonitorAndTelemetry,
                wiring.updateHistoryPanelVisibility,
                wiring.rebuildHostList,
                wiring.syncHistoryHostFilter,
                wiring.resetReplayState,
                wiring.redrawRouteGraph,
                () -> viewMode.apply(),
                wiring.clearHostSelection,
                wiring.syncInputLimits);
        ProfileUiCoordinator[] profileUiRef = new ProfileUiCoordinator[1];
        ProfileUiCoordinator profileUi = new ProfileUiCoordinator(
                wiring.profileDocument,
                wiring.store,
                wiring.mainView.profileCombo(),
                wiring.switchingProfile,
                wiring.setSwitchingProfile,
                wiring.reloadActiveProfile,
                () -> profileUiRef[0].refreshCombo(),
                userFeedback);
        profileUiRef[0] = profileUi;
        profileUi.setDirtyHooks(wiring.markDirty, wiring.isDirty, wiring.saveYaml, wiring.confirmUnsavedChanges);
        if (wiring.profileDocument.get() != null) {
            profileUi.refreshCombo();
        }
        RouteGraphPresenter routeGraph = new RouteGraphPresenter(
                wiring.mainView.graphCanvas(),
                wiring.mainView.hostList(),
                wiring.store,
                () -> viewMode.isExtended(),
                wiring.easterEggActive);
        EasterEggController easterEgg = new EasterEggController(
                wiring.mainView, viewMode, routeGraph, stageGeometry::ensureExtendedStageGeometry);
        HostListPresenter hostList = new HostListPresenter(
                wiring.hostItems,
                wiring.mainView.hostList(),
                wiring.mainView.hostInput(),
                wiring.store,
                wiring.monitor,
                wiring.expertMode,
                userFeedback,
                wiring.syncInputLimits,
                wiring.redrawRouteGraph,
                wiring.clearHistoryReplay,
                wiring.onHostRenamed,
                easterEgg::start,
                wiring.runWhileSyncing);
        hostList.setMarkDirty(wiring.markDirty);
        wiring.mainView.graphCanvas().setOnHopIpCopied(ip -> userFeedback.info(UiI18n.get("status.hop_ip_copied", ip)));
        io.pingui.dns.DnsResolver.addListener(() -> Platform.runLater(routeGraph::redrawIfExtended));
        HostInspectorPresenter hostInspector = new HostInspectorPresenter(
                wiring.mainView.hostInspectorPanel(), wiring.store, wiring.monitor, wiring.dialogOwner, userFeedback);
        hostList.setHostInspector(hostInspector);
        RouteHistoryPresenter routeHistory = new RouteHistoryPresenter(
                wiring.store,
                wiring.mainView.historyHostFilter(),
                wiring.mainView.historyList(),
                wiring.mainView.historyRange24h(),
                wiring.mainView.historyRange7d(),
                () -> viewMode.isExtended(),
                routeGraph::replayRouteChange,
                routeGraph::clearReplay,
                host -> {
                    MonitorService service = wiring.monitor.get();
                    return service == null ? Optional.empty() : service.hostProblemSummary(host);
                });
        routeHistory.configure();
        MonitorUiHandler monitorUi = new MonitorUiHandler(
                wiring.store,
                hostList,
                hostInspector,
                () -> viewMode,
                easterEgg::isActive,
                wiring.mainView,
                routeGraph,
                routeHistory,
                userFeedback,
                wiring.redrawRouteGraph);
        SettingsDialogsCoordinator settingsDialogs = new SettingsDialogsCoordinator(
                wiring.store,
                wiring.monitor,
                wiring.profileDocument,
                wiring.options,
                wiring.dialogOwner,
                userFeedback,
                wiring.markDirty,
                wiring.recreateMonitorForProfileParams,
                wiring.attachTelemetry,
                wiring.telemetry,
                policy -> {
                    easterEgg.dismiss();
                    persistenceSession.reconnectPersistence(policy);
                },
                persistenceSession::resolveSessionDbPath,
                persistenceSession::sessionPersistenceOverride,
                persistenceSession::setSessionGuiDbOverride,
                persistenceSession::setSessionPersistenceOverride,
                () -> viewMode,
                wiring.mainView);
        HistoryPanelWiring.wire(
                wiring.hostItems,
                wiring.mainView,
                hostList,
                hostInspector,
                wiring.historyHostSync,
                wiring.syncHistoryHostFilter,
                wiring.redrawRouteGraph);
        wiring.syncHistoryHostFilter.run();
        return new MainCoordinators(
                viewMode,
                userFeedback,
                stageGeometry,
                persistenceSession,
                profileUi,
                routeGraph,
                easterEgg,
                hostList,
                hostInspector,
                routeHistory,
                monitorUi,
                settingsDialogs);
    }

    /** Callback bundle for {@link #wire(Wiring)} — keeps {@link MainController} thin. */
    static final class Wiring {
        final AppOptions options;
        final MainView mainView;
        final ObservableList<HostItem> hostItems;
        final SimpleBooleanProperty expertMode;
        final java.time.format.DateTimeFormatter timeFmt;
        final Supplier<ProfileDocument> profileDocument;
        final Consumer<SessionStore> setStore;
        final Supplier<SessionStore> store;
        final Supplier<MonitorService> monitor;
        final Supplier<TelemetryAttachment> telemetry;
        final BooleanSupplier switchingProfile;
        final Consumer<Boolean> setSwitchingProfile;
        final Runnable reloadActiveProfile;
        final Runnable redrawRouteGraph;
        final Runnable showEasterEggCanvas;
        final BooleanSupplier easterEggActive;
        final Runnable onSceneShown;
        final Consumer<String> showSimpleErrorAlert;
        final Supplier<Window> dialogOwner;
        final Runnable markDirty;
        final BooleanSupplier isDirty;
        final BooleanSupplier saveYaml;
        final Supplier<ConfirmDialogs.UnsavedDecision> confirmUnsavedChanges;
        final Consumer<List<HostEntry>> installMonitor;
        final Runnable closeMonitorAndTelemetry;
        final Runnable updateHistoryPanelVisibility;
        final Consumer<List<io.pingui.config.HostEntry>> rebuildHostList;
        final Runnable syncHistoryHostFilter;
        final Runnable resetReplayState;
        final Runnable clearHostSelection;
        final Runnable syncInputLimits;
        final Consumer<List<io.pingui.config.HostEntry>> recreateMonitorForProfileParams;
        final Runnable attachTelemetry;
        final java.util.function.BiConsumer<String, String> onHostRenamed;
        final Runnable clearHistoryReplay;
        final Consumer<Runnable> runWhileSyncing;
        final HistoryHostSync historyHostSync;

        Wiring(
                AppOptions options,
                MainView mainView,
                ObservableList<HostItem> hostItems,
                SimpleBooleanProperty expertMode,
                java.time.format.DateTimeFormatter timeFmt,
                Supplier<ProfileDocument> profileDocument,
                Consumer<SessionStore> setStore,
                Supplier<SessionStore> store,
                Supplier<MonitorService> monitor,
                Supplier<TelemetryAttachment> telemetry,
                BooleanSupplier switchingProfile,
                Consumer<Boolean> setSwitchingProfile,
                Runnable reloadActiveProfile,
                Runnable redrawRouteGraph,
                Runnable showEasterEggCanvas,
                BooleanSupplier easterEggActive,
                Runnable onSceneShown,
                Consumer<String> showSimpleErrorAlert,
                Supplier<Window> dialogOwner,
                Runnable markDirty,
                BooleanSupplier isDirty,
                BooleanSupplier saveYaml,
                Supplier<ConfirmDialogs.UnsavedDecision> confirmUnsavedChanges,
                Consumer<List<io.pingui.config.HostEntry>> installMonitor,
                Runnable closeMonitorAndTelemetry,
                Runnable updateHistoryPanelVisibility,
                Consumer<List<io.pingui.config.HostEntry>> rebuildHostList,
                Runnable syncHistoryHostFilter,
                Runnable resetReplayState,
                Runnable clearHostSelection,
                Runnable syncInputLimits,
                Consumer<List<io.pingui.config.HostEntry>> recreateMonitorForProfileParams,
                Runnable attachTelemetry,
                java.util.function.BiConsumer<String, String> onHostRenamed,
                Runnable clearHistoryReplay,
                Consumer<Runnable> runWhileSyncing,
                HistoryHostSync historyHostSync) {
            this.options = options;
            this.mainView = mainView;
            this.hostItems = hostItems;
            this.expertMode = expertMode;
            this.timeFmt = timeFmt;
            this.profileDocument = profileDocument;
            this.setStore = setStore;
            this.store = store;
            this.monitor = monitor;
            this.telemetry = telemetry;
            this.switchingProfile = switchingProfile;
            this.setSwitchingProfile = setSwitchingProfile;
            this.reloadActiveProfile = reloadActiveProfile;
            this.redrawRouteGraph = redrawRouteGraph;
            this.showEasterEggCanvas = showEasterEggCanvas;
            this.easterEggActive = easterEggActive;
            this.onSceneShown = onSceneShown;
            this.showSimpleErrorAlert = showSimpleErrorAlert;
            this.dialogOwner = dialogOwner;
            this.markDirty = markDirty;
            this.isDirty = isDirty;
            this.saveYaml = saveYaml;
            this.confirmUnsavedChanges = confirmUnsavedChanges;
            this.installMonitor = installMonitor;
            this.closeMonitorAndTelemetry = closeMonitorAndTelemetry;
            this.updateHistoryPanelVisibility = updateHistoryPanelVisibility;
            this.rebuildHostList = rebuildHostList;
            this.syncHistoryHostFilter = syncHistoryHostFilter;
            this.resetReplayState = resetReplayState;
            this.clearHostSelection = clearHostSelection;
            this.syncInputLimits = syncInputLimits;
            this.recreateMonitorForProfileParams = recreateMonitorForProfileParams;
            this.attachTelemetry = attachTelemetry;
            this.onHostRenamed = onHostRenamed;
            this.clearHistoryReplay = clearHistoryReplay;
            this.runWhileSyncing = runWhileSyncing;
            this.historyHostSync = historyHostSync;
        }
    }
}

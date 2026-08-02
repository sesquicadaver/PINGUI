package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.CliProfileOverrides;
import io.pingui.CliTelemetryOverrides;
import io.pingui.TelemetryAttachment;
import io.pingui.config.AlertConfig;
import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.PersistenceConfig;
import io.pingui.config.PingPresets;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.config.SessionDbResolver;
import io.pingui.config.TracingProfile;
import io.pingui.dns.DnsResolver;
import io.pingui.geoip.AsnLookup;
import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.timeseries.TimeSeriesBackends;
import io.pingui.persistence.timeseries.TimeSeriesConfigException;
import io.pingui.ui.view.MainView;
import io.pingui.ui.view.MainViewActions;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Main JavaFX window: profiles, host list, optional route graph and event log. */
public final class MainController {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Duration EASTER_EGG_DURATION = Duration.seconds(30);
    private static final String WINDOW_TITLE = "PINGUI — Сесійний монітор маршрутів (Java)";

    /** Window title without dirty suffix (shared with {@link io.pingui.PinguiApplication}). */
    public static String windowTitle() {
        return WINDOW_TITLE;
    }

    private final AppOptions options;
    private ProfileDocument profileDocument;
    private SessionStore store;
    private MonitorService monitor;
    private TelemetryAttachment telemetry;
    private volatile boolean servicesReady;
    private volatile boolean shutdownRequested;
    private final ObservableList<HostItem> hostItems = FXCollections.observableArrayList();
    private final MainView mainView = new MainView();
    private final SimpleBooleanProperty expertMode = new SimpleBooleanProperty(false);
    private final ConfigDirtyState dirtyState = new ConfigDirtyState(this::updateDirtyUi);
    private UiViewMode viewModeBeforeEasterEgg = UiViewMode.SIMPLE;
    private boolean easterEggActive;
    private PauseTransition easterEggTimer;
    private boolean switchingProfile;
    private Optional<PersistencePolicy> sessionPersistenceOverride = Optional.empty();
    private Optional<Path> sessionGuiDbOverride = Optional.empty();

    private ProfileUiCoordinator profileUi;
    private HostListPresenter hostListPresenter;
    private ViewModeController viewModeController;
    private UserFeedback userFeedback;
    private RouteGraphPresenter routeGraphPresenter;
    private RouteHistoryPresenter routeHistoryPresenter;
    private final RouteDiffPresenter routeDiffPresenter = new RouteDiffPresenter();
    private final HistoryHostSync historyHostSync = new HistoryHostSync();
    private WindowGeometry pendingDividerRestore;
    private Stage mainStage;
    private double extendedDefaultWidth = WindowGeometry.DEFAULT_EXTENDED_WIDTH;

    /**
     * Shell constructor (P24-009): FX chrome only — no profile YAML, SQLite, or GeoIP I/O. Heavy load
     * runs via {@link StartupBootstrap#load(AppOptions)} then {@link #attachBootstrap}.
     */
    public MainController(AppOptions options) {
        this.options = options;
    }

    /**
     * @deprecated Use {@link #MainController(AppOptions)} + {@link StartupBootstrap} (P24-009). Kept
     *     for tests that need a fully wired controller on the calling thread.
     */
    @Deprecated
    public MainController(AppOptions options, ProfileDocument document) {
        this.options = options;
        this.profileDocument = document;
        applyCliOverridesToActiveProfile();
        GeoCountry.configure(options.geoipEnabled(), options.geoipHintsPath());
        AsnLookup.configure(options.asnEnabled(), options.asnHintsPath(), options.asnTimeoutMs());
        DnsResolver.configure(true);
        PingPresets.configure(PingPresets.resolvePath(options.configPath()));
        TracingProfile active = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(active.hosts());
        this.store = SessionStore.fromEntries(
                sessionHosts, openSessionDatabase(), profileDocument.active().hostProbeMode());
        attachTimeSeries(store);
        this.monitor = createMonitor(active, sessionHosts);
        this.servicesReady = true;
        initCoordinators();
        hostListPresenter.rebuild(sessionHosts);
    }

    /**
     * Builds the shell Scene without waiting for {@link StartupBootstrap}. Controls stay disabled
     * until {@link #attachBootstrap}.
     */
    public Scene createScene() {
        initCoordinators();
        hostListPresenter.configure();
        mainView.monitorModeToolbar().bindExpertMode(expertMode);
        if (!mainView.monitorModeToolbar().expertCheck().isDisable()) {
            expertMode.addListener((obs, was, on) -> mainView.hostList().refresh());
        }

        MainViewActions actions = buildViewActions();
        mainView.assemble(actions, hostListPresenter.tagFilterBar(), routeDiffPresenter.panel());
        updateDirtyUi();

        // Cross-coordinator listeners (D4) — after assemble.
        mainView.modeGroup().selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            viewModeController.onToggleSelected(newToggle);
            ensureExtendedStageWidth();
            updateHistoryPanelVisibility();
        });

        mainView.hostInput().textProperty().addListener((obs, oldText, newText) -> {
            if (easterEggActive && !HostViewRules.matches(newText)) {
                dismissEasterEgg();
            }
        });

        mainView.hostList().getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                mainView.hostInput().setText(newItem.getHost());
            }
            hostListPresenter.syncInputLimits();
            if (oldItem != newItem) {
                clearHistoryReplay();
            }
            routeGraphPresenter.redrawIfExtended();
        });
        mainView.statusLabel().setText("Завантаження…");
        setShellBusy(true);
        Scene scene = new Scene(mainView.root());
        UiPalette.applyTo(scene);
        return scene;
    }

    /**
     * Attaches background-loaded services on the FX thread and starts polling ({@link
     * MonitorService} ctor).
     */
    public void attachBootstrap(StartupBootstrap.Result result) {
        if (shutdownRequested) {
            // Window closed while bootstrap was in flight — do not start polling.
            try {
                result.store().close();
            } catch (RuntimeException ignored) {
                // best-effort
            }
            return;
        }
        this.profileDocument = result.document();
        this.store = result.store();
        TracingProfile active = profileDocument.active();
        this.monitor = createMonitor(active, result.sessionHosts());
        this.servicesReady = true;
        profileUi.refreshCombo();
        updateHistoryPanelVisibility();
        hostListPresenter.rebuild(result.sessionHosts());
        syncHistoryHostFilter();
        mainView.hostList().getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            mainView.hostList().getSelectionModel().select(0);
        }
        hostListPresenter.syncInputLimits();
        viewModeController.apply();
        setShellBusy(false);
        if (EmptyStateHints.isReplaceableSimpleStatus(mainView.statusLabel().getText())
                || "Завантаження…".equals(mainView.statusLabel().getText())) {
            mainView.statusLabel().setText(EmptyStateHints.waitingForData());
        }
        redrawRouteGraph();
    }

    /** Surfaces bootstrap failure after the Stage is already shown (status only — no modal Alert). */
    public void onBootstrapFailed(Throwable error) {
        if (shutdownRequested) {
            return;
        }
        // Re-enable only the status label path: keep menus/panels disabled to avoid NPE on null store.
        if (mainView.root().getTop() != null) {
            mainView.root().getTop().setDisable(true);
        }
        mainView.leftPanel().setDisable(false);
        mainView.hostInput().setDisable(true);
        mainView.hostList().setDisable(true);
        mainView.profileCombo().setDisable(true);
        mainView.saveButton().setDisable(true);
        mainView.graphPanel().setDisable(true);
        mainView.mainSplit().setDisable(true);
        String message = error.getMessage() != null ? error.getMessage() : error.toString();
        mainView.statusLabel().setText("Помилка завантаження: " + message);
        if (viewModeController != null && viewModeController.isExtended()) {
            mainView.logArea()
                    .appendText("[" + TIME_FMT.format(java.time.Instant.now()) + "] Не вдалося завантажити сесію: "
                            + message + "\n");
        }
    }

    /** True after {@link #attachBootstrap} succeeded (test / guard seam). */
    boolean servicesReady() {
        return servicesReady;
    }

    /** Package-visible for startup tests. */
    String statusTextForTest() {
        return mainView.statusLabel().getText();
    }

    private void setShellBusy(boolean busy) {
        // Disable all interactive chrome (including menu) while bootstrap runs — avoids NPE on null store.
        if (mainView.root().getTop() != null) {
            mainView.root().getTop().setDisable(busy);
        }
        mainView.leftPanel().setDisable(busy);
        mainView.graphPanel().setDisable(busy);
        mainView.mainSplit().setDisable(busy);
    }

    private MainViewActions buildViewActions() {
        return new MainViewActions() {
            @Override
            public void onSaveConfig() {
                MainController.this.onSaveConfig();
            }

            @Override
            public void onAddHost() {
                hostListPresenter.addHost();
            }

            @Override
            public void onEditHost() {
                hostListPresenter.editHost();
            }

            @Override
            public void onEditTags() {
                hostListPresenter.editSelectedHostTags();
            }

            @Override
            public void onRemoveHost() {
                hostListPresenter.removeHost();
            }

            @Override
            public void onNewProfile() {
                profileUi.onNewProfile();
            }

            @Override
            public void onDeleteProfile() {
                profileUi.onDeleteProfile();
            }

            @Override
            public void onProfileSelected() {
                profileUi.onProfileSelected();
            }

            @Override
            public void onRefreshHistory() {
                refreshRouteHistory();
            }

            @Override
            public void onAbout() {
                AppMenuDialogs.showAbout(dialogOwner());
            }

            @Override
            public void onHelp() {
                AppMenuDialogs.showHelp(dialogOwner());
            }

            @Override
            public void onPersistenceSettings() {
                MainController.this.onPersistenceSettings();
            }

            @Override
            public void onProfileParamsSettings() {
                MainController.this.onProfileParamsSettings();
            }

            @Override
            public void onAlertsSettings() {
                MainController.this.onAlertsSettings();
            }

            @Override
            public void onTelemetrySettings() {
                MainController.this.onTelemetrySettings();
            }

            @Override
            public void onExportNow() {
                MainController.this.onExportNow();
            }
        };
    }

    /**
     * Loads prefs, clamps to the visual screen, applies mode/layout once, sets stage bounds, and
     * registers close-only save (P24-006). Call after {@link #createScene()} and before {@code
     * stage.show()}.
     *
     * @param defaultWidthSimple fallback width when prefs missing and mode is Simple
     * @param defaultWidthExtended fallback width when Extended omits width; also expand target on toggle
     * @param defaultHeight fallback height (Simple and Extended)
     */
    public void prepareStageGeometry(
            Stage stage, double defaultWidthSimple, double defaultWidthExtended, double defaultHeight) {
        this.mainStage = stage;
        this.extendedDefaultWidth = defaultWidthExtended;
        WindowGeometryStore store = WindowGeometryStore.userDefault();
        WindowGeometry loaded = store.load(defaultWidthSimple, defaultWidthExtended, defaultHeight);
        Rectangle2D visual = visualBoundsFor(loaded);
        double clampDefaultWidth = loaded.viewMode() == UiViewMode.EXTENDED ? defaultWidthExtended : defaultWidthSimple;
        WindowGeometry geometry = loaded.clamp(
                visual.getMinX(),
                visual.getMinY(),
                visual.getWidth(),
                visual.getHeight(),
                clampDefaultWidth,
                defaultHeight);
        applyRestoredGeometry(geometry);
        if (!Double.isNaN(geometry.x())) {
            stage.setX(geometry.x());
        }
        if (!Double.isNaN(geometry.y())) {
            stage.setY(geometry.y());
        }
        stage.setWidth(geometry.width());
        stage.setHeight(geometry.height());
        pendingDividerRestore = geometry;
        stage.setOnCloseRequest(event -> store.save(captureGeometry(stage)));
    }

    /** Post-show: divider pulse, Simple width fit (height untouched), scene-shown redraw. */
    public void onStageShown() {
        if (pendingDividerRestore != null) {
            WindowGeometry geometry = pendingDividerRestore;
            pendingDividerRestore = null;
            Platform.runLater(() -> viewModeController.applyDivider(geometry.divider()));
        }
        Platform.runLater(this::fitSimpleStageWidthIfNeeded);
        onSceneShown();
    }

    /**
     * After layout: if Simple chrome is narrower than the Stage, shrink width only (leftover Extended
     * width / oversized prefs). Does not change height or Extended layout.
     */
    void fitSimpleStageWidthIfNeeded() {
        if (mainStage == null || viewModeController == null || viewModeController.isExtended()) {
            return;
        }
        javafx.scene.layout.Region root = mainView.root();
        root.applyCss();
        root.layout();
        double next = WindowGeometry.fitSimpleWidth(mainStage.getWidth(), root.prefWidth(-1));
        if (next + 0.5 < mainStage.getWidth()) {
            mainStage.setWidth(next);
        }
    }

    /** When switching to Extended from a Simple-narrow Stage, expand width once (never shrink). */
    void ensureExtendedStageWidth() {
        if (mainStage == null || viewModeController == null || !viewModeController.isExtended()) {
            return;
        }
        double next = WindowGeometry.ensureExtendedWidth(mainStage.getWidth(), extendedDefaultWidth);
        if (next > mainStage.getWidth() + 0.5) {
            mainStage.setWidth(next);
        }
    }

    /**
     * Restores view mode and builds layout once before the Stage is shown (no Simple flash when
     * prefs say EXTENDED).
     */
    void applyRestoredGeometry(WindowGeometry geometry) {
        viewModeController.restoreMode(geometry.viewMode(), mainView::simpleModeButton, mainView::extendedModeButton);
        viewModeController.apply();
        viewModeController.applyDivider(geometry.divider());
    }

    WindowGeometry captureGeometry(Stage stage) {
        return new WindowGeometry(
                stage.getX(),
                stage.getY(),
                stage.getWidth(),
                stage.getHeight(),
                viewModeController.dividerForSave(),
                viewModeController.viewMode());
    }

    static Rectangle2D visualBoundsFor(WindowGeometry geometry) {
        double cx = Double.isNaN(geometry.x()) ? Double.NaN : geometry.x() + geometry.width() / 2.0;
        double cy = Double.isNaN(geometry.y()) ? Double.NaN : geometry.y() + geometry.height() / 2.0;
        if (!Double.isNaN(cx) && !Double.isNaN(cy)) {
            for (Screen screen : Screen.getScreens()) {
                Rectangle2D bounds = screen.getVisualBounds();
                if (bounds.contains(cx, cy)) {
                    return bounds;
                }
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }

    public void onSceneShown() {
        Platform.runLater(() -> {
            if (!easterEggActive) {
                routeGraphPresenter.redrawIfExtended();
            }
        });
    }

    public void shutdown() {
        shutdownRequested = true;
        dismissEasterEgg();
        if (monitor != null) {
            monitor.close();
        }
        closeTelemetry();
        if (store != null) {
            store.close();
        }
    }

    private void initCoordinators() {
        viewModeController = new ViewModeController(
                mainView.graphPanel(),
                mainView.leftPanel(),
                mainView.root(),
                mainView.mainSplit(),
                mainView.logArea(),
                mainView.statusLabel(),
                () -> {
                    if (routeGraphPresenter != null) {
                        routeGraphPresenter.redrawIfExtended();
                    }
                    refreshRouteHistory();
                },
                this::showEasterEggCanvas,
                () -> easterEggActive);
        userFeedback = new UiFeedbackRouter(
                () -> viewModeController.isExtended(),
                mainView.statusLabel()::setText,
                message -> mainView.logArea()
                        .appendText("[" + TIME_FMT.format(java.time.Instant.now()) + "] " + message + "\n"),
                this::showSimpleErrorAlert);

        profileUi = new ProfileUiCoordinator(
                () -> profileDocument,
                () -> store,
                mainView.profileCombo(),
                () -> switchingProfile,
                value -> switchingProfile = value,
                this::reloadActiveProfile,
                () -> profileUi.refreshCombo(),
                userFeedback);
        profileUi.setDirtyHooks(dirtyState::mark, dirtyState::isDirty, this::onSaveConfig, this::confirmUnsavedChanges);
        if (profileDocument != null) {
            profileUi.refreshCombo();
        }

        hostListPresenter = new HostListPresenter(
                hostItems,
                mainView.hostList(),
                mainView.hostInput(),
                () -> store,
                () -> monitor,
                expertMode,
                userFeedback,
                () -> hostListPresenter.syncInputLimits(),
                this::redrawRouteGraph,
                this::clearHistoryReplay,
                this::onHostRenamed,
                this::startEasterEgg,
                historyHostSync::runWhileSyncing);
        hostListPresenter.setMarkDirty(dirtyState::mark);

        routeGraphPresenter = new RouteGraphPresenter(
                mainView.graphCanvas(),
                mainView.hostList(),
                () -> store,
                () -> viewModeController.isExtended(),
                () -> easterEggActive,
                routeDiffPresenter);
        mainView.graphCanvas().setOnHopIpCopied(ip -> userFeedback.info("Скопійовано hop IP: " + ip));
        DnsResolver.addListener(() -> Platform.runLater(routeGraphPresenter::redrawIfExtended));

        routeHistoryPresenter = new RouteHistoryPresenter(
                () -> store,
                mainView.historyHostFilter(),
                mainView.historyList(),
                mainView.historyRange24h(),
                mainView.historyRange7d(),
                () -> viewModeController.isExtended(),
                routeGraphPresenter::replayRouteChange,
                routeGraphPresenter::clearReplay);
        routeHistoryPresenter.configure();
        hostItems.addListener(
                (javafx.collections.ListChangeListener<? super HostItem>) change -> syncHistoryHostFilter());
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
            redrawRouteGraph();
        });
        syncHistoryHostFilter();
    }

    private void syncHistoryHostFilter() {
        if (routeHistoryPresenter == null) {
            return;
        }
        routeHistoryPresenter.rebuildHostFilter(
                hostItems.stream().map(HostItem::getHost).toList());
    }

    private void refreshRouteHistory() {
        if (routeHistoryPresenter != null) {
            routeHistoryPresenter.reloadKeepingFilter();
        }
    }

    private void redrawRouteGraph() {
        if (routeGraphPresenter != null) {
            routeGraphPresenter.redrawIfExtended();
        }
    }

    private void clearHistoryReplay() {
        if (routeHistoryPresenter != null) {
            routeHistoryPresenter.clearSelection();
        }
    }

    private void resetReplayState() {
        clearHistoryReplay();
        if (routeGraphPresenter != null) {
            routeGraphPresenter.clearReplay();
        }
    }

    private void onHostRenamed(String oldHost, String newHost) {
        if (oldHost.equals(mainView.historyHostFilter().getValue())) {
            historyHostSync.runWhileSyncing(() -> mainView.historyHostFilter().setValue(newHost));
        }
        syncHistoryHostFilter();
    }

    /**
     * Active target for live graph updates — same source as {@link RouteGraphPresenter} (host list).
     * History filter is kept in sync via {@link HistoryHostSync}; do not prefer the filter here or
     * pings for host A can redraw while the list shows host B.
     */
    private String viewHost() {
        HostItem selected = mainView.hostList().getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected.getHost();
        }
        String filterHost = mainView.historyHostFilter().getValue();
        return filterHost != null && !filterHost.isBlank() ? filterHost : null;
    }

    private void updateHistoryPanelVisibility() {
        boolean persistence = store != null && store.hasPersistence();
        mainView.historyLabel().setVisible(true);
        mainView.historyLabel().setManaged(true);
        mainView.historyList().setVisible(true);
        mainView.historyList().setManaged(true);
        mainView.historyFilterBar().setVisible(persistence);
        mainView.historyFilterBar().setManaged(persistence);
        mainView.historyRangeBar().setVisible(persistence);
        mainView.historyRangeBar().setManaged(persistence);
        refreshRouteHistory();
    }

    private Window dialogOwner() {
        return mainView.root().getScene() != null ? mainView.root().getScene().getWindow() : null;
    }

    private MonitorService createMonitor(TracingProfile profile, List<HostEntry> sessionHosts) {
        MonitorService service = MonitorLifecycle.create(
                profile,
                profileDocument.activeProfile(),
                store,
                new MonitorService.Listener() {
                    @Override
                    public void onDataReceived(String host, RouteSnapshot snapshot) {
                        Platform.runLater(() -> handleData(host, snapshot));
                    }

                    @Override
                    public void onRouteChanged(String host, List<String> oldIps, List<String> newIps) {
                        Platform.runLater(() -> handleRouteChanged(host, oldIps, newIps));
                    }

                    @Override
                    public void onProbeError(String host, String message) {
                        Platform.runLater(() -> {
                            userFeedback.info("Probe [" + host + "]: " + message);
                            HostItem item = hostListPresenter.findItem(host);
                            if (item != null) {
                                hostListPresenter.syncMetrics(item);
                            }
                        });
                    }

                    @Override
                    public void onPollFinished(String host) {
                        Platform.runLater(() -> {
                            HostItem item = hostListPresenter.findItem(host);
                            if (item != null) {
                                hostListPresenter.syncMetrics(item);
                            }
                        });
                    }
                },
                options.alertOverrides().applyTo(profile.alerts()),
                store.database(),
                sessionHosts,
                MonitorLifecycle.javaFxDesktopSink(this::dialogOwner));
        applyPersistencePolicy(service, profile);
        attachTelemetry(service);
        return service;
    }

    private void attachTelemetry(MonitorService service) {
        Optional<SessionDatabase> sessionDb =
                store != null && store.database() != null ? Optional.of(store.database()) : Optional.empty();
        telemetry = TelemetryAttachment.replace(
                telemetry, service, profileDocument.active().telemetry(), sessionDb);
    }

    private void closeTelemetry() {
        if (telemetry != null) {
            telemetry.close();
            telemetry = null;
        }
    }

    private void applyPersistencePolicy(MonitorService service, TracingProfile profile) {
        PersistencePolicy baseline =
                options.persistenceOverrides().applyTo(profile.persistence()).toPolicy();
        PersistencePolicy effective = sessionPersistenceOverride.orElse(baseline);
        service.setPendingPersistencePolicy(effective);
        service.persistencePolicy().applyPendingAfterCycle();
    }

    private void onExportNow() {
        if (!store.hasPersistence() || store.database() == null) {
            userFeedback.error(SessionExportUi.noSqliteMessage());
            return;
        }
        try {
            Optional<Path> written = SessionExportUi.chooseAndExport(dialogOwner(), store.database());
            written.ifPresent(path -> userFeedback.info(SessionExportUi.successMessage(path)));
        } catch (IOException | RuntimeException ex) {
            userFeedback.error(SessionExportUi.failureMessage(ex));
        }
    }

    private void onProfileParamsSettings() {
        ProfileParamsSettingsDialog.show(
                dialogOwner(), profileDocument.active(), options.profileOverrides(), this::handleProfileParamsSettings);
    }

    private void handleProfileParamsSettings(ProfileParamsSettingsDialog.Result result) {
        List<HostEntry> liveEntries = HostViewRules.entriesForConfig(store.toHostEntries());
        TracingProfile next = profileDocument
                .active()
                .withPollSettings(
                        result.intervalSeconds(), result.maxHops(), result.timeoutSeconds(), result.probeMode())
                .withHosts(liveEntries);
        profileDocument.putProfile(profileDocument.activeProfile(), next);
        monitor.close();
        closeTelemetry();
        monitor = createMonitor(next, liveEntries);
        dirtyState.mark();
        userFeedback.info(String.format(
                java.util.Locale.ROOT,
                "Параметри профілю: interval=%.3g с, max_hops=%d, timeout=%.3g с, probe=%s — «Зберегти» → YAML",
                next.intervalSeconds(),
                next.maxHops(),
                next.timeoutSeconds(),
                next.probeMode().cliValue()));
    }

    private void onPersistenceSettings() {
        PersistencePolicy active =
                store.hasPersistence() ? monitor.persistencePolicy().active() : PersistencePolicy.defaults();
        PersistencePolicy pending = store.hasPersistence()
                ? monitor.persistencePolicy().pending()
                : sessionPersistenceOverride.orElseGet(() -> options.persistenceOverrides()
                        .applyTo(profileDocument.active().persistence())
                        .toPolicy());
        PersistenceSettingsDialog.show(
                dialogOwner(),
                resolveSessionDbPath(),
                options.sessionDbPath(),
                profileDocument.active().persistence().sessionDb(),
                options.persistenceOverrides(),
                active,
                pending,
                store.database(),
                result -> handlePersistenceSettings(result));
    }

    private void handlePersistenceSettings(PersistenceSettingsDialog.Result result) {
        if (result.sessionDbPath().isPresent()) {
            sessionGuiDbOverride = result.sessionDbPath();
            reconnectPersistence(Optional.of(result.policy()));
            notifyPersistenceConnected(result.sessionDbPath().get());
        } else {
            sessionPersistenceOverride = Optional.of(result.policy());
            monitor.setPendingPersistencePolicy(result.policy());
        }
        userFeedback.info("Політика persistence оновлена (з наступного poll-циклу)");
        if (result.sessionDbPath().isPresent()) {
            dirtyState.mark();
        }
    }

    private void onAlertsSettings() {
        AlertsSettingsDialog.show(
                dialogOwner(),
                options.alertOverrides().applyTo(profileDocument.active().alerts()),
                options.alertOverrides(),
                this::handleAlertsSettings);
    }

    private void handleAlertsSettings(AlertsSettingsDialog.Result result) {
        TracingProfile active = profileDocument.active();
        profileDocument.putProfile(profileDocument.activeProfile(), active.withAlerts(result.alerts()));
        AlertConfig effective = options.alertOverrides().applyTo(result.alerts());
        MonitorLifecycle.applyAlertDispatcher(
                monitor, effective, MonitorLifecycle.javaFxDesktopSink(this::dialogOwner));
        MonitorLifecycle.applyAlertRules(monitor, effective);
        dirtyState.mark();
        userFeedback.info("Сповіщення оновлено: " + result.alerts().toRedactedString() + " — «Зберегти» → YAML");
    }

    private void onTelemetrySettings() {
        TelemetrySettingsDialog.show(
                dialogOwner(),
                profileDocument.active().telemetry(),
                options.telemetryOverrides(),
                this::handleTelemetrySettings);
    }

    private void handleTelemetrySettings(TelemetrySettingsDialog.Result result) {
        TracingProfile active = profileDocument.active();
        profileDocument.putProfile(profileDocument.activeProfile(), active.withTelemetry(result.telemetry()));
        attachTelemetry(monitor);
        String sinks = telemetry != null && !telemetry.registeredIds().isEmpty()
                ? String.join(", ", telemetry.registeredIds())
                : "немає активних sinks";
        userFeedback.info(
                "Телеметрія оновлена: " + sinks + " — " + result.telemetry().toRedactedString());
        if (viewModeController.isExtended()) {
            mainView.statusLabel().setText("Телеметрія: " + sinks);
        }
        dirtyState.mark();
    }

    private void notifyPersistenceConnected(Path dbPath) {
        userFeedback.info("SQLite підключено: " + dbPath.toAbsolutePath());
        if (viewModeController.isExtended()) {
            mainView.statusLabel().setText("SQLite: " + dbPath.toAbsolutePath());
        }
    }

    private Optional<Path> resolveSessionDbPath() {
        return SessionDbResolver.resolve(
                options.sessionDbPath(), profileDocument.active().persistence().sessionDb(), sessionGuiDbOverride);
    }

    private io.pingui.persistence.SessionDatabase openSessionDatabase() {
        return resolveSessionDbPath()
                .map(io.pingui.persistence.SessionDatabase::new)
                .orElse(null);
    }

    private void attachTimeSeries(SessionStore sessionStore) {
        try {
            var backend = TimeSeriesBackends.create(options.timeSeriesOverrides());
            if (backend != null) {
                sessionStore.setTimeSeriesBackend(backend);
            }
        } catch (TimeSeriesConfigException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private void reconnectPersistence(Optional<PersistencePolicy> policyOverride) {
        dismissEasterEgg();
        List<HostEntry> liveEntries = HostViewRules.entriesForConfig(store.toHostEntries());
        TracingProfile profile = profileDocument.active();
        monitor.close();
        closeTelemetry();
        store.close();
        store = SessionStore.fromEntries(liveEntries, openSessionDatabase(), profile.hostProbeMode());
        attachTimeSeries(store);
        sessionPersistenceOverride = policyOverride != null ? policyOverride : Optional.empty();
        monitor = createMonitor(profile, liveEntries);
        updateHistoryPanelVisibility();
        hostListPresenter.rebuild(liveEntries);
        syncHistoryHostFilter();
        mainView.hostList().getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            mainView.hostList().getSelectionModel().select(0);
        }
        hostListPresenter.syncInputLimits();
        viewModeController.apply();
        resetReplayState();
        redrawRouteGraph();
    }

    private void applyCliOverridesToActiveProfile() {
        CliProfileOverrides profileOverrides = options.profileOverrides();
        CliTelemetryOverrides telemetryOverrides = options.telemetryOverrides();
        if (profileOverrides.isEmpty() && telemetryOverrides.isEmpty()) {
            return;
        }
        TracingProfile active = profileDocument.active();
        TracingProfile merged = profileOverrides.applyTo(active);
        merged = merged.withTelemetry(telemetryOverrides.applyTo(merged.telemetry()));
        profileDocument.putProfile(profileDocument.activeProfile(), merged);
    }

    private void reloadActiveProfile() {
        dismissEasterEgg();
        sessionPersistenceOverride = Optional.empty();
        sessionGuiDbOverride = Optional.empty();
        TracingProfile profile = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(profile.hosts());
        monitor.close();
        closeTelemetry();
        store.close();
        store = SessionStore.fromEntries(sessionHosts, openSessionDatabase(), profile.hostProbeMode());
        attachTimeSeries(store);
        monitor = createMonitor(profile, sessionHosts);
        updateHistoryPanelVisibility();
        hostListPresenter.rebuild(sessionHosts);
        syncHistoryHostFilter();
        mainView.hostList().getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            mainView.hostList().getSelectionModel().select(0);
        }
        hostListPresenter.syncInputLimits();
        viewModeController.apply();
        resetReplayState();
        redrawRouteGraph();
    }

    /** @return {@code true} when YAML was written successfully */
    private boolean onSaveConfig() {
        try {
            if (sessionGuiDbOverride.isPresent() && options.sessionDbPath().isEmpty()) {
                TracingProfile active = profileDocument.active();
                PersistenceConfig updated = active.persistence().withSessionDb(sessionGuiDbOverride.get());
                profileDocument.putProfile(profileDocument.activeProfile(), active.withPersistence(updated));
            }
            profileUi.syncActiveProfileFromSession();
            ProfilesConfig.save(options.configPath(), profileDocument);
            dirtyState.clear();
            userFeedback.info("Конфіг збережено (усі профілі): " + options.configPath());
            return true;
        } catch (IOException | ConfigError ex) {
            userFeedback.error("Не вдалося зберегти конфіг: " + ex.getMessage());
            return false;
        }
    }

    /** Re-applies dirty indicator after the Stage is shown. */
    public void refreshDirtyUi() {
        updateDirtyUi();
    }

    private void updateDirtyUi() {
        boolean dirty = dirtyState.isDirty();
        mainView.saveButton().setText(dirty ? "Зберегти *" : "Зберегти");
        Window window =
                mainView.root().getScene() != null ? mainView.root().getScene().getWindow() : null;
        if (window instanceof Stage stage) {
            stage.setTitle(dirty ? WINDOW_TITLE + " *" : WINDOW_TITLE);
        }
    }

    private ConfirmDialogs.UnsavedDecision confirmUnsavedChanges() {
        return ConfirmDialogs.confirmUnsaved(dialogOwner());
    }

    private void handleData(String host, RouteSnapshot snapshot) {
        if (!store.containsHost(host)) {
            return;
        }
        store.updateRoute(host, snapshot);
        store.appendPingSamples(host, snapshot);
        HostItem item = hostListPresenter.findItem(host);
        if (item != null) {
            hostListPresenter.syncMetrics(item);
            hostListPresenter.syncProblem(item);
        }
        if (viewModeController.isExtended() && !easterEggActive) {
            String activeHost = viewHost();
            if (activeHost != null && host.equals(activeHost)) {
                mainView.statusLabel()
                        .setText("Останнє оновлення [" + host + "]: " + TIME_FMT.format(snapshot.timestamp()));
                redrawRouteGraph();
            }
        }
    }

    private void handleRouteChanged(String host, List<String> oldIps, List<String> newIps) {
        if (!store.containsHost(host)) {
            return;
        }
        if (viewModeController.isExtended() && !easterEggActive) {
            if (!oldIps.isEmpty()) {
                String oldStr = String.join(" -> ", oldIps);
                userFeedback.info("⚠ ЗМІНА МАРШРУТУ до " + host + "\nБуло: " + oldStr + "\nСтало: "
                        + String.join(" -> ", newIps));
            }
            routeHistoryPresenter.onRouteChanged(host);
            String activeHost = viewHost();
            if (activeHost != null && host.equals(activeHost)) {
                routeGraphPresenter.clearReplay();
                routeHistoryPresenter.clearSelection();
                redrawRouteGraph();
            }
        }
    }

    private void startEasterEgg() {
        if (!HostViewRules.matches(mainView.hostInput().getText())) {
            return;
        }
        if (!easterEggActive) {
            easterEggActive = true;
            viewModeBeforeEasterEgg = viewModeController.viewMode();
            if (!viewModeController.isExtended()) {
                viewModeController.forceExtended(mainView::extendedModeButton);
                ensureExtendedStageWidth();
            }
        }
        showEasterEggCanvas();
        restartEasterEggTimer();
    }

    private void showEasterEggCanvas() {
        String message = HostViewRules.messageFor(mainView.hostInput().getText().strip());
        if (message != null) {
            routeGraphPresenter.showStaticMessage(message);
        }
    }

    private void restartEasterEggTimer() {
        if (easterEggTimer != null) {
            easterEggTimer.stop();
        }
        easterEggTimer = new PauseTransition(EASTER_EGG_DURATION);
        easterEggTimer.setOnFinished(e -> dismissEasterEgg());
        easterEggTimer.play();
    }

    private void dismissEasterEgg() {
        if (!easterEggActive) {
            return;
        }
        easterEggActive = false;
        if (easterEggTimer != null) {
            easterEggTimer.stop();
            easterEggTimer = null;
        }
        viewModeController.restoreMode(
                viewModeBeforeEasterEgg, mainView::simpleModeButton, mainView::extendedModeButton);
    }

    /** Modal error for Simple mode only (injected into {@link UiFeedbackRouter}). */
    private void showSimpleErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Помилка");
        alert.setHeaderText(null);
        Window owner = dialogOwner();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }
}

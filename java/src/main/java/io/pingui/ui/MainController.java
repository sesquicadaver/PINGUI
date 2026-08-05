package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.CliProfileOverrides;
import io.pingui.CliTelemetryOverrides;
import io.pingui.TelemetryAttachment;
import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.PersistenceConfig;
import io.pingui.config.PingPresets;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.config.TracingProfile;
import io.pingui.dns.DnsResolver;
import io.pingui.geoip.AsnLookup;
import io.pingui.geoip.GeoCountry;
import io.pingui.i18n.UiI18n;
import io.pingui.i18n.UiLocale;
import io.pingui.i18n.UiLocaleStore;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.ui.view.MainView;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Main JavaFX window: profiles, host list, optional route graph and event log. */
public final class MainController {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Window title without dirty suffix (shared with {@link io.pingui.PinguiApplication}). */
    public static String windowTitle() {
        return UiI18n.get("status.window_title");
    }

    /** Window title with dirty marker when YAML has unsaved edits. */
    public static String windowTitleDirty() {
        return UiI18n.get("status.window_title_dirty");
    }

    /**
     * Resolve UI locale before Scene creation: CLI {@code --lang} &gt; prefs &gt; Ukrainian canon.
     */
    public static void bootstrapUiLocale(AppOptions options) {
        UiLocale locale = UiLocale.UK;
        if (options != null && options.uiLang().isPresent()) {
            locale = UiLocale.fromCode(options.uiLang().get()).orElse(UiLocale.UK);
        } else {
            locale = UiLocaleStore.userDefault().load().orElse(UiLocale.UK);
        }
        UiI18n.setLocale(locale);
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
    private boolean switchingProfile;

    private ProfileUiCoordinator profileUi;
    private HostListPresenter hostListPresenter;
    private ViewModeController viewModeController;
    private UserFeedback userFeedback;
    private RouteGraphPresenter routeGraphPresenter;
    private RouteHistoryPresenter routeHistoryPresenter;
    private StageGeometryCoordinator stageGeometry;
    private SettingsDialogsCoordinator settingsDialogs;
    private PersistenceSessionCoordinator persistenceSession;
    private EasterEggController easterEgg;
    private MonitorUiHandler monitorUi;
    private final HistoryHostSync historyHostSync = new HistoryHostSync();

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
        initCoordinators();
        this.store = SessionStore.fromEntries(
                sessionHosts,
                persistenceSession.openSessionDatabase(),
                profileDocument.active().hostProbeMode());
        persistenceSession.attachTimeSeries(store);
        this.monitor = createMonitor(active, sessionHosts);
        this.servicesReady = true;
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

        MainViewActionsBinder actionsBinder = new MainViewActionsBinder(
                hostListPresenter,
                profileUi,
                this::refreshRouteHistory,
                this::dialogOwner,
                settingsDialogs,
                this::onSaveConfig,
                this::applyUiLocale);
        mainView.assemble(actionsBinder.bind(), hostListPresenter.tagFilterBar());
        updateDirtyUi();

        mainView.modeGroup().selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            viewModeController.onToggleSelected(newToggle);
            if (viewModeController.isExtended()) {
                stageGeometry.ensureExtendedStageGeometry();
            } else {
                Platform.runLater(stageGeometry::fitSimpleStageGeometryIfNeeded);
            }
            updateHistoryPanelVisibility();
        });

        mainView.hostInput().textProperty().addListener((obs, oldText, newText) -> {
            if (easterEgg.isActive() && !HostViewRules.matches(newText)) {
                easterEgg.dismiss();
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
        mainView.statusLabel().setText(UiI18n.get("status.loading"));
        setShellBusy(true);
        Scene scene = new Scene(mainView.root());
        UiPalette.applyTo(scene);
        return scene;
    }

    /** Attaches background-loaded services on the FX thread and starts polling. */
    public void attachBootstrap(StartupBootstrap.Result result) {
        if (shutdownRequested) {
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
                || UiI18n.get("status.loading").equals(mainView.statusLabel().getText())) {
            mainView.statusLabel().setText(EmptyStateHints.waitingForData());
        }
        redrawRouteGraph();
    }

    /** Surfaces bootstrap failure after the Stage is already shown (status only — no modal Alert). */
    public void onBootstrapFailed(Throwable error) {
        if (shutdownRequested) {
            return;
        }
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
        mainView.statusLabel().setText(UiI18n.get("status.load_error", message));
        if (viewModeController != null && viewModeController.isExtended()) {
            mainView.logArea()
                    .appendText("[" + TIME_FMT.format(java.time.Instant.now()) + "] "
                            + UiI18n.get("status.load_failed_log", message) + "\n");
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

    /** Persist locale, refresh chrome labels, update Stage title (P25). */
    void applyUiLocale(UiLocale locale) {
        if (locale == null || locale == UiI18n.locale()) {
            return;
        }
        UiI18n.setLocale(locale);
        UiLocaleStore.userDefault().save(locale);
        mainView.retranslateChrome();
        updateDirtyUi();
        if (hostListPresenter != null) {
            hostListPresenter.configure();
        }
        viewModeController.apply();
    }

    /**
     * Loads prefs, clamps to the visual screen, applies Simple layout once, sets stage bounds, and
     * registers close-only save (P24-006).
     */
    public void prepareStageGeometry(
            Stage stage,
            double defaultWidthSimple,
            double defaultWidthExtended,
            double defaultHeightSimple,
            double defaultHeightExtended) {
        stageGeometry.prepareStageGeometry(
                stage, defaultWidthSimple, defaultWidthExtended, defaultHeightSimple, defaultHeightExtended);
    }

    /** Post-show: remember divider for Extended, fit Simple Stage to chrome, scene-shown redraw. */
    public void onStageShown() {
        stageGeometry.onStageShown();
    }

    public void onSceneShown() {
        Platform.runLater(() -> {
            if (!easterEgg.isActive()) {
                routeGraphPresenter.redrawIfExtended();
            }
        });
    }

    public void shutdown() {
        shutdownRequested = true;
        easterEgg.dismiss();
        if (monitor != null) {
            monitor.close();
        }
        closeTelemetry();
        if (store != null) {
            store.close();
        }
    }

    /** Re-applies dirty indicator after the Stage is shown. */
    public void refreshDirtyUi() {
        updateDirtyUi();
    }

    private void initCoordinators() {
        if (viewModeController != null) {
            return;
        }
        MainCoordinators wired = MainCoordinators.wire(new MainCoordinators.Wiring(
                options,
                mainView,
                hostItems,
                expertMode,
                TIME_FMT,
                () -> profileDocument,
                value -> store = value,
                () -> store,
                () -> monitor,
                () -> telemetry,
                () -> switchingProfile,
                value -> switchingProfile = value,
                this::reloadActiveProfile,
                this::redrawRouteGraph,
                () -> {
                    if (easterEgg != null) {
                        easterEgg.showCanvas();
                    }
                },
                () -> easterEgg != null && easterEgg.isActive(),
                this::onSceneShown,
                this::showSimpleErrorAlert,
                this::dialogOwner,
                dirtyState::mark,
                dirtyState::isDirty,
                this::onSaveConfig,
                this::confirmUnsavedChanges,
                this::installMonitor,
                this::closeMonitorAndTelemetry,
                this::updateHistoryPanelVisibility,
                entries -> hostListPresenter.rebuild(entries),
                this::syncHistoryHostFilter,
                this::resetReplayState,
                this::clearHostSelection,
                () -> hostListPresenter.syncInputLimits(),
                this::recreateMonitorForProfileParams,
                () -> attachTelemetry(monitor),
                this::onHostRenamed,
                this::clearHistoryReplay,
                historyHostSync::runWhileSyncing,
                historyHostSync));
        viewModeController = wired.viewMode;
        userFeedback = wired.userFeedback;
        stageGeometry = wired.stageGeometry;
        persistenceSession = wired.persistenceSession;
        profileUi = wired.profileUi;
        routeGraphPresenter = wired.routeGraph;
        easterEgg = wired.easterEgg;
        hostListPresenter = wired.hostList;
        routeHistoryPresenter = wired.routeHistory;
        monitorUi = wired.monitorUi;
        settingsDialogs = wired.settingsDialogs;
    }

    private void setShellBusy(boolean busy) {
        if (mainView.root().getTop() != null) {
            mainView.root().getTop().setDisable(busy);
        }
        mainView.leftPanel().setDisable(busy);
        mainView.graphPanel().setDisable(busy);
        mainView.mainSplit().setDisable(busy);
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
        return new MonitorServiceFactory(
                        options,
                        () -> store,
                        () -> profileDocument.activeProfile(),
                        monitorUi,
                        hostListPresenter,
                        userFeedback,
                        this::dialogOwner,
                        persistenceSession::sessionPersistenceOverride,
                        this::attachTelemetry)
                .create(profile, sessionHosts);
    }

    private void installMonitor(List<HostEntry> entries) {
        monitor = createMonitor(profileDocument.active(), entries);
    }

    private void recreateMonitorForProfileParams(List<HostEntry> liveEntries) {
        monitor.close();
        closeTelemetry();
        monitor = createMonitor(profileDocument.active(), liveEntries);
    }

    private void closeMonitorAndTelemetry() {
        monitor.close();
        closeTelemetry();
    }

    private void attachTelemetry(MonitorService service) {
        Optional<io.pingui.persistence.SessionDatabase> sessionDb =
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

    private void reloadActiveProfile() {
        easterEgg.dismiss();
        persistenceSession.reloadActiveProfile();
    }

    private void clearHostSelection() {
        mainView.hostList().getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            mainView.hostList().getSelectionModel().select(0);
        }
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

    /** @return {@code true} when YAML was written successfully */
    private boolean onSaveConfig() {
        try {
            Optional<Path> guiDb = persistenceSession.sessionGuiDbOverride();
            if (guiDb.isPresent() && options.sessionDbPath().isEmpty()) {
                TracingProfile active = profileDocument.active();
                PersistenceConfig updated = active.persistence().withSessionDb(guiDb.get());
                profileDocument.putProfile(profileDocument.activeProfile(), active.withPersistence(updated));
            }
            profileUi.syncActiveProfileFromSession();
            ProfilesConfig.save(options.configPath(), profileDocument);
            dirtyState.clear();
            userFeedback.info(UiI18n.get("status.config_saved", options.configPath()));
            return true;
        } catch (IOException | ConfigError ex) {
            userFeedback.error(UiI18n.get("status.config_save_failed", ex.getMessage()));
            return false;
        }
    }

    private void updateDirtyUi() {
        boolean dirty = dirtyState.isDirty();
        mainView.saveButton().setText(dirty ? UiI18n.get("host.save_dirty") : UiI18n.get("host.save"));
        Window window =
                mainView.root().getScene() != null ? mainView.root().getScene().getWindow() : null;
        if (window instanceof Stage stage) {
            stage.setTitle(dirty ? windowTitleDirty() : windowTitle());
        }
    }

    private ConfirmDialogs.UnsavedDecision confirmUnsavedChanges() {
        return ConfirmDialogs.confirmUnsaved(dialogOwner());
    }

    /** Modal error for Simple mode only (injected into {@link UiFeedbackRouter}). */
    private void showSimpleErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(UiI18n.get("error.title"));
        alert.setHeaderText(null);
        Window owner = dialogOwner();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }
}

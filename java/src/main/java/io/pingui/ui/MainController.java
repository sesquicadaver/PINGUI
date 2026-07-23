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
import io.pingui.platform.PlatformCapabilities;
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
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Main JavaFX window: profiles, host list, optional route graph and event log. */
public final class MainController {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final double SIMPLE_PANEL_MIN_WIDTH = 580.0;
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
    private final ObservableList<HostItem> hostItems = FXCollections.observableArrayList();
    private final ListView<HostItem> hostList = new ListView<>();
    private final TextField hostInput = new TextField();
    private final TextArea logArea = new TextArea();
    private final GraphCanvas graphCanvas = new GraphCanvas();
    private final ListView<RouteHistoryItem> historyList = new ListView<>();
    private final RadioButton historyRange24h = new RadioButton("24 год");
    private final RadioButton historyRange7d = new RadioButton("7 днів");
    private final Label historyLabel = new Label("Історія змін");
    private final ComboBox<String> historyHostFilter = new ComboBox<>();
    private final HBox historyFilterBar = new HBox(8);
    private final HBox historyRangeBar = new HBox(8);
    private final Label statusLabel = new Label(EmptyStateHints.waitingForData());
    private final VBox graphPanel = new VBox(8);
    private final VBox leftPanel = new VBox(8);
    private final BorderPane root = new BorderPane();
    private final ComboBox<String> profileCombo = new ComboBox<>();
    private final SimpleBooleanProperty expertMode = new SimpleBooleanProperty(false);
    private final Button saveButton = new Button("Зберегти");
    private final ConfigDirtyState dirtyState = new ConfigDirtyState(this::updateDirtyUi);
    private RadioButton simpleModeButton;
    private RadioButton extendedModeButton;
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
        initCoordinators();
        hostListPresenter.rebuild(sessionHosts);
    }

    public Scene createScene() {
        hostListPresenter.configure();
        hostInput.setPromptText("IP або hostname…");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(SIMPLE_PANEL_MIN_WIDTH - 16);

        Button addButton = new Button("Додати");
        Button editButton = new Button("Змінити");
        Button tagsButton = new Button("Теги");
        Button removeButton = new Button("Видалити");
        addButton.setOnAction(e -> hostListPresenter.addHost());
        editButton.setOnAction(e -> hostListPresenter.editHost());
        tagsButton.setOnAction(e -> hostListPresenter.editSelectedHostTags());
        removeButton.setOnAction(e -> hostListPresenter.removeHost());
        saveButton.setOnAction(e -> onSaveConfig());
        hostInput.setOnAction(e -> hostListPresenter.addHost());
        updateDirtyUi();

        RadioButton simpleMode = new RadioButton("Простий");
        extendedModeButton = new RadioButton("Розширений");
        simpleModeButton = simpleMode;
        ToggleGroup modeGroup = new ToggleGroup();
        simpleMode.setToggleGroup(modeGroup);
        extendedModeButton.setToggleGroup(modeGroup);
        simpleMode.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            viewModeController.onToggleSelected(newToggle);
            updateHistoryPanelVisibility();
        });

        CheckBox expertCheck = new CheckBox("Експерт");
        if (PlatformCapabilities.expertPingSupported()) {
            expertCheck.selectedProperty().bindBidirectional(expertMode);
            expertMode.addListener((obs, was, on) -> hostList.refresh());
        } else {
            expertCheck.setDisable(true);
            expertCheck.setTooltip(new Tooltip("Expert ping (iputils ping) доступний лише на Linux"));
        }

        Button newProfileButton = new Button("Новий профіль");
        Button deleteProfileButton = new Button("Видалити профіль");
        newProfileButton.setOnAction(e -> profileUi.onNewProfile());
        deleteProfileButton.setOnAction(e -> profileUi.onDeleteProfile());
        profileUi.refreshCombo();
        profileCombo.setOnAction(e -> profileUi.onProfileSelected());

        HBox profileBar = new HBox(8, new Label("Профіль:"), profileCombo, newProfileButton, deleteProfileButton);
        profileCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(profileCombo, Priority.ALWAYS);

        HBox modeBar = new HBox(12, new Label("Режим:"), simpleMode, extendedModeButton, expertCheck);
        HBox buttons = new HBox(8, addButton, editButton, tagsButton, removeButton, saveButton);
        leftPanel
                .getChildren()
                .addAll(
                        profileBar,
                        modeBar,
                        hostListPresenter.tagFilterBar(),
                        hostList,
                        hostInput,
                        buttons,
                        statusLabel,
                        logArea);
        VBox.setVgrow(hostList, Priority.NEVER);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        leftPanel.setPadding(new Insets(8));
        leftPanel.setMinWidth(SIMPLE_PANEL_MIN_WIDTH);
        hostList.setPrefWidth(SIMPLE_PANEL_MIN_WIDTH);
        hostInput.setMaxWidth(Double.MAX_VALUE);
        hostInput.textProperty().addListener((obs, oldText, newText) -> {
            if (easterEggActive && !HostViewRules.matches(newText)) {
                dismissEasterEgg();
            }
        });

        graphPanel.getChildren().addAll(new Label("Граф маршруту"), graphCanvas, routeDiffPresenter.panel());
        configureHistoryPanel();
        VBox.setVgrow(graphCanvas, Priority.ALWAYS);
        graphPanel.setPadding(new Insets(8));
        graphCanvas.setMinSize(400, 280);

        root.setLeft(leftPanel);
        root.setTop(createMenuBar());

        hostList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                hostInput.setText(newItem.getHost());
            }
            hostListPresenter.syncInputLimits();
            // Host list is the live-graph source of truth: drop timeline replay for another target.
            if (oldItem != newItem) {
                clearHistoryReplay();
            }
            routeGraphPresenter.redrawIfExtended();
        });
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
        }
        hostListPresenter.syncInputLimits();
        viewModeController.apply();
        return new Scene(root, Color.web("#fafafa"));
    }

    public void onSceneShown() {
        Platform.runLater(() -> {
            if (!easterEggActive) {
                routeGraphPresenter.redrawIfExtended();
            }
            viewModeController.fitWindowToContent();
        });
    }

    public void shutdown() {
        dismissEasterEgg();
        monitor.close();
        closeTelemetry();
        store.close();
    }

    private void initCoordinators() {
        viewModeController = new ViewModeController(
                graphPanel,
                leftPanel,
                root,
                logArea,
                statusLabel,
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
                statusLabel::setText,
                message -> logArea.appendText("[" + TIME_FMT.format(java.time.Instant.now()) + "] " + message + "\n"),
                this::showSimpleErrorAlert);

        profileUi = new ProfileUiCoordinator(
                () -> profileDocument,
                () -> store,
                profileCombo,
                () -> switchingProfile,
                value -> switchingProfile = value,
                this::reloadActiveProfile,
                () -> profileUi.refreshCombo(),
                userFeedback);
        profileUi.setDirtyHooks(dirtyState::mark, dirtyState::isDirty, this::onSaveConfig, this::confirmUnsavedChanges);

        hostListPresenter = new HostListPresenter(
                hostItems,
                hostList,
                hostInput,
                () -> store,
                () -> monitor,
                expertMode,
                userFeedback,
                () -> hostListPresenter.syncInputLimits(),
                this::redrawRouteGraph,
                this::clearHistoryReplay,
                this::onHostRenamed,
                this::startEasterEgg,
                () -> viewModeController.fitWindowToContent(),
                historyHostSync::runWhileSyncing);
        hostListPresenter.setMarkDirty(dirtyState::mark);

        routeGraphPresenter = new RouteGraphPresenter(
                graphCanvas,
                hostList,
                () -> store,
                () -> viewModeController.isExtended(),
                () -> easterEggActive,
                routeDiffPresenter);
        graphCanvas.setOnHopIpCopied(ip -> userFeedback.info("Скопійовано hop IP: " + ip));
        DnsResolver.addListener(() -> Platform.runLater(routeGraphPresenter::redrawIfExtended));

        routeHistoryPresenter = new RouteHistoryPresenter(
                () -> store,
                historyHostFilter,
                historyList,
                historyRange24h,
                historyRange7d,
                () -> viewModeController.isExtended(),
                routeGraphPresenter::replayRouteChange,
                routeGraphPresenter::clearReplay);
        routeHistoryPresenter.configure();
        hostItems.addListener(
                (javafx.collections.ListChangeListener<? super HostItem>) change -> syncHistoryHostFilter());
        hostList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, item) -> {
            historyHostSync.syncFilterFromHostList(
                    item != null ? item.getHost() : null, historyHostFilter.getValue(), historyHostFilter::setValue);
        });
        historyHostFilter.valueProperty().addListener((obs, oldHost, newHost) -> {
            if (historyHostSync.isSyncing()) {
                return;
            }
            hostListPresenter.ensureHostVisibleForTagFilter(newHost);
            historyHostSync.syncHostListFromFilter(newHost, hostItems, hostList);
            redrawRouteGraph();
        });
    }

    private void syncHistoryHostFilter() {
        if (routeHistoryPresenter == null) {
            return;
        }
        routeHistoryPresenter.rebuildHostFilter(
                hostItems.stream().map(HostItem::getHost).toList());
    }

    private void configureHistoryPanel() {
        updateHistoryPanelVisibility();
        historyList.setPrefHeight(120);
        historyHostFilter.setPromptText("Оберіть ціль…");
        historyHostFilter.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(historyHostFilter, Priority.ALWAYS);
        historyFilterBar.getChildren().addAll(new Label("Ціль:"), historyHostFilter);
        Button refreshHistory = new Button("Оновити");
        refreshHistory.setOnAction(e -> refreshRouteHistory());
        historyRangeBar.getChildren().addAll(historyRange24h, historyRange7d, refreshHistory);
        graphPanel.getChildren().addAll(historyLabel, historyFilterBar, historyRangeBar, historyList);
        syncHistoryHostFilter();
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
        if (oldHost.equals(historyHostFilter.getValue())) {
            historyHostSync.runWhileSyncing(() -> historyHostFilter.setValue(newHost));
        }
        syncHistoryHostFilter();
    }

    /**
     * Active target for live graph updates — same source as {@link RouteGraphPresenter} (host list).
     * History filter is kept in sync via {@link HistoryHostSync}; do not prefer the filter here or
     * pings for host A can redraw while the list shows host B.
     */
    private String viewHost() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected.getHost();
        }
        String filterHost = historyHostFilter.getValue();
        return filterHost != null && !filterHost.isBlank() ? filterHost : null;
    }

    private void updateHistoryPanelVisibility() {
        // Extended graph panel owns history; always show list+label so empty-state hints are visible.
        // Filter/range only when SQLite session is connected (P20-007).
        boolean persistence = store.hasPersistence();
        historyLabel.setVisible(true);
        historyLabel.setManaged(true);
        historyList.setVisible(true);
        historyList.setManaged(true);
        historyFilterBar.setVisible(persistence);
        historyFilterBar.setManaged(persistence);
        historyRangeBar.setVisible(persistence);
        historyRangeBar.setManaged(persistence);
        refreshRouteHistory();
    }

    private MenuBar createMenuBar() {
        MenuItem saveItem = new MenuItem("Зберегти");
        saveItem.setAccelerator(KeyCombination.valueOf(AppAccelerators.SAVE));
        saveItem.setOnAction(e -> onSaveConfig());
        MenuItem addHostItem = new MenuItem("Додати ціль");
        addHostItem.setAccelerator(KeyCombination.valueOf(AppAccelerators.ADD_HOST));
        addHostItem.setOnAction(e -> hostListPresenter.addHost());
        Menu fileMenu = new Menu("Файл");
        fileMenu.getItems().addAll(saveItem, addHostItem);

        MenuItem aboutItem = new MenuItem("Про PINGUI…");
        aboutItem.setOnAction(e -> AppMenuDialogs.showAbout(dialogOwner()));
        Menu aboutMenu = new Menu("Про");
        aboutMenu.getItems().add(aboutItem);

        MenuItem helpItem = new MenuItem("Довідка…");
        helpItem.setAccelerator(KeyCombination.valueOf(AppAccelerators.HELP));
        helpItem.setOnAction(e -> AppMenuDialogs.showHelp(dialogOwner()));
        Menu helpMenu = new Menu("Довідка");
        helpMenu.getItems().add(helpItem);

        MenuItem databaseItem = new MenuItem("База даних…");
        databaseItem.setOnAction(e -> onPersistenceSettings());
        MenuItem profileParamsItem = new MenuItem("Профіль…");
        profileParamsItem.setOnAction(e -> onProfileParamsSettings());
        MenuItem alertsItem = new MenuItem("Сповіщення…");
        alertsItem.setOnAction(e -> onAlertsSettings());
        MenuItem telemetryItem = new MenuItem("Телеметрія…");
        telemetryItem.setOnAction(e -> onTelemetrySettings());
        MenuItem exportItem = new MenuItem("Експорт зараз…");
        exportItem.setOnAction(e -> onExportNow());
        Menu settingsMenu = new Menu("Налаштування");
        settingsMenu.getItems().addAll(databaseItem, profileParamsItem, alertsItem, telemetryItem, exportItem);

        MenuBar menuBar = new MenuBar(fileMenu, aboutMenu, settingsMenu, helpMenu);
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    private Window dialogOwner() {
        return root.getScene() != null ? root.getScene().getWindow() : null;
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
                        // info (not error): avoid Alert spam on recurring poll failures in Simple.
                        Platform.runLater(() -> userFeedback.info("Probe [" + host + "]: " + message));
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
        // YAML Save currently persists sessionDb override; policy-only Apply stays session-runtime.
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
        MonitorLifecycle.applyAlertDispatcher(monitor, effective, MonitorLifecycle.javaFxDesktopSink(this::dialogOwner));
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
            statusLabel.setText("Телеметрія: " + sinks);
        }
        dirtyState.mark();
    }

    private void notifyPersistenceConnected(Path dbPath) {
        userFeedback.info("SQLite підключено: " + dbPath.toAbsolutePath());
        if (viewModeController.isExtended()) {
            statusLabel.setText("SQLite: " + dbPath.toAbsolutePath());
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
        hostList.getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
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
        hostList.getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
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
        saveButton.setText(dirty ? "Зберегти *" : "Зберегти");
        Window window = root.getScene() != null ? root.getScene().getWindow() : null;
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
                statusLabel.setText("Останнє оновлення [" + host + "]: " + TIME_FMT.format(snapshot.timestamp()));
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
        if (!HostViewRules.matches(hostInput.getText())) {
            return;
        }
        if (!easterEggActive) {
            easterEggActive = true;
            viewModeBeforeEasterEgg = viewModeController.viewMode();
            if (!viewModeController.isExtended()) {
                viewModeController.forceExtended(() -> extendedModeButton);
            }
        }
        showEasterEggCanvas();
        restartEasterEggTimer();
    }

    private void showEasterEggCanvas() {
        String message = HostViewRules.messageFor(hostInput.getText().strip());
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
        viewModeController.restoreMode(viewModeBeforeEasterEgg, () -> simpleModeButton, () -> extendedModeButton);
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

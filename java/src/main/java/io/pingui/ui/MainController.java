package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.CliProfileOverrides;
import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.config.TracingProfile;
import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.platform.PlatformCapabilities;
import java.io.IOException;
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
import javafx.scene.control.Button;
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
import javafx.stage.Window;
import javafx.util.Duration;

/** Main JavaFX window: profiles, host list, optional route graph and event log. */
public final class MainController {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final double SIMPLE_PANEL_MIN_WIDTH = 580.0;
    private static final Duration EASTER_EGG_DURATION = Duration.seconds(30);

    private final AppOptions options;
    private ProfileDocument profileDocument;
    private SessionStore store;
    private MonitorService monitor;
    private final ObservableList<HostItem> hostItems = FXCollections.observableArrayList();
    private final ListView<HostItem> hostList = new ListView<>(hostItems);
    private final TextField hostInput = new TextField();
    private final TextArea logArea = new TextArea();
    private final GraphCanvas graphCanvas = new GraphCanvas();
    private final ListView<RouteHistoryItem> historyList = new ListView<>();
    private final RadioButton historyRange24h = new RadioButton("24 год");
    private final RadioButton historyRange7d = new RadioButton("7 днів");
    private final Label historyLabel = new Label("Історія змін");
    private final HBox historyRangeBar = new HBox(8);
    private final Label statusLabel = new Label("Очікування даних…");
    private final VBox graphPanel = new VBox(8);
    private final VBox leftPanel = new VBox(8);
    private final BorderPane root = new BorderPane();
    private final ComboBox<String> profileCombo = new ComboBox<>();
    private final SimpleBooleanProperty expertMode = new SimpleBooleanProperty(false);
    private RadioButton simpleModeButton;
    private RadioButton extendedModeButton;
    private UiViewMode viewModeBeforeEasterEgg = UiViewMode.SIMPLE;
    private boolean easterEggActive;
    private PauseTransition easterEggTimer;
    private boolean switchingProfile;
    private Optional<PersistencePolicy> sessionPersistenceOverride = Optional.empty();

    private ProfileUiCoordinator profileUi;
    private HostListPresenter hostListPresenter;
    private ViewModeController viewModeController;
    private RouteGraphPresenter routeGraphPresenter;
    private RouteHistoryPresenter routeHistoryPresenter;

    public MainController(AppOptions options, ProfileDocument document) {
        this.options = options;
        this.profileDocument = document;
        applyCliOverridesToActiveProfile();
        GeoCountry.configure(options.geoipEnabled(), options.geoipHintsPath());
        TracingProfile active = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(active.hosts());
        this.store = SessionStore.fromEntries(sessionHosts, openSessionDatabase());
        this.monitor = createMonitor(active);
        initCoordinators();
        hostListPresenter.rebuild(sessionHosts);
    }

    public Scene createScene() {
        hostListPresenter.configure();
        hostInput.setPromptText("IP або hostname…");
        logArea.setEditable(false);
        logArea.setWrapText(true);

        Button addButton = new Button("Додати");
        Button editButton = new Button("Змінити");
        Button removeButton = new Button("Видалити");
        Button saveButton = new Button("Зберегти");
        addButton.setOnAction(e -> hostListPresenter.addHost());
        editButton.setOnAction(e -> hostListPresenter.editHost());
        removeButton.setOnAction(e -> hostListPresenter.removeHost());
        saveButton.setOnAction(e -> onSaveConfig());
        hostInput.setOnAction(e -> hostListPresenter.addHost());

        RadioButton simpleMode = new RadioButton("Простий");
        extendedModeButton = new RadioButton("Розширений");
        simpleModeButton = simpleMode;
        ToggleGroup modeGroup = new ToggleGroup();
        simpleMode.setToggleGroup(modeGroup);
        extendedModeButton.setToggleGroup(modeGroup);
        simpleMode.setSelected(true);
        modeGroup
                .selectedToggleProperty()
                .addListener((obs, oldToggle, newToggle) -> viewModeController.onToggleSelected(newToggle));

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
        HBox buttons = new HBox(8, addButton, editButton, removeButton, saveButton);
        leftPanel.getChildren().addAll(profileBar, modeBar, hostList, hostInput, buttons, statusLabel, logArea);
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

        graphPanel.getChildren().addAll(new Label("Граф маршруту"), graphCanvas);
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
        store.close();
    }

    private void initCoordinators() {
        profileUi = new ProfileUiCoordinator(
                () -> profileDocument,
                () -> store,
                profileCombo,
                () -> switchingProfile,
                value -> switchingProfile = value,
                this::reloadActiveProfile,
                () -> profileUi.refreshCombo(),
                this::appendLog);

        hostListPresenter = new HostListPresenter(
                hostItems,
                hostList,
                hostInput,
                () -> store,
                () -> monitor,
                expertMode,
                this::appendLog,
                () -> hostListPresenter.syncInputLimits(),
                () -> routeGraphPresenter.redrawIfExtended(),
                this::startEasterEgg,
                () -> viewModeController.fitWindowToContent());

        viewModeController = new ViewModeController(
                graphPanel,
                leftPanel,
                root,
                logArea,
                statusLabel,
                () -> {
                    routeGraphPresenter.redrawIfExtended();
                    refreshRouteHistory();
                },
                this::showEasterEggCanvas,
                () -> easterEggActive);

        routeGraphPresenter = new RouteGraphPresenter(
                graphCanvas, hostList, () -> store, () -> viewModeController.isExtended(), () -> easterEggActive);

        routeHistoryPresenter = new RouteHistoryPresenter(
                () -> store,
                hostList,
                historyList,
                historyRange24h,
                historyRange7d,
                () -> viewModeController.isExtended(),
                routeGraphPresenter::replayRouteChange,
                routeGraphPresenter::clearReplay);
        routeHistoryPresenter.configure();
    }

    private void configureHistoryPanel() {
        updateHistoryPanelVisibility();
        historyList.setPrefHeight(120);
        Button refreshHistory = new Button("Оновити");
        refreshHistory.setOnAction(e -> refreshRouteHistory());
        historyRangeBar.getChildren().addAll(historyRange24h, historyRange7d, refreshHistory);
        graphPanel.getChildren().addAll(historyLabel, historyRangeBar, historyList);
    }

    private void refreshRouteHistory() {
        if (routeHistoryPresenter != null) {
            routeHistoryPresenter.refresh();
        }
    }

    private void updateHistoryPanelVisibility() {
        boolean persistence = store.hasPersistence();
        historyLabel.setVisible(persistence);
        historyLabel.setManaged(persistence);
        historyRangeBar.setVisible(persistence);
        historyRangeBar.setManaged(persistence);
        historyList.setVisible(persistence);
        historyList.setManaged(persistence);
        refreshRouteHistory();
    }

    private MenuBar createMenuBar() {
        MenuItem aboutItem = new MenuItem("Про PINGUI…");
        aboutItem.setOnAction(e -> AppMenuDialogs.showAbout(dialogOwner()));
        Menu aboutMenu = new Menu("Про");
        aboutMenu.getItems().add(aboutItem);

        MenuItem helpItem = new MenuItem("Довідка…");
        helpItem.setAccelerator(KeyCombination.valueOf("F1"));
        helpItem.setOnAction(e -> AppMenuDialogs.showHelp(dialogOwner()));
        Menu helpMenu = new Menu("Довідка");
        helpMenu.getItems().add(helpItem);

        MenuItem databaseItem = new MenuItem("База даних…");
        databaseItem.setOnAction(e -> onPersistenceSettings());
        databaseItem.setDisable(!store.hasPersistence());
        Menu settingsMenu = new Menu("Налаштування");
        settingsMenu.getItems().add(databaseItem);

        MenuBar menuBar = new MenuBar(aboutMenu, settingsMenu, helpMenu);
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    private Window dialogOwner() {
        return root.getScene() != null ? root.getScene().getWindow() : null;
    }

    private MonitorService createMonitor(TracingProfile profile) {
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
                        Platform.runLater(() -> appendLog("Помилка [" + host + "]: " + message));
                    }
                },
                options.alertOverrides().applyTo(profile.alerts()),
                store.database());
        applyPersistencePolicy(service, profile);
        return service;
    }

    private void applyPersistencePolicy(MonitorService service, TracingProfile profile) {
        PersistencePolicy baseline =
                options.persistenceOverrides().applyTo(profile.persistence()).toPolicy();
        PersistencePolicy effective = sessionPersistenceOverride.orElse(baseline);
        service.setPendingPersistencePolicy(effective);
        service.persistencePolicy().applyPendingAfterCycle();
    }

    private void onPersistenceSettings() {
        if (!store.hasPersistence()) {
            return;
        }
        PersistenceSettingsDialog.show(
                dialogOwner(),
                options.sessionDbPath(),
                options.persistenceOverrides(),
                monitor.persistencePolicy().active(),
                monitor.persistencePolicy().pending(),
                store.database(),
                policy -> {
                    sessionPersistenceOverride = Optional.of(policy);
                    monitor.setPendingPersistencePolicy(policy);
                    appendLog("Політика persistence оновлена (з наступного poll-циклу)");
                });
    }

    private io.pingui.persistence.SessionDatabase openSessionDatabase() {
        return options.sessionDbPath()
                .map(io.pingui.persistence.SessionDatabase::new)
                .orElse(null);
    }

    private void applyCliOverridesToActiveProfile() {
        CliProfileOverrides overrides = options.profileOverrides();
        if (overrides.isEmpty()) {
            return;
        }
        TracingProfile active = profileDocument.active();
        profileDocument.putProfile(profileDocument.activeProfile(), overrides.applyTo(active));
    }

    private void reloadActiveProfile() {
        dismissEasterEgg();
        sessionPersistenceOverride = Optional.empty();
        TracingProfile profile = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(profile.hosts());
        monitor.close();
        store.close();
        store = SessionStore.fromEntries(sessionHosts, openSessionDatabase());
        monitor = createMonitor(profile);
        updateHistoryPanelVisibility();
        hostListPresenter.rebuild(sessionHosts);
        hostList.getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
        }
        hostListPresenter.syncInputLimits();
        viewModeController.apply();
    }

    private void onSaveConfig() {
        try {
            profileUi.syncActiveProfileFromSession();
            ProfilesConfig.save(options.configPath(), profileDocument);
            appendLog("Конфіг збережено (усі профілі): " + options.configPath());
        } catch (IOException | ConfigError ex) {
            appendLog("Не вдалося зберегти конфіг: " + ex.getMessage());
        }
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
        }
        if (viewModeController.isExtended() && !easterEggActive) {
            HostItem selected = hostList.getSelectionModel().getSelectedItem();
            if (selected != null && host.equals(selected.getHost())) {
                statusLabel.setText("Останнє оновлення [" + host + "]: " + TIME_FMT.format(snapshot.timestamp()));
                routeGraphPresenter.redrawIfExtended();
            }
        }
    }

    private void handleRouteChanged(String host, List<String> oldIps, List<String> newIps) {
        if (!store.containsHost(host) || !viewModeController.isExtended()) {
            return;
        }
        String oldStr = oldIps.isEmpty() ? "Початок моніторингу" : String.join(" -> ", oldIps);
        appendLog("⚠ ЗМІНА МАРШРУТУ до " + host + "\nБуло: " + oldStr + "\nСтало: " + String.join(" -> ", newIps));
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected != null && host.equals(selected.getHost()) && !easterEggActive) {
            routeGraphPresenter.clearReplay();
            routeHistoryPresenter.clearSelection();
            routeGraphPresenter.redrawIfExtended();
        }
        refreshRouteHistory();
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

    private void appendLog(String message) {
        if (!viewModeController.isExtended()) {
            return;
        }
        logArea.appendText("[" + TIME_FMT.format(java.time.Instant.now()) + "] " + message + "\n");
    }
}

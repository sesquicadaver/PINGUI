package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.HostsConfig;
import io.pingui.config.PingExpertEntry;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.config.TracingProfile;
import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Main JavaFX window: profiles, host list, optional route graph and event log. */
public final class MainController {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final double HOST_ROW_HEIGHT = 56.0;
    private static final double HOST_LIST_INSET = 4.0;
    private static final double SIMPLE_PANEL_MIN_WIDTH = 540.0;
    private static final double EXTENDED_WIDTH = 1100.0;
    private static final double EXTENDED_HEIGHT = 700.0;
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
    private final Label statusLabel = new Label("Очікування даних…");
    private final VBox graphPanel = new VBox(8);
    private final VBox leftPanel = new VBox(8);
    private final BorderPane root = new BorderPane();
    private final ComboBox<String> profileCombo = new ComboBox<>();
    private final SimpleBooleanProperty expertMode = new SimpleBooleanProperty(false);
    private UiViewMode viewMode = UiViewMode.SIMPLE;
    private RadioButton simpleModeButton;
    private RadioButton extendedModeButton;
    private UiViewMode viewModeBeforeEasterEgg = UiViewMode.SIMPLE;
    private boolean easterEggActive;
    private PauseTransition easterEggTimer;
    private boolean updatingList;
    private boolean switchingProfile;

    public MainController(AppOptions options, ProfileDocument document) {
        this.options = options;
        this.profileDocument = document;
        applyCliOverridesToActiveProfile();
        GeoCountry.configure(options.geoipEnabled(), options.geoipHintsPath());
        TracingProfile active = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(active.hosts());
        this.store = SessionStore.fromEntries(sessionHosts);
        this.monitor = createMonitor(active);
        rebuildHostItems(sessionHosts);
    }

    public Scene createScene() {
        configureHostList();
        hostInput.setPromptText("IP або hostname…");
        logArea.setEditable(false);
        logArea.setWrapText(true);

        Button addButton = new Button("Додати");
        Button editButton = new Button("Змінити");
        Button removeButton = new Button("Видалити");
        Button saveButton = new Button("Зберегти");
        addButton.setOnAction(e -> onAddHost());
        editButton.setOnAction(e -> onEditHost());
        removeButton.setOnAction(e -> onRemoveHost());
        saveButton.setOnAction(e -> onSaveConfig());
        hostInput.setOnAction(e -> onAddHost());

        RadioButton simpleMode = new RadioButton("Простий");
        extendedModeButton = new RadioButton("Розширений");
        simpleModeButton = simpleMode;
        ToggleGroup modeGroup = new ToggleGroup();
        simpleMode.setToggleGroup(modeGroup);
        extendedModeButton.setToggleGroup(modeGroup);
        simpleMode.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> onViewModeSelected(newToggle));

        CheckBox expertCheck = new CheckBox("Експерт");
        if (PlatformCapabilities.expertPingSupported()) {
            expertCheck.selectedProperty().bindBidirectional(expertMode);
            expertMode.addListener((obs, was, on) -> hostList.refresh());
        } else {
            expertCheck.setDisable(true);
            expertCheck.setTooltip(
                    new Tooltip("Expert ping (iputils ping) доступний лише на Linux"));
        }

        Button newProfileButton = new Button("Новий профіль");
        Button deleteProfileButton = new Button("Видалити профіль");
        newProfileButton.setOnAction(e -> onNewProfile());
        deleteProfileButton.setOnAction(e -> onDeleteProfile());
        refreshProfileCombo();
        profileCombo.setOnAction(e -> onProfileSelected());

        HBox profileBar =
                new HBox(
                        8,
                        new Label("Профіль:"),
                        profileCombo,
                        newProfileButton,
                        deleteProfileButton);
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
        VBox.setVgrow(graphCanvas, Priority.ALWAYS);
        graphPanel.setPadding(new Insets(8));
        graphCanvas.setMinSize(400, 400);

        root.setLeft(leftPanel);

        hostList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                hostInput.setText(newItem.getHost());
            }
            syncControls();
            redrawRouteIfExtended();
        });
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
        }
        syncControls();
        applyViewMode();
        return new Scene(root, Color.web("#fafafa"));
    }

    public void onSceneShown() {
        Platform.runLater(() -> {
            if (!easterEggActive) {
                redrawRouteIfExtended();
            }
            fitWindowToContent();
        });
    }

    public void shutdown() {
        dismissEasterEgg();
        monitor.close();
    }

    private MonitorService createMonitor(TracingProfile profile) {
        MonitorService service =
                new MonitorService(
                        profile.intervalSeconds(),
                        profile.maxHops(),
                        profile.timeoutSeconds(),
                        profile.probeMode());
        service.setExpertResolver(store::getPingExpert);
        service.setListener(
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
                });
        for (HostEntry entry : profile.hosts()) {
            if (!HostViewRules.matches(entry.address())) {
                service.addHost(entry.address(), entry.enabled());
            }
        }
        return service;
    }

    private void applyCliOverridesToActiveProfile() {
        TracingProfile active = profileDocument.active();
        profileDocument.putProfile(
                profileDocument.activeProfile(),
                new TracingProfile(
                        options.intervalSeconds(),
                        options.maxHops(),
                        options.timeoutSeconds(),
                        options.probeMode(),
                        active.hosts()));
    }

    private void rebuildHostItems(List<HostEntry> entries) {
        hostItems.clear();
        for (HostEntry entry : HostViewRules.sessionEntries(entries)) {
            HostItem item = new HostItem(entry.address(), entry.enabled());
            item.setExpertConfigured(entry.pingExpert().isConfigured());
            hostItems.add(item);
        }
    }

    private void refreshProfileCombo() {
        switchingProfile = true;
        String active = profileDocument.activeProfile();
        profileCombo.setItems(FXCollections.observableArrayList(profileDocument.profiles().keySet()));
        profileCombo.getSelectionModel().select(active);
        switchingProfile = false;
    }

    private void syncActiveProfileFromSession() {
        TracingProfile current = profileDocument.active();
        List<HostEntry> hosts = HostViewRules.entriesForConfig(store.toHostEntries());
        profileDocument.putProfile(
                profileDocument.activeProfile(),
                new TracingProfile(
                        current.intervalSeconds(),
                        current.maxHops(),
                        current.timeoutSeconds(),
                        current.probeMode(),
                        hosts));
    }

    private void onProfileSelected() {
        if (switchingProfile) {
            return;
        }
        String selected = profileCombo.getSelectionModel().getSelectedItem();
        if (selected == null || selected.equals(profileDocument.activeProfile())) {
            return;
        }
        try {
            syncActiveProfileFromSession();
            profileDocument.setActiveProfile(selected);
            reloadActiveProfile();
            appendLog("Завантажено профіль: " + selected);
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
            refreshProfileCombo();
        }
    }

    private void onNewProfile() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Новий профіль");
        dialog.setHeaderText("Ім'я профілю трасування");
        dialog.setContentText("Назва:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }
        String name = result.get().strip();
        if (profileDocument.hasProfile(name)) {
            appendLog("Профіль уже існує: " + name);
            return;
        }
        try {
            syncActiveProfileFromSession();
            profileDocument.putProfile(name, TracingProfile.defaults(List.of()));
            profileDocument.setActiveProfile(name);
            reloadActiveProfile();
            refreshProfileCombo();
            appendLog("Створено профіль: " + name);
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
        }
    }

    private void onDeleteProfile() {
        String active = profileDocument.activeProfile();
        try {
            syncActiveProfileFromSession();
            profileDocument.removeProfile(active);
            reloadActiveProfile();
            refreshProfileCombo();
            appendLog("Видалено профіль: " + active);
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
        }
    }

    private void reloadActiveProfile() {
        dismissEasterEgg();
        TracingProfile profile = profileDocument.active();
        List<HostEntry> sessionHosts = HostViewRules.sessionEntries(profile.hosts());
        monitor.close();
        store = SessionStore.fromEntries(sessionHosts);
        monitor = createMonitor(profile);
        rebuildHostItems(sessionHosts);
        hostList.getSelectionModel().clearSelection();
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
        }
        syncControls();
        applyViewMode();
    }

    private void onOpenExpertPing(HostItem item, Void ignored) {
        PingExpertEntry current = store.getPingExpert(item.getHost());
        Optional<PingExpertEntry> updated = PingExpertDialog.show(item.getHost(), current);
        if (updated.isEmpty()) {
            return;
        }
        try {
            store.setPingExpert(item.getHost(), updated.get());
            item.setExpertConfigured(updated.get().isConfigured());
            appendLog(
                    "Expert ping [" + item.getHost() + "]: "
                            + (updated.get().isConfigured() ? updated.get().args() : "скинуто"));
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
        }
    }

    private void onViewModeSelected(Toggle toggle) {
        if (toggle == null) {
            return;
        }
        viewMode = ((RadioButton) toggle).getText().equals("Розширений") ? UiViewMode.EXTENDED : UiViewMode.SIMPLE;
        applyViewMode();
    }

    private void applyViewMode() {
        boolean extended = viewMode == UiViewMode.EXTENDED;
        graphPanel.setVisible(extended);
        graphPanel.setManaged(extended);
        logArea.setVisible(extended);
        logArea.setManaged(extended);
        statusLabel.setVisible(extended);
        statusLabel.setManaged(extended);
        root.setCenter(extended ? graphPanel : null);
        BorderPane.setMargin(leftPanel, extended ? new Insets(0, 4, 0, 0) : Insets.EMPTY);
        if (extended) {
            leftPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            if (easterEggActive) {
                showEasterEggCanvas();
            } else {
                redrawRouteIfExtended();
            }
        } else {
            leftPanel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            root.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        }
        fitWindowToContent();
    }

    private void fitWindowToContent() {
        Platform.runLater(() -> {
            Scene scene = root.getScene();
            if (scene == null || scene.getWindow() == null) {
                return;
            }
            if (viewMode == UiViewMode.SIMPLE) {
                leftPanel.applyCss();
                leftPanel.layout();
                root.applyCss();
                root.layout();
                // sizeToScene() often fails to shrink on Linux after EXTENDED setWidth/setHeight.
                double prefW = Math.max(SIMPLE_PANEL_MIN_WIDTH, root.prefWidth(-1));
                double prefH = Math.max(root.minHeight(-1), root.prefHeight(-1));
                scene.getWindow().setWidth(prefW);
                scene.getWindow().setHeight(prefH);
            } else {
                scene.getWindow().setWidth(EXTENDED_WIDTH);
                scene.getWindow().setHeight(EXTENDED_HEIGHT);
            }
        });
    }

    private void configureHostList() {
        hostList.setFixedCellSize(HOST_ROW_HEIGHT);
        hostList.setMaxHeight(listHeightForRows(HostsConfig.MAX_HOSTS));
        hostItems.addListener((ListChangeListener.Change<? extends HostItem> change) -> syncHostListHeight());
        syncHostListHeight();
        hostList.setCellFactory(
                list -> new HostListCell(this::onToggleEnabled, expertMode, this::onOpenExpertPing));
    }

    private void syncHostListHeight() {
        int rows = Math.max(1, hostItems.size());
        hostList.setPrefHeight(listHeightForRows(Math.min(rows, HostsConfig.MAX_HOSTS)));
        fitWindowToContent();
    }

    private static double listHeightForRows(int rows) {
        return rows * HOST_ROW_HEIGHT + HOST_LIST_INSET;
    }

    private void onToggleEnabled(HostItem item, boolean enabled) {
        try {
            monitor.setHostEnabled(item.getHost(), enabled);
            store.setEnabled(item.getHost(), enabled);
            updatingList = true;
            item.enabledProperty().set(enabled);
            updatingList = false;
            syncHostMetrics(item);
            redrawRouteIfExtended();
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
            updatingList = true;
            item.enabledProperty().set(store.get(item.getHost()).isEnabled());
            updatingList = false;
            syncHostMetrics(item);
        }
    }

    private void onAddHost() {
        String raw = hostInput.getText().strip();
        if (raw.isBlank()) {
            return;
        }
        if (HostViewRules.matches(raw)) {
            startEasterEgg();
            return;
        }
        try {
            String host = HostsConfig.validateSessionHost(raw, store.hosts());
            monitor.addHost(host, false);
            store.addHost(host, false);
            HostItem item = new HostItem(host, false);
            hostItems.add(item);
            hostList.getSelectionModel().select(item);
            hostInput.clear();
            appendLog("Додано ціль: " + host);
            syncControls();
            redrawRouteIfExtended();
        } catch (ConfigError ex) {
            appendLog("Не вдалося додати ціль: " + ex.getMessage());
        }
    }

    private void onEditHost() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String oldHost = selected.getHost();
        String newText = hostInput.getText().strip();
        if (newText.isBlank() || newText.equals(oldHost)) {
            return;
        }
        if (HostViewRules.matches(newText)) {
            startEasterEgg();
            return;
        }
        try {
            List<String> others = store.hosts().stream().filter(h -> !h.equals(oldHost)).toList();
            String renamed = HostsConfig.validateSessionHost(newText, others);
            monitor.renameHost(oldHost, renamed);
            store.renameHost(oldHost, renamed);
            selected.hostProperty().set(renamed);
            hostInput.setText(renamed);
            appendLog("Змінено ціль: " + oldHost + " → " + renamed);
            redrawRouteIfExtended();
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
        }
    }

    private void onRemoveHost() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String host = selected.getHost();
        try {
            monitor.removeHost(host);
            store.removeHost(host);
            hostItems.remove(selected);
            hostInput.clear();
            appendLog("Видалено ціль: " + host);
            syncControls();
            redrawRouteIfExtended();
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
        }
    }

    private void onSaveConfig() {
        try {
            syncActiveProfileFromSession();
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
        HostItem item = findHostItem(host);
        if (item != null) {
            syncHostMetrics(item);
        }
        if (viewMode == UiViewMode.EXTENDED && !easterEggActive) {
            HostItem selected = hostList.getSelectionModel().getSelectedItem();
            if (selected != null && host.equals(selected.getHost())) {
                statusLabel.setText("Останнє оновлення [" + host + "]: " + TIME_FMT.format(snapshot.timestamp()));
                redrawRouteIfExtended();
            }
        }
    }

    private void handleRouteChanged(String host, List<String> oldIps, List<String> newIps) {
        if (!store.containsHost(host) || viewMode != UiViewMode.EXTENDED) {
            return;
        }
        String oldStr = oldIps.isEmpty() ? "Початок моніторингу" : String.join(" -> ", oldIps);
        appendLog("⚠ ЗМІНА МАРШРУТУ до " + host + "\nБуло: " + oldStr + "\nСтало: " + String.join(" -> ", newIps));
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected != null && host.equals(selected.getHost()) && !easterEggActive) {
            redrawRouteIfExtended();
        }
    }

    private void syncHostMetrics(HostItem item) {
        if (!item.isEnabled()) {
            item.clearMetrics();
            return;
        }
        HostTargetStats stats = store.targetStats(item.getHost());
        if (stats == null) {
            item.clearMetrics();
            return;
        }
        item.applyMetrics(stats);
    }

    private HostItem findHostItem(String host) {
        for (HostItem item : hostItems) {
            if (host.equals(item.getHost())) {
                return item;
            }
        }
        return null;
    }

    private void startEasterEgg() {
        if (!HostViewRules.matches(hostInput.getText())) {
            return;
        }
        if (!easterEggActive) {
            easterEggActive = true;
            viewModeBeforeEasterEgg = viewMode;
            if (viewMode != UiViewMode.EXTENDED && extendedModeButton != null) {
                extendedModeButton.setSelected(true);
            }
        }
        showEasterEggCanvas();
        restartEasterEggTimer();
    }

    private void showEasterEggCanvas() {
        String message = HostViewRules.messageFor(hostInput.getText().strip());
        if (message != null) {
            graphCanvas.renderStaticView(message);
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
        UiViewMode restore = viewModeBeforeEasterEgg;
        if (restore == UiViewMode.SIMPLE && simpleModeButton != null) {
            simpleModeButton.setSelected(true);
        } else if (extendedModeButton != null) {
            extendedModeButton.setSelected(true);
        }
    }

    private void redrawRouteIfExtended() {
        if (viewMode != UiViewMode.EXTENDED || easterEggActive) {
            return;
        }
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            graphCanvas.renderRoute(List.of(), ip -> null, List.of());
            return;
        }
        String host = selected.getHost();
        graphCanvas.renderRoute(
                store.get(host).getCurrentRoute(),
                ip -> store.avgPing(host, ip),
                store.inactiveRoute(host),
                hop -> store.hopStatsSummary(host, hop));
    }

    private void appendLog(String message) {
        if (viewMode != UiViewMode.EXTENDED) {
            return;
        }
        logArea.appendText("[" + TIME_FMT.format(java.time.Instant.now()) + "] " + message + "\n");
    }

    private void syncControls() {
        boolean canAdd = store.canAddHost();
        hostInput.setDisable(!canAdd);
        if (!canAdd) {
            hostInput.setPromptText("Досягнуто ліміт 10 цілей у списку");
        } else {
            hostInput.setPromptText("IP або hostname…");
        }
    }
}

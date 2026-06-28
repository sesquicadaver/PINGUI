package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.config.ConfigError;
import io.pingui.config.HostsConfig;
import io.pingui.geoip.GeoCountry;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Main JavaFX window: host list, optional route graph and event log. */
public final class MainController {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final double HOST_ROW_HEIGHT = 52.0;
    private static final double HOST_LIST_INSET = 4.0;
    /** Minimum width for host row metrics (loss / min / avg / max). */
    private static final double SIMPLE_PANEL_MIN_WIDTH = 500.0;
    private static final double EXTENDED_WIDTH = 1100.0;
    private static final double EXTENDED_HEIGHT = 700.0;

    private final AppOptions options;
    private final SessionStore store;
    private final MonitorService monitor;
    private final ObservableList<HostItem> hostItems = FXCollections.observableArrayList();
    private final ListView<HostItem> hostList = new ListView<>(hostItems);
    private final TextField hostInput = new TextField();
    private final TextArea logArea = new TextArea();
    private final GraphCanvas graphCanvas = new GraphCanvas();
    private final Label statusLabel = new Label("Очікування даних…");
    private final VBox graphPanel = new VBox(8);
    private final VBox leftPanel = new VBox(8);
    private final BorderPane root = new BorderPane();
    private UiViewMode viewMode = UiViewMode.SIMPLE;
    private RadioButton extendedModeButton;
    private boolean updatingList;

    public MainController(AppOptions options, List<String> initialHosts) {
        this.options = options;
        GeoCountry.configure(options.geoipEnabled(), options.geoipHintsPath());
        this.store = new SessionStore(initialHosts);
        this.monitor =
                new MonitorService(
                        options.intervalSeconds(), options.maxHops(), options.timeoutSeconds(), options.probeMode());
        for (String host : initialHosts) {
            hostItems.add(new HostItem(host, false));
            monitor.addHost(host, false);
        }
        monitor.setListener(new MonitorService.Listener() {
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
        saveButton.setOnAction(e -> onSaveHosts());
        hostInput.setOnAction(e -> onAddHost());

        RadioButton simpleMode = new RadioButton("Простий");
        extendedModeButton = new RadioButton("Розширений");
        ToggleGroup modeGroup = new ToggleGroup();
        simpleMode.setToggleGroup(modeGroup);
        extendedModeButton.setToggleGroup(modeGroup);
        simpleMode.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> onViewModeSelected(newToggle));

        HBox modeBar = new HBox(12, new Label("Режим:"), simpleMode, extendedModeButton);
        HBox buttons = new HBox(8, addButton, editButton, removeButton, saveButton);
        leftPanel.getChildren().addAll(modeBar, hostList, hostInput, buttons, statusLabel, logArea);
        VBox.setVgrow(hostList, Priority.NEVER);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        leftPanel.setPadding(new Insets(8));
        leftPanel.setMinWidth(SIMPLE_PANEL_MIN_WIDTH);
        hostList.setPrefWidth(SIMPLE_PANEL_MIN_WIDTH);
        hostInput.setMaxWidth(Double.MAX_VALUE);

        graphPanel.getChildren().addAll(new Label("Граф маршруту"), graphCanvas);
        VBox.setVgrow(graphCanvas, Priority.ALWAYS);
        graphPanel.setPadding(new Insets(8));
        graphCanvas.setMinSize(400, 400);

        root.setLeft(leftPanel);

        hostList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                hostInput.setText(newItem.getHost());
                if (revealEasterEggForHost(newItem.getHost())) {
                    syncControls();
                    return;
                }
            }
            syncControls();
            redrawRouteIfExtended();
        });
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
        }
        syncControls();
        applyViewMode();
        return new Scene(root);
    }

    /** Call after {@code Stage.show()} so graph canvas has non-zero layout bounds. */
    public void onSceneShown() {
        Platform.runLater(() -> {
            HostItem selected = hostList.getSelectionModel().getSelectedItem();
            if (selected == null || !revealEasterEggForHost(selected.getHost())) {
                redrawRouteIfExtended();
            }
            fitWindowToContent();
        });
    }

    public void shutdown() {
        monitor.close();
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
            redrawRouteIfExtended();
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
                scene.getWindow().sizeToScene();
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
        hostList.setCellFactory(list -> new HostListCell(this::onToggleEnabled));
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
            if (!HostViewRules.matches(item.getHost())) {
                monitor.setHostEnabled(item.getHost(), enabled);
            } else if (enabled) {
                enabled = false;
            }
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
        String raw = hostInput.getText();
        if (raw.isBlank()) {
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
            if (!revealEasterEggForHost(host)) {
                redrawRouteIfExtended();
            }
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
        try {
            List<String> others = store.hosts().stream().filter(h -> !h.equals(oldHost)).toList();
            String renamed = HostsConfig.validateSessionHost(newText, others);
            monitor.renameHost(oldHost, renamed);
            store.renameHost(oldHost, renamed);
            selected.hostProperty().set(renamed);
            hostInput.setText(renamed);
            appendLog("Змінено ціль: " + oldHost + " → " + renamed);
            if (!revealEasterEggForHost(renamed)) {
                redrawRouteIfExtended();
            }
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

    private void onSaveHosts() {
        try {
            HostsConfig.save(options.configPath(), HostViewRules.hostsForConfig(store.hosts()));
            appendLog("Список цілей збережено: " + options.configPath());
        } catch (IOException | ConfigError ex) {
            appendLog("Не вдалося зберегти список: " + ex.getMessage());
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
        if (viewMode == UiViewMode.EXTENDED) {
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

    /** In Simple mode, switch to Extended and show the static canvas message. */
    private boolean revealEasterEggForHost(String host) {
        String message = HostViewRules.messageFor(host);
        if (message == null) {
            return false;
        }
        if (viewMode != UiViewMode.EXTENDED && extendedModeButton != null) {
            extendedModeButton.setSelected(true);
        }
        graphCanvas.renderStaticView(message);
        return true;
    }

    private void redrawRouteIfExtended() {
        if (viewMode != UiViewMode.EXTENDED) {
            return;
        }
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            graphCanvas.renderRoute(List.of(), ip -> null, List.of());
            return;
        }
        String host = selected.getHost();
        String staticView = HostViewRules.messageFor(host);
        if (staticView != null) {
            graphCanvas.renderStaticView(staticView);
            return;
        }
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

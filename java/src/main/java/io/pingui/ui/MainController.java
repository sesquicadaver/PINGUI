package io.pingui.ui;

import io.pingui.AppOptions;
import io.pingui.config.ConfigError;
import io.pingui.config.HostsConfig;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Main JavaFX window: host list, route view, event log. */
public final class MainController {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AppOptions options;
    private final SessionStore store;
    private final MonitorService monitor;
    private final ObservableList<HostItem> hostItems = FXCollections.observableArrayList();
    private final ListView<HostItem> hostList = new ListView<>(hostItems);
    private final TextField hostInput = new TextField();
    private final TextArea logArea = new TextArea();
    private final TextArea routeArea = new TextArea();
    private final Label statusLabel = new Label("Очікування даних…");
    private boolean updatingList;

    public MainController(AppOptions options, List<String> initialHosts) {
        this.options = options;
        this.store = new SessionStore(initialHosts);
        this.monitor = new MonitorService(options.intervalSeconds(), options.maxHops(), options.timeoutSeconds());
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
        routeArea.setEditable(false);
        routeArea.setWrapText(true);
        routeArea.setPromptText("Маршрут виділеної цілі…");

        Button addButton = new Button("Додати");
        Button editButton = new Button("Змінити");
        Button removeButton = new Button("Видалити");
        Button saveButton = new Button("Зберегти");
        addButton.setOnAction(e -> onAddHost());
        editButton.setOnAction(e -> onEditHost());
        removeButton.setOnAction(e -> onRemoveHost());
        saveButton.setOnAction(e -> onSaveHosts());
        hostInput.setOnAction(e -> onAddHost());

        HBox buttons = new HBox(8, addButton, editButton, removeButton, saveButton);
        VBox left = new VBox(8, hostList, hostInput, buttons, statusLabel, logArea);
        VBox.setVgrow(hostList, Priority.ALWAYS);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        left.setPadding(new Insets(8));

        VBox right = new VBox(8, new Label("Маршрут"), routeArea);
        VBox.setVgrow(routeArea, Priority.ALWAYS);
        right.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setLeft(left);
        root.setCenter(right);
        BorderPane.setMargin(left, new Insets(0, 4, 0, 0));

        hostList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                hostInput.setText(newItem.getHost());
            }
            syncControls();
            redrawRoute();
        });
        if (!hostItems.isEmpty()) {
            hostList.getSelectionModel().select(0);
        }
        syncControls();
        return new Scene(root, 1100, 700);
    }

    public void shutdown() {
        monitor.close();
    }

    private void configureHostList() {
        hostList.setCellFactory(list -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final Label label = new Label();

            {
                checkBox.selectedProperty().addListener((obs, was, isNow) -> {
                    HostItem item = getItem();
                    if (item != null && !updatingList && item.isEnabled() != isNow) {
                        onToggleEnabled(item, isNow);
                    }
                });
            }

            @Override
            protected void updateItem(HostItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                updatingList = true;
                checkBox.setSelected(item.isEnabled());
                label.setText(item.getHost());
                updatingList = false;
                HBox box = new HBox(8, checkBox, label);
                setGraphic(box);
            }
        });
    }

    private void onToggleEnabled(HostItem item, boolean enabled) {
        try {
            monitor.setHostEnabled(item.getHost(), enabled);
            store.setEnabled(item.getHost(), enabled);
            updatingList = true;
            item.enabledProperty().set(enabled);
            updatingList = false;
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
            updatingList = true;
            item.enabledProperty().set(store.get(item.getHost()).isEnabled());
            updatingList = false;
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
            redrawRoute();
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
            redrawRoute();
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
            redrawRoute();
        } catch (ConfigError ex) {
            appendLog(ex.getMessage());
        }
    }

    private void onSaveHosts() {
        try {
            HostsConfig.save(options.configPath(), store.hosts());
            appendLog("Список цілей збережено: " + options.configPath());
        } catch (IOException | ConfigError ex) {
            appendLog("Не вдалося зберегти список: " + ex.getMessage());
        }
    }

    private void handleData(String host, RouteSnapshot snapshot) {
        store.updateRoute(host, snapshot);
        store.appendPingSamples(host, snapshot);
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected != null && host.equals(selected.getHost())) {
            statusLabel.setText("Останнє оновлення [" + host + "]: " + TIME_FMT.format(snapshot.timestamp()));
            redrawRoute();
        }
    }

    private void handleRouteChanged(String host, List<String> oldIps, List<String> newIps) {
        String oldStr = oldIps.isEmpty() ? "Початок моніторингу" : String.join(" -> ", oldIps);
        appendLog("⚠ ЗМІНА МАРШРУТУ до " + host + "\nБуло: " + oldStr + "\nСтало: " + String.join(" -> ", newIps));
    }

    private void redrawRoute() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            routeArea.clear();
            return;
        }
        String host = selected.getHost();
        List<HopNode> inactive = store.inactiveRoute(host);
        List<HopNode> current = store.get(host).getCurrentRoute();
        StringBuilder builder = new StringBuilder();
        builder.append("Попередній (сірий):\n");
        builder.append(formatRoute(inactive)).append("\n\n");
        builder.append("Поточний:\n");
        builder.append(formatRoute(current));
        routeArea.setText(builder.toString());
    }

    private String formatRoute(List<HopNode> nodes) {
        if (nodes.isEmpty()) {
            return "  (немає даних)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("  Ваш ПК\n");
        for (HopNode node : nodes) {
            String ping = node.pingMs() != null ? String.format(" %.1f ms", node.pingMs()) : "";
            builder.append("    ↓ hop ").append(node.hop()).append(": ").append(node.ip()).append(ping).append('\n');
        }
        return builder.toString();
    }

    private void appendLog(String message) {
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

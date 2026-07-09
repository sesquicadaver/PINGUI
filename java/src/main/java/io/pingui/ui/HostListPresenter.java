package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.HostsConfig;
import io.pingui.config.PingExpertEntry;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.MonitorService;
import io.pingui.monitor.SessionStore;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/** Host list CRUD, toggles, and row metrics in the main window. */
final class HostListPresenter {
    private static final double HOST_ROW_HEIGHT = 56.0;
    private static final double HOST_LIST_INSET = 4.0;

    private final ObservableList<HostItem> hostItems;
    private final ListView<HostItem> hostList;
    private final TextField hostInput;
    private final Supplier<SessionStore> store;
    private final Supplier<MonitorService> monitor;
    private final SimpleBooleanProperty expertMode;
    private final Consumer<String> appendLog;
    private final Runnable syncControls;
    private final Runnable redrawRoute;
    private final Runnable clearHistoryReplay;
    private final java.util.function.BiConsumer<String, String> onHostRenamed;
    private final Runnable startEasterEgg;
    private final Runnable fitWindow;
    private boolean updatingList;

    HostListPresenter(
            ObservableList<HostItem> hostItems,
            ListView<HostItem> hostList,
            TextField hostInput,
            Supplier<SessionStore> store,
            Supplier<MonitorService> monitor,
            SimpleBooleanProperty expertMode,
            Consumer<String> appendLog,
            Runnable syncControls,
            Runnable redrawRoute,
            Runnable clearHistoryReplay,
            java.util.function.BiConsumer<String, String> onHostRenamed,
            Runnable startEasterEgg,
            Runnable fitWindow) {
        this.hostItems = hostItems;
        this.hostList = hostList;
        this.hostInput = hostInput;
        this.store = store;
        this.monitor = monitor;
        this.expertMode = expertMode;
        this.appendLog = appendLog;
        this.syncControls = syncControls;
        this.redrawRoute = redrawRoute;
        this.clearHistoryReplay = clearHistoryReplay;
        this.onHostRenamed = onHostRenamed;
        this.startEasterEgg = startEasterEgg;
        this.fitWindow = fitWindow;
    }

    void configure() {
        hostList.setFixedCellSize(HOST_ROW_HEIGHT);
        hostList.setMaxHeight(listHeightForRows(HostsConfig.MAX_HOSTS));
        hostItems.addListener((ListChangeListener.Change<? extends HostItem> change) -> syncListHeight());
        syncListHeight();
        hostList.setCellFactory(list ->
                new HostListCell(this::onToggleEnabled, this::onTogglePingOnly, expertMode, this::onOpenExpertPing));
    }

    void rebuild(List<HostEntry> entries) {
        hostItems.clear();
        for (HostEntry entry : HostViewRules.sessionEntries(entries)) {
            HostItem item = new HostItem(entry.address(), entry.enabled(), entry.pingOnly());
            item.setExpertConfigured(entry.pingExpert().isConfigured());
            hostItems.add(item);
        }
    }

    void syncMetrics(HostItem item) {
        if (!item.isEnabled()) {
            item.clearMetrics();
            return;
        }
        HostTargetStats stats = store.get().targetStats(item.getHost());
        if (stats == null) {
            item.clearMetrics();
            return;
        }
        item.applyMetrics(stats);
    }

    HostItem findItem(String host) {
        for (HostItem item : hostItems) {
            if (host.equals(item.getHost())) {
                return item;
            }
        }
        return null;
    }

    void addHost() {
        String raw = hostInput.getText().strip();
        if (raw.isBlank()) {
            return;
        }
        if (HostViewRules.matches(raw)) {
            startEasterEgg.run();
            return;
        }
        try {
            SessionStore session = store.get();
            String host = HostsConfig.validateSessionHost(raw, session.hosts());
            monitor.get().addHost(host, false, false);
            session.addHost(host, false, false, PingExpertEntry.empty());
            HostItem item = new HostItem(host, false);
            hostItems.add(item);
            hostList.getSelectionModel().select(item);
            hostInput.clear();
            appendLog.accept("Додано ціль: " + host);
            syncControls.run();
            redrawRoute.run();
        } catch (ConfigError ex) {
            appendLog.accept("Не вдалося додати ціль: " + ex.getMessage());
        }
    }

    void editHost() {
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
            startEasterEgg.run();
            return;
        }
        try {
            SessionStore session = store.get();
            List<String> others =
                    session.hosts().stream().filter(h -> !h.equals(oldHost)).toList();
            String renamed = HostsConfig.validateSessionHost(newText, others);
            monitor.get().renameHost(oldHost, renamed);
            session.renameHost(oldHost, renamed);
            selected.hostProperty().set(renamed);
            hostInput.setText(renamed);
            onHostRenamed.accept(oldHost, renamed);
            appendLog.accept("Змінено ціль: " + oldHost + " → " + renamed);
            clearHistoryReplay.run();
            redrawRoute.run();
        } catch (ConfigError ex) {
            appendLog.accept("Не вдалося змінити ціль: " + ex.getMessage());
        }
    }

    void removeHost() {
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String host = selected.getHost();
        try {
            monitor.get().removeHost(host);
            store.get().removeHost(host);
            hostItems.remove(selected);
            hostInput.clear();
            appendLog.accept("Видалено ціль: " + host);
            syncControls.run();
            redrawRoute.run();
        } catch (ConfigError ex) {
            appendLog.accept(ex.getMessage());
        }
    }

    void syncInputLimits() {
        boolean canAdd = store.get().canAddHost();
        hostInput.setDisable(!canAdd);
        if (!canAdd) {
            hostInput.setPromptText("Досягнуто ліміт 10 цілей у списку");
        } else {
            hostInput.setPromptText("IPv4, IPv6 literal або hostname…");
        }
    }

    private void syncListHeight() {
        int rows = Math.max(1, hostItems.size());
        hostList.setPrefHeight(listHeightForRows(Math.min(rows, HostsConfig.MAX_HOSTS)));
        fitWindow.run();
    }

    private static double listHeightForRows(int rows) {
        return rows * HOST_ROW_HEIGHT + HOST_LIST_INSET;
    }

    private void onToggleEnabled(HostItem item, boolean enabled) {
        try {
            monitor.get().setHostEnabled(item.getHost(), enabled);
            store.get().setEnabled(item.getHost(), enabled);
            updatingList = true;
            item.enabledProperty().set(enabled);
            updatingList = false;
            syncMetrics(item);
            clearHistoryReplay.run();
            redrawRoute.run();
        } catch (ConfigError ex) {
            appendLog.accept(ex.getMessage());
            updatingList = true;
            item.enabledProperty().set(store.get().get(item.getHost()).isEnabled());
            updatingList = false;
            syncMetrics(item);
        }
    }

    private void onTogglePingOnly(HostItem item, boolean pingOnly) {
        try {
            SessionStore session = store.get();
            monitor.get().setHostPingOnly(item.getHost(), pingOnly);
            session.setPingOnly(item.getHost(), pingOnly);
            if (pingOnly) {
                PingExpertEntry expert = session.getPingExpert(item.getHost());
                if (expert.applyToChain()) {
                    session.setPingExpert(item.getHost(), new PingExpertEntry(false, expert.args()));
                }
            }
            updatingList = true;
            item.pingOnlyProperty().set(pingOnly);
            updatingList = false;
            hostList.refresh();
            clearHistoryReplay.run();
            redrawRoute.run();
            appendLog.accept("Ping only [" + item.getHost() + "]: " + (pingOnly ? "увімкнено" : "вимкнено"));
        } catch (ConfigError ex) {
            appendLog.accept(ex.getMessage());
            updatingList = true;
            item.pingOnlyProperty().set(store.get().isPingOnly(item.getHost()));
            updatingList = false;
        }
    }

    private void onOpenExpertPing(HostItem item, Void ignored) {
        SessionStore session = store.get();
        PingExpertEntry current = session.getPingExpert(item.getHost());
        Optional<PingExpertEntry> updated = PingExpertDialog.show(item.getHost(), current, item.isPingOnly());
        if (updated.isEmpty()) {
            return;
        }
        try {
            session.setPingExpert(item.getHost(), updated.get());
            item.setExpertConfigured(updated.get().isConfigured());
            appendLog.accept("Expert ping [" + item.getHost() + "]: "
                    + (updated.get().isConfigured() ? updated.get().args() : "скинуто"));
        } catch (ConfigError ex) {
            appendLog.accept(ex.getMessage());
        }
    }
}

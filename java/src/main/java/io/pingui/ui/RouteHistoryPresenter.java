package io.pingui.ui;

import io.pingui.model.Models.HopNode;
import io.pingui.monitor.RouteChangeEvent;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistenceEventRecord;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.SessionDatabase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

/** Route-change timeline from SQLite (P11-020); selection triggers graph replay (P11-021). */
final class RouteHistoryPresenter {
    private static final int MAX_ROWS = 200;

    private final Supplier<SessionStore> store;
    private final ComboBox<String> hostFilter;
    private final ListView<RouteHistoryItem> historyList;
    private final RadioButton range24h;
    private final RadioButton range7d;
    private final BooleanSupplier extendedView;
    private final Consumer<RouteChangeEvent> onReplay;
    private final Runnable onClearReplay;
    private final Label placeholderLabel = new Label();

    private Duration lookback = Duration.ofHours(24);

    RouteHistoryPresenter(
            Supplier<SessionStore> store,
            ComboBox<String> hostFilter,
            ListView<RouteHistoryItem> historyList,
            RadioButton range24h,
            RadioButton range7d,
            BooleanSupplier extendedView,
            Consumer<RouteChangeEvent> onReplay,
            Runnable onClearReplay) {
        this.store = store;
        this.hostFilter = hostFilter;
        this.historyList = historyList;
        this.range24h = range24h;
        this.range7d = range7d;
        this.extendedView = extendedView;
        this.onReplay = onReplay;
        this.onClearReplay = onClearReplay;
    }

    void configure() {
        ToggleGroup rangeGroup = new ToggleGroup();
        range24h.setToggleGroup(rangeGroup);
        range7d.setToggleGroup(rangeGroup);
        range24h.setSelected(true);
        rangeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == range7d) {
                lookback = Duration.ofDays(7);
            } else {
                lookback = Duration.ofHours(24);
            }
            resetAndRefresh();
        });

        placeholderLabel.setWrapText(true);
        placeholderLabel.setStyle("-fx-text-fill: #666;");
        historyList.setPlaceholder(placeholderLabel);
        updatePlaceholder();

        historyList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(RouteHistoryItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.summary());
            }
        });
        historyList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                onClearReplay.run();
            } else {
                onReplay.accept(newItem.event());
            }
        });
        hostFilter.valueProperty().addListener((obs, oldHost, newHost) -> resetAndRefresh());
    }

    /** Repopulates host filter after profile / host list changes. */
    void rebuildHostFilter(List<String> hosts) {
        String previous = hostFilter.getValue();
        hostFilter.getItems().setAll(hosts);
        if (previous != null && hosts.contains(previous)) {
            if (!previous.equals(hostFilter.getValue())) {
                hostFilter.setValue(previous);
            } else {
                refreshPreservingSelection();
            }
            return;
        }
        if (!hosts.isEmpty()) {
            hostFilter.setValue(hosts.get(0));
        } else {
            hostFilter.setValue(null);
            clearHistoryContent();
        }
    }

    /** Refreshes timeline when a live route change affects the filtered host only. */
    void onRouteChanged(String host) {
        if (!HistoryHostSync.shouldRefreshHistoryForRouteChange(host, hostFilter.getValue())) {
            return;
        }
        refreshPreservingSelection();
    }

    private void resetAndRefresh() {
        clearHistoryContent();
        refresh();
    }

    private void clearHistoryContent() {
        historyList.getSelectionModel().clearSelection();
        historyList.setItems(FXCollections.observableArrayList());
        updatePlaceholder();
        onClearReplay.run();
    }

    void reloadKeepingFilter() {
        refreshPreservingSelection();
    }

    void refreshPreservingSelection() {
        RouteHistoryItem selected = historyList.getSelectionModel().getSelectedItem();
        long selectedId = selected != null ? selected.id() : -1L;
        refresh();
        if (selectedId < 0) {
            return;
        }
        for (RouteHistoryItem item : historyList.getItems()) {
            if (item.id() == selectedId) {
                historyList.getSelectionModel().select(item);
                return;
            }
        }
        onClearReplay.run();
    }

    void refresh() {
        ObservableList<RouteHistoryItem> items = FXCollections.observableArrayList();
        SessionStore session = store.get();
        if (!session.hasPersistence() || !extendedView.getAsBoolean()) {
            historyList.setItems(items);
            updatePlaceholder();
            return;
        }
        String host = hostFilter.getValue();
        if (host == null || host.isBlank()) {
            historyList.setItems(items);
            updatePlaceholder();
            return;
        }
        SessionDatabase database = session.database();
        Instant since = Instant.now().minus(lookback);
        List<PersistenceEventRecord> rows =
                database.listEvents(PersistenceEventType.ROUTE_CHANGE, host, since, MAX_ROWS);
        for (PersistenceEventRecord row : rows) {
            try {
                RouteChangeEvent event = RouteChangeEvent.fromJson(row.payloadJson());
                items.add(new RouteHistoryItem(row.id(), event));
            } catch (RuntimeException ignored) {
                // Skip malformed payloads.
            }
        }
        historyList.setItems(items);
        updatePlaceholder();
    }

    void clearSelection() {
        historyList.getSelectionModel().clearSelection();
        onClearReplay.run();
    }

    /** Visible placeholder text (for tests). */
    String placeholderText() {
        return placeholderLabel.getText();
    }

    private void updatePlaceholder() {
        SessionStore session = store.get();
        if (!session.hasPersistence()) {
            placeholderLabel.setText(EmptyStateHints.noSqlite());
            return;
        }
        String host = hostFilter.getValue();
        if (host == null || host.isBlank()) {
            placeholderLabel.setText(EmptyStateHints.noHostSelected());
            return;
        }
        placeholderLabel.setText(EmptyStateHints.emptyHistory());
    }

    static List<HopNode> ipsToRoute(List<String> ips) {
        List<HopNode> nodes = new ArrayList<>();
        for (int i = 0; i < ips.size(); i++) {
            nodes.add(new HopNode(i + 1, ips.get(i), null, false));
        }
        return List.copyOf(nodes);
    }
}

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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

/** Route-change timeline from SQLite (P11-020); selection triggers graph replay (P11-021). */
final class RouteHistoryPresenter {
    private static final int MAX_ROWS = 200;

    private final Supplier<SessionStore> store;
    private final ListView<HostItem> hostList;
    private final ListView<RouteHistoryItem> historyList;
    private final RadioButton range24h;
    private final RadioButton range7d;
    private final BooleanSupplier extendedView;
    private final Consumer<RouteChangeEvent> onReplay;
    private final Runnable onClearReplay;

    private Duration lookback = Duration.ofHours(24);

    RouteHistoryPresenter(
            Supplier<SessionStore> store,
            ListView<HostItem> hostList,
            ListView<RouteHistoryItem> historyList,
            RadioButton range24h,
            RadioButton range7d,
            BooleanSupplier extendedView,
            Consumer<RouteChangeEvent> onReplay,
            Runnable onClearReplay) {
        this.store = store;
        this.hostList = hostList;
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
            refresh();
        });

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
        hostList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            historyList.getSelectionModel().clearSelection();
            onClearReplay.run();
            refresh();
        });
    }

    void refresh() {
        ObservableList<RouteHistoryItem> items = FXCollections.observableArrayList();
        SessionStore session = store.get();
        if (!session.hasPersistence() || !extendedView.getAsBoolean()) {
            historyList.setItems(items);
            return;
        }
        HostItem selected = hostList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            historyList.setItems(items);
            return;
        }
        SessionDatabase database = session.database();
        Instant since = Instant.now().minus(lookback);
        List<PersistenceEventRecord> rows =
                database.listEvents(PersistenceEventType.ROUTE_CHANGE, selected.getHost(), since, MAX_ROWS);
        for (PersistenceEventRecord row : rows) {
            try {
                RouteChangeEvent event = RouteChangeEvent.fromJson(row.payloadJson());
                items.add(new RouteHistoryItem(row.id(), event));
            } catch (RuntimeException ignored) {
                // Skip malformed payloads.
            }
        }
        historyList.setItems(items);
    }

    void clearSelection() {
        historyList.getSelectionModel().clearSelection();
        onClearReplay.run();
    }

    static List<HopNode> ipsToRoute(List<String> ips) {
        List<HopNode> nodes = new ArrayList<>();
        for (int i = 0; i < ips.size(); i++) {
            nodes.add(new HopNode(i + 1, ips.get(i), null, false));
        }
        return List.copyOf(nodes);
    }
}

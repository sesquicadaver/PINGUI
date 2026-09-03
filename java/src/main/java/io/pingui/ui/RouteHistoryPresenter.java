package io.pingui.ui;

import io.pingui.model.Models.HopNode;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.IncidentTimeline;
import io.pingui.monitor.IncidentTimelineBuilder;
import io.pingui.monitor.IncidentTimelineEntry;
import io.pingui.monitor.RouteChangeEvent;
import io.pingui.monitor.SessionStore;
import io.pingui.persistence.PersistenceEventRecord;
import io.pingui.persistence.SessionDatabase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

/**
 * Per-host incident timeline from SQLite (+ live problem duration) (P11-020 / P29-002). Route-change
 * selection still triggers graph replay (P11-021).
 */
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
    private final Function<String, Optional<HostProblemSummary>> liveProblem;
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
        this(
                store,
                hostFilter,
                historyList,
                range24h,
                range7d,
                extendedView,
                onReplay,
                onClearReplay,
                host -> Optional.empty());
    }

    RouteHistoryPresenter(
            Supplier<SessionStore> store,
            ComboBox<String> hostFilter,
            ListView<RouteHistoryItem> historyList,
            RadioButton range24h,
            RadioButton range7d,
            BooleanSupplier extendedView,
            Consumer<RouteChangeEvent> onReplay,
            Runnable onClearReplay,
            Function<String, Optional<HostProblemSummary>> liveProblem) {
        this.store = store;
        this.hostFilter = hostFilter;
        this.historyList = historyList;
        this.range24h = range24h;
        this.range7d = range7d;
        this.extendedView = extendedView;
        this.onReplay = onReplay;
        this.onClearReplay = onClearReplay;
        this.liveProblem = liveProblem != null ? liveProblem : host -> Optional.empty();
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
            if (newItem == null || newItem.routeEvent().isEmpty()) {
                onClearReplay.run();
            } else {
                onReplay.accept(newItem.routeEvent().get());
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
        if (session == null || !session.hasPersistence() || !extendedView.getAsBoolean()) {
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
        Instant now = Instant.now();
        Instant since = now.minus(lookback);
        List<PersistenceEventRecord> rows = database.listHostEvents(host, since, MAX_ROWS);
        Optional<HostProblemSummary> live = liveProblem.apply(host);
        IncidentTimeline timeline = IncidentTimelineBuilder.build(host, rows, live, now);
        for (IncidentTimelineEntry entry : timeline.entries()) {
            items.add(new RouteHistoryItem(entry.id(), entry));
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
        if (session == null || !session.hasPersistence()) {
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

package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import io.pingui.ui.EmptyStateHints;
import io.pingui.ui.RouteHistoryItem;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Route / incident history chrome. Full list + filters only when SQLite persistence is on; otherwise
 * a one-line DB hint.
 */
public final class HistoryPanel {
    private final Label historyLabel = new Label();
    private final Label dbHintLabel = new Label();
    private final ListView<RouteHistoryItem> historyList = new ListView<>();
    private final RadioButton historyRange24h = new RadioButton();
    private final RadioButton historyRange7d = new RadioButton();
    private final ComboBox<String> historyHostFilter = new ComboBox<>();
    private final Label targetLabel = new Label();
    private final HBox historyFilterBar = new HBox(8);
    private final HBox historyRangeBar = new HBox(8);
    private final Button refreshHistory = new Button();

    public HistoryPanel() {
        historyList.setPrefHeight(120);
        historyHostFilter.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(historyHostFilter, Priority.ALWAYS);
        historyFilterBar.getChildren().addAll(targetLabel, historyHostFilter);
        historyRangeBar.getChildren().addAll(historyRange24h, historyRange7d, refreshHistory);
        dbHintLabel.getStyleClass().add("pingui-muted");
        dbHintLabel.setWrapText(true);
        dbHintLabel.setMaxWidth(Double.MAX_VALUE);
        retranslate();
        setPersistenceEnabled(false);
    }

    void wire(MainViewActions actions) {
        refreshHistory.setOnAction(e -> actions.onRefreshHistory());
    }

    void installInto(VBox graphPanel) {
        graphPanel.getChildren().addAll(historyLabel, dbHintLabel, historyFilterBar, historyRangeBar, historyList);
    }

    void retranslate() {
        historyLabel.setText(UiI18n.get("history.title"));
        historyRange24h.setText(UiI18n.get("history.range_24h"));
        historyRange7d.setText(UiI18n.get("history.range_7d"));
        historyHostFilter.setPromptText(UiI18n.get("history.target_prompt"));
        targetLabel.setText(UiI18n.get("history.target"));
        refreshHistory.setText(UiI18n.get("history.refresh"));
        dbHintLabel.setText(EmptyStateHints.noSqlite());
    }

    /**
     * Shows full history chrome when SQLite session is active; otherwise only a compact DB hint.
     *
     * @param enabled {@code true} when {@code SessionStore.hasPersistence()}
     */
    public void setPersistenceEnabled(boolean enabled) {
        historyLabel.setVisible(enabled);
        historyLabel.setManaged(enabled);
        historyFilterBar.setVisible(enabled);
        historyFilterBar.setManaged(enabled);
        historyRangeBar.setVisible(enabled);
        historyRangeBar.setManaged(enabled);
        historyList.setVisible(enabled);
        historyList.setManaged(enabled);
        dbHintLabel.setVisible(!enabled);
        dbHintLabel.setManaged(!enabled);
    }

    public Label historyLabel() {
        return historyLabel;
    }

    public Label dbHintLabel() {
        return dbHintLabel;
    }

    public ListView<RouteHistoryItem> historyList() {
        return historyList;
    }

    public RadioButton historyRange24h() {
        return historyRange24h;
    }

    public RadioButton historyRange7d() {
        return historyRange7d;
    }

    public ComboBox<String> historyHostFilter() {
        return historyHostFilter;
    }

    public HBox historyFilterBar() {
        return historyFilterBar;
    }

    public HBox historyRangeBar() {
        return historyRangeBar;
    }
}

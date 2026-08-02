package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import io.pingui.ui.RouteHistoryItem;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Route-history chrome (filter, range, list). Visibility rules stay in MainController. */
public final class HistoryPanel {
    private final Label historyLabel = new Label();
    private final ListView<RouteHistoryItem> historyList = new ListView<>();
    private final RadioButton historyRange24h = new RadioButton();
    private final RadioButton historyRange7d = new RadioButton();
    private final ComboBox<String> historyHostFilter = new ComboBox<>();
    private final Label targetLabel = new Label();
    private final HBox historyFilterBar = new HBox(8);
    private final HBox historyRangeBar = new HBox(8);
    private final Button refreshHistory = new Button();

    HistoryPanel() {
        historyList.setPrefHeight(120);
        historyHostFilter.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(historyHostFilter, Priority.ALWAYS);
        historyFilterBar.getChildren().addAll(targetLabel, historyHostFilter);
        historyRangeBar.getChildren().addAll(historyRange24h, historyRange7d, refreshHistory);
        retranslate();
    }

    void wire(MainViewActions actions) {
        refreshHistory.setOnAction(e -> actions.onRefreshHistory());
    }

    void installInto(VBox graphPanel) {
        graphPanel.getChildren().addAll(historyLabel, historyFilterBar, historyRangeBar, historyList);
    }

    void retranslate() {
        historyLabel.setText(UiI18n.get("history.title"));
        historyRange24h.setText(UiI18n.get("history.range_24h"));
        historyRange7d.setText(UiI18n.get("history.range_7d"));
        historyHostFilter.setPromptText(UiI18n.get("history.target_prompt"));
        targetLabel.setText(UiI18n.get("history.target"));
        refreshHistory.setText(UiI18n.get("history.refresh"));
    }

    Label historyLabel() {
        return historyLabel;
    }

    ListView<RouteHistoryItem> historyList() {
        return historyList;
    }

    RadioButton historyRange24h() {
        return historyRange24h;
    }

    RadioButton historyRange7d() {
        return historyRange7d;
    }

    ComboBox<String> historyHostFilter() {
        return historyHostFilter;
    }

    HBox historyFilterBar() {
        return historyFilterBar;
    }

    HBox historyRangeBar() {
        return historyRangeBar;
    }
}

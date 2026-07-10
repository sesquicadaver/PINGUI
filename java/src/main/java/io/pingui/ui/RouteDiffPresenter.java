package io.pingui.ui;

import io.pingui.model.Models.HopNode;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Hop-by-hop «було → стало» panel for extended view (P14-010). */
final class RouteDiffPresenter {
    private final ListView<RouteDiff.Row> diffList = new ListView<>();
    private final Label header = new Label("Diff маршруту");
    private final VBox panel = new VBox(4);

    RouteDiffPresenter() {
        diffList.setPrefHeight(110);
        diffList.setMinHeight(80);
        diffList.setFocusTraversable(false);
        diffList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(RouteDiff.Row item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.summary());
            }
        });
        panel.getChildren().addAll(header, diffList);
        VBox.setVgrow(diffList, Priority.NEVER);
        clear();
    }

    VBox panel() {
        return panel;
    }

    ListView<RouteDiff.Row> listView() {
        return diffList;
    }

    void show(List<HopNode> oldRoute, List<HopNode> newRoute) {
        List<HopNode> oldNodes = oldRoute != null ? oldRoute : List.of();
        List<HopNode> newNodes = newRoute != null ? newRoute : List.of();
        // No previous path yet (first baseline) — avoid labeling every hop as ADDED.
        if (oldNodes.isEmpty()) {
            clear();
            if (!newNodes.isEmpty()) {
                header.setText("Diff маршруту (початковий маршрут)");
            }
            return;
        }
        List<RouteDiff.Row> rows = RouteDiff.compare(oldNodes, newNodes);
        if (rows.isEmpty()) {
            clear();
            return;
        }
        if (!RouteDiff.hasChanges(rows)) {
            header.setText("Diff маршруту (без змін)");
        } else {
            long changed = rows.stream()
                    .filter(r -> r.kind() != RouteDiff.Kind.UNCHANGED)
                    .count();
            header.setText("Diff маршруту (" + changed + " змін)");
        }
        diffList.setItems(FXCollections.observableArrayList(rows));
    }

    void clear() {
        header.setText("Diff маршруту");
        diffList.setItems(FXCollections.observableArrayList());
    }
}

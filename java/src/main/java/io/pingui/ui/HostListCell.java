package io.pingui.ui;

import java.util.function.BiConsumer;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Host list row: checkbox, name, optional terminal-hop metrics, RTT color fill. */
final class HostListCell extends ListCell<HostItem> {
    private final CheckBox checkBox = new CheckBox();
    private final Label hostLabel = new Label();
    private final Label metricsLabel = new Label();
    private final VBox textBox = new VBox(2, hostLabel, metricsLabel);
    private final HBox root = new HBox(8, checkBox, textBox);
    private final BiConsumer<HostItem, Boolean> onEnabledChanged;
    private HostItem boundItem;
    private ChangeListener<String> rowColorListener;
    private boolean updating;

    HostListCell(BiConsumer<HostItem, Boolean> onEnabledChanged) {
        this.onEnabledChanged = onEnabledChanged;
        metricsLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");
        HBox.setHgrow(textBox, Priority.ALWAYS);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(4, 6, 4, 2));
        checkBox.selectedProperty().addListener((obs, was, isNow) -> {
            HostItem item = getItem();
            if (item != null && !updating && item.isEnabled() != isNow) {
                onEnabledChanged.accept(item, isNow);
            }
        });
    }

    @Override
    protected void updateItem(HostItem item, boolean empty) {
        super.updateItem(item, empty);
        unbindItem();
        if (empty || item == null) {
            setGraphic(null);
            setBackground(null);
            return;
        }
        boundItem = item;
        updating = true;
        checkBox.setSelected(item.isEnabled());
        hostLabel.textProperty().bind(item.hostProperty());
        metricsLabel.textProperty().bind(item.metricsTextProperty());
        metricsLabel.visibleProperty().bind(item.showMetricsProperty());
        metricsLabel.managedProperty().bind(item.showMetricsProperty());
        rowColorListener = (obs, was, color) -> applyBackground(color);
        item.rowColorProperty().addListener(rowColorListener);
        applyBackground(item.rowColorProperty().get());
        updating = false;
        setGraphic(root);
    }

    private void unbindItem() {
        if (boundItem == null) {
            return;
        }
        hostLabel.textProperty().unbind();
        metricsLabel.textProperty().unbind();
        metricsLabel.visibleProperty().unbind();
        metricsLabel.managedProperty().unbind();
        if (rowColorListener != null) {
            boundItem.rowColorProperty().removeListener(rowColorListener);
            rowColorListener = null;
        }
        boundItem = null;
    }

    private void applyBackground(String hex) {
        setBackground(new Background(new BackgroundFill(Color.web(hex), CornerRadii.EMPTY, Insets.EMPTY)));
    }
}

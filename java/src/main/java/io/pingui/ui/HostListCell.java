package io.pingui.ui;

import java.util.function.BiConsumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Host list row: enable, Ping only, optional Exten./MTU, problem badge, name, metrics. */
final class HostListCell extends ListCell<HostItem> {
    private final CheckBox checkBox = new CheckBox();
    private final CheckBox pingOnlyCheck = new CheckBox("Ping only");
    private final Button extenButton = new Button("Exten.");
    private final Button mtuButton = new Button("MTU");
    private final Button problemButton = new Button("!");
    private final Label hostLabel = new Label();
    private final Label tagsLabel = new Label();
    private final Label metricsLabel = new Label();
    private final HBox hostRow = new HBox(6, extenButton, mtuButton, problemButton, hostLabel);
    private final VBox textBox = new VBox(2, hostRow, tagsLabel, metricsLabel);
    private final HBox root = new HBox(8, checkBox, textBox, pingOnlyCheck);
    private final BiConsumer<HostItem, Boolean> onEnabledChanged;
    private final BiConsumer<HostItem, Boolean> onPingOnlyChanged;
    private final BooleanProperty expertMode;
    private final BiConsumer<HostItem, Void> onExpertOpen;
    private final BiConsumer<HostItem, Void> onMtuWizardOpen;
    private final BiConsumer<HostItem, Void> onProblemOpen;
    private HostItem boundItem;
    private ChangeListener<String> rowColorListener;
    private ChangeListener<Boolean> expertConfiguredListener;
    private ChangeListener<Boolean> expertModeListener;
    private ChangeListener<Boolean> problemUnreadListener;
    private boolean updating;

    HostListCell(
            BiConsumer<HostItem, Boolean> onEnabledChanged,
            BiConsumer<HostItem, Boolean> onPingOnlyChanged,
            BooleanProperty expertMode,
            BiConsumer<HostItem, Void> onExpertOpen,
            BiConsumer<HostItem, Void> onMtuWizardOpen,
            BiConsumer<HostItem, Void> onProblemOpen) {
        this.onEnabledChanged = onEnabledChanged;
        this.onPingOnlyChanged = onPingOnlyChanged;
        this.expertMode = expertMode;
        this.onExpertOpen = onExpertOpen;
        this.onMtuWizardOpen = onMtuWizardOpen;
        this.onProblemOpen = onProblemOpen;
        extenButton.setMinWidth(56);
        mtuButton.setMinWidth(48);
        mtuButton.setTooltip(new Tooltip("MTU discovery wizard (−s sweep + −M do)"));
        problemButton.setMinWidth(28);
        problemButton.setStyle("-fx-font-weight: bold; -fx-text-fill: #b71c1c;");
        problemButton.setTooltip(new Tooltip("Проблема доступності (endpoint_down)"));
        pingOnlyCheck.setStyle("-fx-font-size: 10px;");
        pingOnlyCheck.setMinWidth(72);
        extenButton.setOnAction(e -> {
            HostItem item = getItem();
            if (item != null) {
                onExpertOpen.accept(item, null);
            }
        });
        mtuButton.setOnAction(e -> {
            HostItem item = getItem();
            if (item != null) {
                onMtuWizardOpen.accept(item, null);
            }
        });
        problemButton.setOnAction(e -> {
            HostItem item = getItem();
            if (item != null) {
                onProblemOpen.accept(item, null);
            }
        });
        metricsLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");
        tagsLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");
        HBox.setHgrow(textBox, Priority.ALWAYS);
        HBox.setHgrow(hostLabel, Priority.ALWAYS);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(4, 6, 4, 2));
        checkBox.selectedProperty().addListener((obs, was, isNow) -> {
            HostItem item = getItem();
            if (item != null && !updating && item.isEnabled() != isNow) {
                onEnabledChanged.accept(item, isNow);
            }
        });
        pingOnlyCheck.selectedProperty().addListener((obs, was, isNow) -> {
            HostItem item = getItem();
            if (item != null && !updating && item.isPingOnly() != isNow) {
                onPingOnlyChanged.accept(item, isNow);
            }
        });
        expertModeListener = (obs, was, on) -> refreshExpertControls(getItem());
        expertMode.addListener(expertModeListener);
        refreshProblemBadge(false);
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
        pingOnlyCheck.setSelected(item.isPingOnly());
        hostLabel.textProperty().bind(item.hostProperty());
        tagsLabel.textProperty().bind(item.tagsTextProperty());
        tagsLabel.visibleProperty().bind(item.tagsTextProperty().isNotEmpty());
        tagsLabel.managedProperty().bind(item.tagsTextProperty().isNotEmpty());
        metricsLabel.textProperty().bind(item.metricsTextProperty());
        metricsLabel.visibleProperty().bind(item.showMetricsProperty());
        metricsLabel.managedProperty().bind(item.showMetricsProperty());
        rowColorListener = (obs, was, color) -> applyBackground(color);
        item.rowColorProperty().addListener(rowColorListener);
        expertConfiguredListener = (obs, was, configured) -> styleExtenButton(configured);
        item.expertConfiguredProperty().addListener(expertConfiguredListener);
        problemUnreadListener = (obs, was, unread) -> refreshProblemBadge(unread);
        item.problemUnreadProperty().addListener(problemUnreadListener);
        applyBackground(item.rowColorProperty().get());
        styleExtenButton(item.isExpertConfigured());
        refreshExpertControls(item);
        refreshProblemBadge(item.isProblemUnread());
        updating = false;
        setGraphic(root);
    }

    private void refreshExpertControls(HostItem item) {
        boolean show = expertMode.get() && item != null && !HostViewRules.matches(item.getHost());
        extenButton.setVisible(show);
        extenButton.setManaged(show);
        mtuButton.setVisible(show);
        mtuButton.setManaged(show);
    }

    private void refreshProblemBadge(boolean unread) {
        problemButton.setVisible(unread);
        problemButton.setManaged(unread);
    }

    private void styleExtenButton(boolean configured) {
        if (configured) {
            extenButton.setStyle("-fx-font-weight: bold;");
        } else {
            extenButton.setStyle("");
        }
    }

    private void unbindItem() {
        if (boundItem == null) {
            return;
        }
        hostLabel.textProperty().unbind();
        tagsLabel.textProperty().unbind();
        tagsLabel.visibleProperty().unbind();
        tagsLabel.managedProperty().unbind();
        metricsLabel.textProperty().unbind();
        metricsLabel.visibleProperty().unbind();
        metricsLabel.managedProperty().unbind();
        if (rowColorListener != null) {
            boundItem.rowColorProperty().removeListener(rowColorListener);
            rowColorListener = null;
        }
        if (expertConfiguredListener != null) {
            boundItem.expertConfiguredProperty().removeListener(expertConfiguredListener);
            expertConfiguredListener = null;
        }
        if (problemUnreadListener != null) {
            boundItem.problemUnreadProperty().removeListener(problemUnreadListener);
            problemUnreadListener = null;
        }
        boundItem = null;
    }

    private void applyBackground(String hex) {
        setBackground(new Background(new BackgroundFill(Color.web(hex), CornerRadii.EMPTY, Insets.EMPTY)));
    }
}

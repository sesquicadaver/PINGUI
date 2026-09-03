package io.pingui.ui;

import io.pingui.i18n.UiI18n;
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
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Unified host list row (P31-001): {@code [state] name RTT loss mode [problem]} with fixed columns;
 * poll/RTT detail and tags in tooltip.
 */
final class HostListCell extends ListCell<HostItem> {
    private static final double COL_RTT = 36.0;
    private static final double COL_LOSS = 40.0;
    private static final double COL_MODE = 44.0;

    private final CheckBox checkBox = new CheckBox();
    private final CheckBox pingOnlyCheck = new CheckBox(UiI18n.get("host.ping_only"));
    private final Button extenButton = new Button(UiI18n.get("host.exten"));
    private final Button mtuButton = new Button(UiI18n.get("host.mtu"));
    private final Button problemButton = new Button(UiI18n.get("host.problem_badge"));
    private final Label stateLabel = new Label();
    private final Label routeLabel = new Label();
    private final Label hostLabel = new Label();
    private final Label rttLabel = new Label();
    private final Label lossLabel = new Label();
    private final Label modeLabel = new Label();
    private final HBox mainRow = new HBox(
            6,
            stateLabel,
            routeLabel,
            hostLabel,
            rttLabel,
            lossLabel,
            modeLabel,
            problemButton,
            extenButton,
            mtuButton);
    private final HBox root = new HBox(8, checkBox, mainRow, pingOnlyCheck);
    private final BiConsumer<HostItem, Boolean> onEnabledChanged;
    private final BiConsumer<HostItem, Boolean> onPingOnlyChanged;
    private final BooleanProperty expertMode;
    private final BiConsumer<HostItem, Void> onExpertOpen;
    private final BiConsumer<HostItem, Void> onMtuWizardOpen;
    private final BiConsumer<HostItem, Void> onProblemOpen;
    private HostItem boundItem;
    private ChangeListener<String> rowColorListener;
    private ChangeListener<String> stateGlyphListener;
    private ChangeListener<String> routeGlyphListener;
    private ChangeListener<String> rttColumnListener;
    private ChangeListener<String> lossColumnListener;
    private ChangeListener<String> modeColumnListener;
    private ChangeListener<String> rowTooltipListener;
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
        configureColumn(stateLabel, 14.0, "pingui-host-state", Pos.CENTER);
        configureColumn(routeLabel, 14.0, "pingui-host-route", Pos.CENTER);
        configureColumn(rttLabel, COL_RTT, "pingui-host-col-rtt", Pos.CENTER_RIGHT);
        configureColumn(lossLabel, COL_LOSS, "pingui-host-col-loss", Pos.CENTER_RIGHT);
        configureColumn(modeLabel, COL_MODE, "pingui-host-col-mode", Pos.CENTER);
        extenButton.setMinWidth(Region.USE_PREF_SIZE);
        mtuButton.setMinWidth(Region.USE_PREF_SIZE);
        mtuButton.setTooltip(new Tooltip(UiI18n.get("host.mtu_tooltip")));
        problemButton.setMinWidth(Region.USE_PREF_SIZE);
        problemButton.getStyleClass().add("pingui-danger");
        problemButton.setTooltip(new Tooltip(UiI18n.get("host.problem_tooltip")));
        pingOnlyCheck.getStyleClass().add("pingui-muted");
        pingOnlyCheck.setMinWidth(Region.USE_PREF_SIZE);
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
        hostLabel.getStyleClass().add("pingui-host-name");
        HBox.setHgrow(mainRow, Priority.ALWAYS);
        HBox.setHgrow(hostLabel, Priority.ALWAYS);
        root.getStyleClass().add("pingui-host-row");
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
            setTooltip(null);
            return;
        }
        boundItem = item;
        updating = true;
        checkBox.setSelected(item.isEnabled());
        boolean tcpTarget = io.pingui.config.TcpEndpoint.looksLike(item.getHost());
        pingOnlyCheck.setDisable(tcpTarget);
        pingOnlyCheck.setSelected(!tcpTarget && item.isPingOnly());
        if (tcpTarget) {
            pingOnlyCheck.setText(UiI18n.get("host.tcp_connect"));
            pingOnlyCheck.setTooltip(new Tooltip(UiI18n.get("host.tcp_connect_tooltip")));
        } else {
            pingOnlyCheck.setText(UiI18n.get("host.ping_only"));
            pingOnlyCheck.setTooltip(null);
        }
        hostLabel.textProperty().bind(item.hostProperty());
        stateGlyphListener = (obs, was, glyph) -> stateLabel.setText(glyph);
        item.stateGlyphProperty().addListener(stateGlyphListener);
        stateLabel.setText(item.stateGlyphProperty().get());
        routeGlyphListener = (obs, was, glyph) -> routeLabel.setText(glyph);
        item.routeGlyphProperty().addListener(routeGlyphListener);
        routeLabel.setText(item.routeGlyphProperty().get());
        rttColumnListener = (obs, was, text) -> rttLabel.setText(text);
        item.rttColumnTextProperty().addListener(rttColumnListener);
        rttLabel.setText(item.rttColumnTextProperty().get());
        lossColumnListener = (obs, was, text) -> lossLabel.setText(text);
        item.lossColumnTextProperty().addListener(lossColumnListener);
        lossLabel.setText(item.lossColumnTextProperty().get());
        modeColumnListener = (obs, was, text) -> modeLabel.setText(text);
        item.modeColumnTextProperty().addListener(modeColumnListener);
        modeLabel.setText(item.modeColumnTextProperty().get());
        rowTooltipListener = (obs, was, text) -> applyRowTooltip(text);
        item.rowDetailsTooltipProperty().addListener(rowTooltipListener);
        applyRowTooltip(item.rowDetailsTooltipProperty().get());
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

    private static void configureColumn(Label label, double width, String styleClass, Pos alignment) {
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(alignment);
        label.getStyleClass().add(styleClass);
    }

    private void applyRowTooltip(String text) {
        if (text == null || text.isBlank()) {
            setTooltip(null);
        } else {
            setTooltip(new Tooltip(text));
        }
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
        if (stateGlyphListener != null) {
            boundItem.stateGlyphProperty().removeListener(stateGlyphListener);
            stateGlyphListener = null;
        }
        if (routeGlyphListener != null) {
            boundItem.routeGlyphProperty().removeListener(routeGlyphListener);
            routeGlyphListener = null;
        }
        if (rttColumnListener != null) {
            boundItem.rttColumnTextProperty().removeListener(rttColumnListener);
            rttColumnListener = null;
        }
        if (lossColumnListener != null) {
            boundItem.lossColumnTextProperty().removeListener(lossColumnListener);
            lossColumnListener = null;
        }
        if (modeColumnListener != null) {
            boundItem.modeColumnTextProperty().removeListener(modeColumnListener);
            modeColumnListener = null;
        }
        if (rowTooltipListener != null) {
            boundItem.rowDetailsTooltipProperty().removeListener(rowTooltipListener);
            rowTooltipListener = null;
        }
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

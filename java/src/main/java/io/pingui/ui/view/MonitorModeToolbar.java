package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import io.pingui.platform.PlatformCapabilities;
import io.pingui.ui.UiViewMode;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/** Simple/Extended radios + Expert checkbox chrome. */
public final class MonitorModeToolbar {
    private final RadioButton simpleToggle = new RadioButton();
    private final RadioButton extendedToggle = new RadioButton();
    private final CheckBox expertCheck = new CheckBox();
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final Label modeLabel = new Label();
    private final HBox bar = new HBox(12, modeLabel, simpleToggle, extendedToggle, expertCheck);

    MonitorModeToolbar() {
        simpleToggle.setToggleGroup(modeGroup);
        extendedToggle.setToggleGroup(modeGroup);
        simpleToggle.setUserData(UiViewMode.SIMPLE);
        extendedToggle.setUserData(UiViewMode.EXTENDED);
        simpleToggle.setSelected(true);
        if (!PlatformCapabilities.expertPingSupported()) {
            expertCheck.setDisable(true);
        }
        retranslate();
    }

    public void bindExpertMode(BooleanProperty expertMode) {
        if (PlatformCapabilities.expertPingSupported()) {
            expertCheck.selectedProperty().bindBidirectional(expertMode);
        }
    }

    void retranslate() {
        modeLabel.setText(UiI18n.get("mode.label"));
        simpleToggle.setText(UiI18n.get("mode.simple"));
        extendedToggle.setText(UiI18n.get("mode.extended"));
        expertCheck.setText(UiI18n.get("mode.expert"));
        if (!PlatformCapabilities.expertPingSupported()) {
            expertCheck.setTooltip(new Tooltip(UiI18n.get("mode.expert_linux_only")));
        }
    }

    RadioButton simpleToggle() {
        return simpleToggle;
    }

    RadioButton extendedToggle() {
        return extendedToggle;
    }

    ToggleGroup modeGroup() {
        return modeGroup;
    }

    public CheckBox expertCheck() {
        return expertCheck;
    }

    HBox bar() {
        return bar;
    }
}

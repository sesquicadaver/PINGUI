package io.pingui.ui.view;

import io.pingui.platform.PlatformCapabilities;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/** Simple/Extended radios + Expert checkbox chrome. */
public final class MonitorModeToolbar {
    private final RadioButton simpleToggle = new RadioButton("Простий");
    private final RadioButton extendedToggle = new RadioButton("Розширений");
    private final CheckBox expertCheck = new CheckBox("Експерт");
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final HBox bar = new HBox(12, new Label("Режим:"), simpleToggle, extendedToggle, expertCheck);

    MonitorModeToolbar() {
        simpleToggle.setToggleGroup(modeGroup);
        extendedToggle.setToggleGroup(modeGroup);
        simpleToggle.setSelected(true);
        if (PlatformCapabilities.expertPingSupported()) {
            // Binding applied later via bindExpertMode.
        } else {
            expertCheck.setDisable(true);
            expertCheck.setTooltip(new Tooltip("Expert ping (iputils ping) доступний лише на Linux"));
        }
    }

    public void bindExpertMode(BooleanProperty expertMode) {
        if (PlatformCapabilities.expertPingSupported()) {
            expertCheck.selectedProperty().bindBidirectional(expertMode);
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

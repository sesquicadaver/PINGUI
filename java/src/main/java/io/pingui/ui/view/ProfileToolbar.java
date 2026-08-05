package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Profile combo + new/delete chrome. */
public final class ProfileToolbar {
    private final ComboBox<String> profileCombo = new ComboBox<>();
    private final Button newProfileButton = new Button();
    private final Button deleteProfileButton = new Button();
    private final Label profileLabel = new Label();
    private final HBox bar = new HBox(8, profileLabel, profileCombo, newProfileButton, deleteProfileButton);

    ProfileToolbar() {
        profileCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(profileCombo, Priority.ALWAYS);
        retranslate();
    }

    void wire(MainViewActions actions) {
        newProfileButton.setOnAction(e -> actions.onNewProfile());
        deleteProfileButton.setOnAction(e -> actions.onDeleteProfile());
        profileCombo.setOnAction(e -> actions.onProfileSelected());
    }

    void retranslate() {
        profileLabel.setText(UiI18n.get("profile.label"));
        newProfileButton.setText(UiI18n.get("profile.new"));
        deleteProfileButton.setText(UiI18n.get("profile.delete"));
    }

    ComboBox<String> profileCombo() {
        return profileCombo;
    }

    HBox bar() {
        return bar;
    }
}

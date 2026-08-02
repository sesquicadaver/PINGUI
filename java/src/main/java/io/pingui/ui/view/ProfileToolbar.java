package io.pingui.ui.view;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Profile combo + new/delete chrome. */
public final class ProfileToolbar {
    private final ComboBox<String> profileCombo = new ComboBox<>();
    private final Button newProfileButton = new Button("Новий профіль");
    private final Button deleteProfileButton = new Button("Видалити профіль");
    private final HBox bar = new HBox(8, new Label("Профіль:"), profileCombo, newProfileButton, deleteProfileButton);

    ProfileToolbar() {
        profileCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(profileCombo, Priority.ALWAYS);
    }

    void wire(MainViewActions actions) {
        newProfileButton.setOnAction(e -> actions.onNewProfile());
        deleteProfileButton.setOnAction(e -> actions.onDeleteProfile());
        profileCombo.setOnAction(e -> actions.onProfileSelected());
    }

    ComboBox<String> profileCombo() {
        return profileCombo;
    }

    HBox bar() {
        return bar;
    }
}

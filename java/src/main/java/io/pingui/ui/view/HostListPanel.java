package io.pingui.ui.view;

import io.pingui.ui.HostItem;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Host list, input, CRUD + save button chrome. */
public final class HostListPanel {
    static final double PANEL_MIN_WIDTH = 580.0;

    private final ListView<HostItem> hostList = new ListView<>();
    private final TextField hostInput = new TextField();
    private final Button addButton = new Button("Додати");
    private final Button editButton = new Button("Змінити");
    private final Button tagsButton = new Button("Теги");
    private final Button removeButton = new Button("Видалити");
    private final Button saveButton = new Button("Зберегти");
    private final HBox buttons = new HBox(8, addButton, editButton, tagsButton, removeButton, saveButton);

    HostListPanel() {
        hostInput.setPromptText("IP або hostname…");
        hostInput.setMaxWidth(Double.MAX_VALUE);
        hostList.setPrefWidth(PANEL_MIN_WIDTH);
        VBox.setVgrow(hostList, Priority.NEVER);
    }

    void wire(MainViewActions actions) {
        addButton.setOnAction(e -> actions.onAddHost());
        editButton.setOnAction(e -> actions.onEditHost());
        tagsButton.setOnAction(e -> actions.onEditTags());
        removeButton.setOnAction(e -> actions.onRemoveHost());
        saveButton.setOnAction(e -> actions.onSaveConfig());
        hostInput.setOnAction(e -> actions.onAddHost());
    }

    /**
     * Layout slot for presenter-owned tag filter bar plus host chrome (D5).
     *
     * @return ordered nodes for the left column middle section
     */
    Node[] chromeWithTagFilter(Node tagFilterBar) {
        return new Node[] {tagFilterBar, hostList, hostInput, buttons};
    }

    ListView<HostItem> hostList() {
        return hostList;
    }

    TextField hostInput() {
        return hostInput;
    }

    Button saveButton() {
        return saveButton;
    }

    HBox buttons() {
        return buttons;
    }
}

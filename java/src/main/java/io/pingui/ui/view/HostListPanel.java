package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import io.pingui.ui.HostItem;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Host list, input, CRUD + save button chrome. */
public final class HostListPanel {
    static final double PANEL_MIN_WIDTH = 580.0;

    private final ListView<HostItem> hostList = new ListView<>();
    private final TextField hostInput = new TextField();
    private final Button addButton = new Button();
    private final Button editButton = new Button();
    private final Button tagsButton = new Button();
    private final Button removeButton = new Button();
    private final Button saveButton = new Button();
    private final FlowPane buttons = new FlowPane(8, 8);

    HostListPanel() {
        hostInput.setMaxWidth(Double.MAX_VALUE);
        hostList.setPrefWidth(PANEL_MIN_WIDTH);
        VBox.setVgrow(hostList, Priority.NEVER);
        for (Button button : new Button[] {addButton, editButton, tagsButton, removeButton, saveButton}) {
            button.setMinWidth(Region.USE_PREF_SIZE);
            buttons.getChildren().add(button);
        }
        buttons.setMinWidth(Region.USE_PREF_SIZE);
        buttons.setPrefWrapLength(PANEL_MIN_WIDTH - 24);
        retranslate();
    }

    void wire(MainViewActions actions) {
        addButton.setOnAction(e -> actions.onAddHost());
        editButton.setOnAction(e -> actions.onEditHost());
        tagsButton.setOnAction(e -> actions.onEditTags());
        removeButton.setOnAction(e -> actions.onRemoveHost());
        saveButton.setOnAction(e -> actions.onSaveConfig());
        hostInput.setOnAction(e -> actions.onAddHost());
    }

    void retranslate() {
        addButton.setText(UiI18n.get("host.add"));
        editButton.setText(UiI18n.get("host.edit"));
        tagsButton.setText(UiI18n.get("host.tags"));
        removeButton.setText(UiI18n.get("host.remove"));
        saveButton.setText(UiI18n.get("host.save"));
        hostInput.setPromptText(UiI18n.get("host.prompt"));
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

    FlowPane buttons() {
        return buttons;
    }
}

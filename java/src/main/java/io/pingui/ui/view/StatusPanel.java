package io.pingui.ui.view;

import io.pingui.ui.EmptyStateHints;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Status label + event log chrome for the left column. */
public final class StatusPanel {
    private final Label statusLabel = new Label(EmptyStateHints.waitingForData());
    private final TextArea logArea = new TextArea();

    StatusPanel() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(HostListPanel.PANEL_MIN_WIDTH - 16);
        VBox.setVgrow(logArea, Priority.ALWAYS);
    }

    Label statusLabel() {
        return statusLabel;
    }

    TextArea logArea() {
        return logArea;
    }
}

package io.pingui.ui.view;

import io.pingui.i18n.UiI18n;
import io.pingui.ui.EmptyStateHints;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Status area: durable monitoring summary, transient ops, optional progress, event log. */
public final class StatusPanel {
    private final Label monitoringLabel = new Label(EmptyStateHints.waitingForData());
    private final Label opsLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
    private final Button cancelButton = new Button();
    private final HBox progressRow = new HBox(8);
    private final TextArea logArea = new TextArea();
    private final VBox chrome = new VBox(4);

    public StatusPanel() {
        logArea.setEditable(false);
        logArea.setWrapText(true);
        monitoringLabel.setWrapText(true);
        monitoringLabel.setMaxWidth(HostListPanel.PANEL_MIN_WIDTH - 16);
        opsLabel.setWrapText(true);
        opsLabel.setMaxWidth(HostListPanel.PANEL_MIN_WIDTH - 16);
        opsLabel.getStyleClass().add("pingui-muted");
        opsLabel.setManaged(false);
        opsLabel.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        cancelButton.setText(UiI18n.get("status.op.cancel"));
        cancelButton.setFocusTraversable(false);
        progressRow.getChildren().setAll(progressBar, cancelButton);
        progressRow.setManaged(false);
        progressRow.setVisible(false);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        chrome.getChildren().setAll(monitoringLabel, opsLabel, progressRow, logArea);
    }

    /** Root chrome for the left column (monitoring + ops + progress + log). */
    public VBox chrome() {
        return chrome;
    }

    public Label monitoringLabel() {
        return monitoringLabel;
    }

    /** Backward-compatible alias for the durable monitoring line. */
    public Label statusLabel() {
        return monitoringLabel;
    }

    public Label opsLabel() {
        return opsLabel;
    }

    public ProgressBar progressBar() {
        return progressBar;
    }

    public Button cancelButton() {
        return cancelButton;
    }

    public HBox progressRow() {
        return progressRow;
    }

    public TextArea logArea() {
        return logArea;
    }

    public void setOps(String text) {
        String value = text != null ? text : "";
        opsLabel.setText(value);
        boolean show = !value.isBlank();
        opsLabel.setVisible(show);
        opsLabel.setManaged(show);
    }

    public void clearOps() {
        opsLabel.setText("");
        opsLabel.setVisible(false);
        opsLabel.setManaged(false);
        opsLabel.getStyleClass().remove("pingui-danger");
    }

    public void showProgress(boolean cancelVisible) {
        progressRow.setVisible(true);
        progressRow.setManaged(true);
        cancelButton.setVisible(cancelVisible);
        cancelButton.setManaged(cancelVisible);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    }

    public void hideProgress() {
        progressRow.setVisible(false);
        progressRow.setManaged(false);
        progressBar.setProgress(0);
    }
}

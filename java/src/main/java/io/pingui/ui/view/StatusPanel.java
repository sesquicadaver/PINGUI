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

/**
 * Compact status chrome: transient ops + optional progress. Durable monitoring summary and the event
 * log are kept as API fields for tests/bootstrap but are not shown in the left column (route changes
 * belong in incident history when DB is on).
 */
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
        logArea.setVisible(false);
        logArea.setManaged(false);
        monitoringLabel.setWrapText(true);
        monitoringLabel.setMaxWidth(HostListPanel.PANEL_MIN_WIDTH - 16);
        monitoringLabel.setVisible(false);
        monitoringLabel.setManaged(false);
        opsLabel.setWrapText(true);
        opsLabel.setMaxWidth(HostListPanel.PANEL_MIN_WIDTH - 16);
        opsLabel.getStyleClass().add("pingui-muted");
        opsLabel.setManaged(false);
        opsLabel.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        cancelButton.setText(UiI18n.get("status.op.cancel"));
        cancelButton.setFocusTraversable(true);
        progressBar.setAccessibleText(UiI18n.get("a11y.status_progress"));
        cancelButton.setAccessibleText(UiI18n.get("status.op.cancel"));
        monitoringLabel.setAccessibleText(UiI18n.get("a11y.status_monitoring"));
        opsLabel.setAccessibleText(UiI18n.get("a11y.status_ops"));
        logArea.setAccessibleText(UiI18n.get("a11y.status_log"));
        progressRow.getChildren().setAll(progressBar, cancelButton);
        progressRow.setManaged(false);
        progressRow.setVisible(false);
        chrome.getChildren().setAll(opsLabel, progressRow);
    }

    /** Root chrome for the left column (ops + progress only). */
    public VBox chrome() {
        return chrome;
    }

    public Label monitoringLabel() {
        return monitoringLabel;
    }

    /** Backward-compatible alias for the durable monitoring line (hidden from chrome). */
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

    /** Retained for bootstrap/error append paths; not shown in the left column. */
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

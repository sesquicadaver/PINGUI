package io.pingui.ui;

import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Shared OK/Cancel confirmation dialogs for destructive UI actions. */
final class ConfirmDialogs {
    private ConfirmDialogs() {}

    /**
     * Shows a confirmation Alert. Returns {@code true} only when the user chooses OK.
     *
     * @param owner optional owner window (may be null)
     * @param title dialog title
     * @param header header text (may be null)
     * @param content body text
     */
    static boolean confirm(Window owner, String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        if (owner != null) {
            alert.initOwner(owner);
        }
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }
}

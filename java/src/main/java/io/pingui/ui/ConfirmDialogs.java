package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Shared OK/Cancel confirmation dialogs for destructive UI actions. */
final class ConfirmDialogs {
    private ConfirmDialogs() {}

    /** Outcome of the unsaved-changes dialog on profile switch. */
    enum UnsavedDecision {
        SAVE,
        DISCARD,
        CANCEL
    }

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

    /**
     * Asks what to do with unsaved YAML changes before switching profile.
     *
     * @param owner optional owner window (may be null)
     */
    static UnsavedDecision confirmUnsaved(Window owner) {
        ButtonType save = new ButtonType(UiI18n.get("confirm.unsaved.save"), ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType(UiI18n.get("confirm.unsaved.discard"), ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType(UiI18n.get("confirm.unsaved.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(UiI18n.get("confirm.unsaved.title"));
        alert.setHeaderText(UiI18n.get("confirm.unsaved.header"));
        alert.setContentText(UiI18n.get("confirm.unsaved.content"));
        alert.getButtonTypes().setAll(save, discard, cancel);
        if (owner != null) {
            alert.initOwner(owner);
        }
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == cancel) {
            return UnsavedDecision.CANCEL;
        }
        if (answer.get() == save) {
            return UnsavedDecision.SAVE;
        }
        return UnsavedDecision.DISCARD;
    }
}

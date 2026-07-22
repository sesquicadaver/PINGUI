package io.pingui.ui;

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
        ButtonType save = new ButtonType("Зберегти", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Не зберігати", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Скасувати", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Незбережені зміни");
        alert.setHeaderText("Конфіг змінено, але не збережено у YAML.");
        alert.setContentText("Зберегти перед перемиканням профілю?");
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

package io.pingui.ui;

import io.pingui.CliPersistenceOverrides;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.PersistencePolicySupport;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** GUI for persistence event policy and purge (P11-014). */
public final class PersistenceSettingsDialog {
    private static final ButtonType KEEP_HISTORY = new ButtonType("Залишити історію", ButtonBar.ButtonData.NO);
    private static final ButtonType DELETE_HISTORY = new ButtonType("Видалити", ButtonBar.ButtonData.YES);

    private PersistenceSettingsDialog() {}

    /**
     * Shows persistence settings when {@code sessionDbPath} is present.
     *
     * @param onApply receives the new pending policy after purge confirmations
     */
    public static void show(
            Window owner,
            Optional<Path> sessionDbPath,
            CliPersistenceOverrides cliLocks,
            PersistencePolicy activePolicy,
            PersistencePolicy pendingPolicy,
            SessionDatabase database,
            Consumer<PersistencePolicy> onApply) {
        if (sessionDbPath.isEmpty() || database == null) {
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.initOwner(owner);
            info.setTitle("База даних");
            info.setHeaderText("SQLite не налаштовано");
            info.setContentText("Запустіть PINGUI з прапором --session-db PATH для збереження сесії.");
            info.showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("База даних");
        dialog.setHeaderText("Політика запису подій у SQLite");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        TextField pathField = new TextField(sessionDbPath.get().toAbsolutePath().toString());
        pathField.setEditable(false);
        pathField.setMaxWidth(Double.MAX_VALUE);

        CheckBox routeChangeCheck = new CheckBox("Зміни маршруту (route_change)");
        routeChangeCheck.setSelected(pendingPolicy.routeChange());
        cliLocks.routeChange().ifPresent(locked -> {
            routeChangeCheck.setDisable(true);
            routeChangeCheck.setTooltip(new javafx.scene.control.Tooltip("Заблоковано CLI"));
        });

        CheckBox probeErrorCheck = new CheckBox("Помилки probe (probe_error)");
        probeErrorCheck.setSelected(pendingPolicy.probeError());
        cliLocks.probeError().ifPresent(locked -> {
            probeErrorCheck.setDisable(true);
            probeErrorCheck.setTooltip(new javafx.scene.control.Tooltip("Заблоковано CLI"));
        });

        Label hint = new Label("Зміни набувають чинності після завершення поточного poll-циклу.");
        hint.setWrapText(true);

        VBox content = new VBox(10, new Label("Файл бази:"), pathField, routeChangeCheck, probeErrorCheck, hint);
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.APPLY) {
            return;
        }

        PersistencePolicy next = PersistencePolicy.of(routeChangeCheck.isSelected(), probeErrorCheck.isSelected());
        if (!confirmDisables(owner, activePolicy, next, database)) {
            return;
        }
        onApply.accept(next);
    }

    private static boolean confirmDisables(
            Window owner, PersistencePolicy active, PersistencePolicy next, SessionDatabase database) {
        List<PersistenceEventType> disabled = PersistencePolicySupport.typesBeingDisabled(active, next);
        for (PersistenceEventType type : disabled) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(owner);
            confirm.setTitle("Вимкнути запис подій");
            confirm.setHeaderText("Вимкнути запис: " + PersistencePolicySupport.labelUk(type) + "?");
            confirm.setContentText("Видалити з бази всі збережені події цього типу?");
            confirm.getButtonTypes().setAll(KEEP_HISTORY, DELETE_HISTORY, ButtonType.CANCEL);
            Optional<ButtonType> answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) {
                return false;
            }
            if (answer.get() == DELETE_HISTORY) {
                database.deleteEventsByType(type);
            }
        }
        return true;
    }
}

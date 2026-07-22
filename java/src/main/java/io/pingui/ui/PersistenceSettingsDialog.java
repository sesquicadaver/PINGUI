package io.pingui.ui;

import io.pingui.CliPersistenceOverrides;
import io.pingui.config.SessionDbAutoName;
import io.pingui.config.SessionDbResolver;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.PersistencePolicySupport;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** GUI for SQLite path and persistence event policy (P11-014, P11-016, P22-005). */
public final class PersistenceSettingsDialog {
    private static final ButtonType KEEP_HISTORY = new ButtonType("Залишити історію", ButtonBar.ButtonData.NO);
    private static final ButtonType DELETE_HISTORY = new ButtonType("Видалити", ButtonBar.ButtonData.YES);

    private PersistenceSettingsDialog() {}

    /** Result from the database settings dialog. */
    public record Result(Optional<Path> sessionDbPath, PersistencePolicy policy) {}

    /**
     * Shows persistence settings. When SQLite is not connected, user can pick a file and enable persistence.
     *
     * @param onApply receives path (when changed or newly set) and pending policy
     */
    public static void show(
            Window owner,
            Optional<Path> effectiveDbPath,
            Optional<Path> cliDbPath,
            Optional<Path> yamlDbPath,
            CliPersistenceOverrides cliLocks,
            PersistencePolicy activePolicy,
            PersistencePolicy pendingPolicy,
            SessionDatabase database,
            Consumer<Result> onApply) {
        boolean pathLockedByCli = SessionDbResolver.isCliLocked(cliDbPath);
        boolean canPickPath = SessionDbResolver.canPickGuiPath(cliDbPath, yamlDbPath);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("База даних");
        dialog.setHeaderText(database == null ? "Підключення SQLite" : "Політика запису подій у SQLite");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        TextField pathField = new TextField();
        pathField.setMaxWidth(Double.MAX_VALUE);
        if (effectiveDbPath.isPresent()) {
            pathField.setText(effectiveDbPath.get().toString());
        } else if (canPickPath) {
            pathField.setPromptText("наприклад, data/ping.db");
        }
        pathField.setEditable(canPickPath);

        Button browseButton = new Button("Обрати…");
        browseButton.setDisable(!canPickPath);
        browseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Файл SQLite");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite (*.db)", "*.db"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Усі файли", "*.*"));
            if (!pathField.getText().isBlank()) {
                Path current = Path.of(pathField.getText().strip());
                if (current.getParent() != null) {
                    chooser.setInitialDirectory(current.getParent().toFile());
                }
                chooser.setInitialFileName(current.getFileName().toString());
            }
            java.io.File chosen = chooser.showSaveDialog(owner);
            if (chosen != null) {
                pathField.setText(chosen.getPath());
            }
        });

        Button autoButton = new Button("Створити…");
        autoButton.setDisable(!canPickPath);
        autoButton.setTooltip(new Tooltip("data/YYYY-MM-DD_HH-mm-ss_<локальний-IP>.db"));
        autoButton.setOnAction(
                e -> pathField.setText(SessionDbAutoName.generate().toString()));

        if (pathLockedByCli) {
            pathField.setTooltip(new Tooltip("Шлях задано CLI (--session-db)"));
        } else if (!canPickPath) {
            pathField.setTooltip(new Tooltip("Шлях задано YAML (persistence.session_db)"));
        }

        HBox pathRow = new HBox(8, pathField, browseButton, autoButton);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        CheckBox routeChangeCheck = new CheckBox("Зміни маршруту (route_change)");
        routeChangeCheck.setSelected(pendingPolicy.routeChange());
        cliLocks.routeChange().ifPresent(ignored -> {
            routeChangeCheck.setDisable(true);
            routeChangeCheck.setTooltip(new Tooltip("Заблоковано CLI"));
        });

        CheckBox probeErrorCheck = new CheckBox("Помилки probe (probe_error)");
        probeErrorCheck.setSelected(pendingPolicy.probeError());
        cliLocks.probeError().ifPresent(ignored -> {
            probeErrorCheck.setDisable(true);
            probeErrorCheck.setTooltip(new Tooltip("Заблоковано CLI"));
        });

        Label hint = new Label(
                database == null
                        ? "Оберіть файл, «Створити…» (автоімʼя) або вкажіть шлях, потім «Застосувати»."
                        : "Зміни політики набувають чинності після завершення поточного poll-циклу.");
        hint.setWrapText(true);

        VBox content = new VBox(10, new Label("Файл бази:"), pathRow, routeChangeCheck, probeErrorCheck, hint);
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.APPLY) {
            return;
        }

        String pathText = pathField.getText() == null ? "" : pathField.getText().strip();
        if (pathText.isBlank()) {
            Alert error = new Alert(Alert.AlertType.WARNING);
            error.initOwner(owner);
            error.setTitle("База даних");
            error.setHeaderText("Не вказано файл бази");
            error.setContentText("Вкажіть шлях до SQLite або скасуйте.");
            error.showAndWait();
            return;
        }

        Path selectedPath = Path.of(pathText);
        PersistencePolicy next = PersistencePolicy.of(routeChangeCheck.isSelected(), probeErrorCheck.isSelected());
        if (database != null && !confirmDisables(owner, activePolicy, next, database)) {
            return;
        }

        boolean pathChanged =
                effectiveDbPath.isEmpty() || !effectiveDbPath.get().equals(selectedPath);
        if (pathChanged || database == null) {
            onApply.accept(new Result(Optional.of(selectedPath), next));
        } else {
            onApply.accept(new Result(Optional.empty(), next));
        }
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

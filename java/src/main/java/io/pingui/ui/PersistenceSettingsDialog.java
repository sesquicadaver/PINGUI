package io.pingui.ui;

import io.pingui.CliPersistenceOverrides;
import io.pingui.config.SessionDbAutoName;
import io.pingui.config.SessionDbResolver;
import io.pingui.i18n.UiI18n;
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
        dialog.setTitle(UiI18n.get("persistence.title"));
        dialog.setHeaderText(
                database == null ? UiI18n.get("persistence.header_connect") : UiI18n.get("persistence.header_policy"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        TextField pathField = new TextField();
        pathField.setMaxWidth(Double.MAX_VALUE);
        if (effectiveDbPath.isPresent()) {
            pathField.setText(effectiveDbPath.get().toString());
        } else if (canPickPath) {
            pathField.setPromptText(UiI18n.get("persistence.path_prompt"));
        }
        pathField.setEditable(canPickPath);

        Button browseButton = new Button(UiI18n.get("dialog.browse"));
        browseButton.setDisable(!canPickPath);
        browseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(UiI18n.get("persistence.sqlite_chooser"));
            chooser.getExtensionFilters()
                    .add(new FileChooser.ExtensionFilter(UiI18n.get("dialog.filter_sqlite"), "*.db"));
            chooser.getExtensionFilters()
                    .add(new FileChooser.ExtensionFilter(UiI18n.get("dialog.filter_all_files"), "*.*"));
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

        Button autoButton = new Button(UiI18n.get("dialog.create"));
        autoButton.setDisable(!canPickPath);
        autoButton.setTooltip(new Tooltip(UiI18n.get("persistence.auto_tooltip")));
        autoButton.setOnAction(
                e -> pathField.setText(SessionDbAutoName.generate().toString()));

        if (pathLockedByCli) {
            pathField.setTooltip(new Tooltip(UiI18n.get("persistence.path_cli")));
        } else if (!canPickPath) {
            pathField.setTooltip(new Tooltip(UiI18n.get("persistence.path_yaml")));
        }

        HBox pathRow = new HBox(8, pathField, browseButton, autoButton);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        CheckBox routeChangeCheck = new CheckBox(UiI18n.get("persistence.route_change"));
        routeChangeCheck.setSelected(pendingPolicy.routeChange());
        cliLocks.routeChange().ifPresent(ignored -> {
            routeChangeCheck.setDisable(true);
            routeChangeCheck.setTooltip(new Tooltip(UiI18n.get("dialog.locked_cli")));
        });

        CheckBox probeErrorCheck = new CheckBox(UiI18n.get("persistence.probe_error"));
        probeErrorCheck.setSelected(pendingPolicy.probeError());
        cliLocks.probeError().ifPresent(ignored -> {
            probeErrorCheck.setDisable(true);
            probeErrorCheck.setTooltip(new Tooltip(UiI18n.get("dialog.locked_cli")));
        });

        Label hint = new Label(
                database == null ? UiI18n.get("persistence.hint_connect") : UiI18n.get("persistence.hint_policy"));
        hint.setWrapText(true);

        VBox content = new VBox(
                10, new Label(UiI18n.get("persistence.path_label")), pathRow, routeChangeCheck, probeErrorCheck, hint);
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
            error.setTitle(UiI18n.get("persistence.title"));
            error.setHeaderText(UiI18n.get("persistence.path_missing_header"));
            error.setContentText(UiI18n.get("persistence.path_missing_content"));
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
        ButtonType keepHistory = new ButtonType(UiI18n.get("persistence.keep_history"), ButtonBar.ButtonData.NO);
        ButtonType deleteHistory = new ButtonType(UiI18n.get("persistence.delete_history"), ButtonBar.ButtonData.YES);
        List<PersistenceEventType> disabled = PersistencePolicySupport.typesBeingDisabled(active, next);
        for (PersistenceEventType type : disabled) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(owner);
            confirm.setTitle(UiI18n.get("persistence.disable_title"));
            confirm.setHeaderText(UiI18n.get("persistence.disable_header", eventLabel(type)));
            confirm.setContentText(UiI18n.get("persistence.disable_content"));
            confirm.getButtonTypes().setAll(keepHistory, deleteHistory, ButtonType.CANCEL);
            Optional<ButtonType> answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) {
                return false;
            }
            if (answer.get() == deleteHistory) {
                database.deleteEventsByType(type);
            }
        }
        return true;
    }

    private static String eventLabel(PersistenceEventType type) {
        return switch (type) {
            case ROUTE_CHANGE -> UiI18n.get("persistence.label.route_change");
            case PROBE_ERROR -> UiI18n.get("persistence.label.probe_error");
            case ENDPOINT_DOWN -> UiI18n.get("persistence.label.endpoint_down");
            case LATENCY_HIGH -> UiI18n.get("persistence.label.latency_high");
        };
    }
}

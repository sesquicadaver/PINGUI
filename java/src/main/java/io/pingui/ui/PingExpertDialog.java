package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.PingExpertEntry;
import io.pingui.probe.PingExpertValidator;
import io.pingui.probe.PingOptionCatalog;
import io.pingui.probe.PingOptionCatalog.Kind;
import io.pingui.probe.PingOptionCatalog.PingOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Dialog for per-host expert ping flags (iputils ping). */
public final class PingExpertDialog {
    private static final String BOOL_FALSE = "false";
    private static final String BOOL_TRUE = "true";

    private PingExpertDialog() {}

    public static Optional<PingExpertEntry> show(String host, PingExpertEntry current) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Expert ping — " + host);
        dialog.setHeaderText("Параметри ping(8) для цілі (без -c/-w/-W/-i та інших лімітів часу/кількості)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(560);

        CheckBox chainCheck = new CheckBox("Застосувати до всього ланцюжка");
        chainCheck.setSelected(current != null && current.applyToChain());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(8, 0, 0, 0));
        ColumnConstraints flagCol = new ColumnConstraints();
        flagCol.setMinWidth(48);
        ColumnConstraints descCol = new ColumnConstraints();
        descCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(flagCol, descCol, new ColumnConstraints(140));

        Map<String, ComboBox<String>> flagChoices = new HashMap<>();
        Map<String, TextField> valueFields = new HashMap<>();
        List<String> currentArgs = current != null ? current.args() : List.of();
        int row = 0;
        grid.add(new Label("Опція"), 0, row);
        grid.add(new Label("Опис"), 1, row);
        grid.add(new Label("Значення"), 2, row);
        row++;
        for (PingOption option : PingOptionCatalog.options()) {
            grid.add(new Label(option.flag()), 0, row);
            grid.add(wrapDescription(option.description()), 1, row);
            if (option.kind() == Kind.FLAG) {
                ComboBox<String> choice =
                        new ComboBox<>(FXCollections.observableArrayList(BOOL_FALSE, BOOL_TRUE));
                choice.setMaxWidth(Double.MAX_VALUE);
                applyFlagSelection(option, currentArgs, choice);
                flagChoices.put(option.flag(), choice);
                grid.add(choice, 2, row);
            } else {
                TextField valueField = new TextField();
                valueField.setPromptText(option.valueHint());
                applyValueSelection(option, currentArgs, valueField);
                valueFields.put(option.flag(), valueField);
                grid.add(valueField, 2, row);
            }
            row++;
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        VBox content = new VBox(8, chainCheck, scroll);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        while (true) {
            Optional<ButtonType> choice = dialog.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                return Optional.empty();
            }
            try {
                List<String> args = collectArgs(flagChoices, valueFields);
                List<String> validated = PingExpertValidator.validateAndNormalize(args);
                return Optional.of(new PingExpertEntry(chainCheck.isSelected(), validated));
            } catch (ConfigError ex) {
                Dialog<Void> error = new Dialog<>();
                error.setTitle("Помилка параметрів");
                error.setHeaderText(ex.getMessage());
                error.getDialogPane().getButtonTypes().add(ButtonType.OK);
                error.showAndWait();
            }
        }
    }

    private static Label wrapDescription(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static void applyFlagSelection(PingOption option, List<String> args, ComboBox<String> choice) {
        for (String arg : args) {
            if (option.flag().equals(arg)) {
                choice.getSelectionModel().select(BOOL_TRUE);
                return;
            }
        }
        choice.getSelectionModel().select(BOOL_FALSE);
    }

    private static void applyValueSelection(PingOption option, List<String> args, TextField valueField) {
        for (int i = 0; i < args.size(); i++) {
            if (!args.get(i).equals(option.flag())) {
                continue;
            }
            if (i + 1 < args.size()) {
                valueField.setText(args.get(i + 1));
            }
            return;
        }
    }

    private static List<String> collectArgs(
            Map<String, ComboBox<String>> flagChoices, Map<String, TextField> valueFields) {
        List<String> args = new ArrayList<>();
        for (PingOption option : PingOptionCatalog.options()) {
            if (option.kind() == Kind.FLAG) {
                ComboBox<String> choice = flagChoices.get(option.flag());
                if (choice != null && BOOL_TRUE.equals(choice.getValue())) {
                    args.add(option.flag());
                }
                continue;
            }
            TextField field = valueFields.get(option.flag());
            if (field == null) {
                continue;
            }
            String value = field.getText().strip();
            if (value.isEmpty()) {
                continue;
            }
            args.add(option.flag());
            args.add(value);
        }
        return args;
    }
}

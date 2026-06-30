package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.PingExpertEntry;
import io.pingui.probe.PingExpertValidator;
import io.pingui.probe.PingOptionCatalog;
import io.pingui.probe.PingOptionCatalog.Kind;
import io.pingui.probe.PingOptionCatalog.PingOption;
import io.pingui.probe.PingOptionCatalog.ValueKind;
import io.pingui.probe.PingOptionCatalog.ValueSpec;
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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Dialog for per-host expert ping flags (iputils ping). */
public final class PingExpertDialog {
    private static final String BOOL_FALSE = "false";
    private static final String BOOL_TRUE = "true";
    private static final String UNSET = "—";
    private static final String FIELD_OK = "";
    private static final String FIELD_ERROR = "-fx-border-color: #c0392b; -fx-border-width: 1.5px;";

    private PingExpertDialog() {}

    public static Optional<PingExpertEntry> show(String host, PingExpertEntry current) {
        return show(host, current, false);
    }

    /** @param pingOnly when true, hide chain checkbox (direct ping only, no hop chain). */
    public static Optional<PingExpertEntry> show(String host, PingExpertEntry current, boolean pingOnly) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Expert ping — " + host);
        dialog.setHeaderText(
                pingOnly
                        ? "Параметри ping(8) для прямого ping до цілі (режим Ping only)"
                        : "Параметри ping(8) для цілі (без -c/-w/-W/-i та інших лімітів часу/кількості)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(580);

        CheckBox chainCheck = new CheckBox("Застосувати до всього ланцюжка");
        if (pingOnly) {
            chainCheck.setVisible(false);
            chainCheck.setManaged(false);
            chainCheck.setSelected(false);
        } else {
            chainCheck.setSelected(current != null && current.applyToChain());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(8, 0, 0, 0));
        ColumnConstraints flagCol = new ColumnConstraints();
        flagCol.setMinWidth(48);
        ColumnConstraints descCol = new ColumnConstraints();
        descCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(flagCol, descCol, new ColumnConstraints(160));

        Map<String, ComboBox<String>> flagChoices = new HashMap<>();
        Map<String, ComboBox<String>> choiceValues = new HashMap<>();
        Map<String, TextField> textValues = new HashMap<>();
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
                ComboBox<String> choice = new ComboBox<>(FXCollections.observableArrayList(BOOL_FALSE, BOOL_TRUE));
                choice.setMaxWidth(Double.MAX_VALUE);
                applyFlagSelection(option, currentArgs, choice);
                flagChoices.put(option.flag(), choice);
                grid.add(choice, 2, row);
            } else {
                ValueSpec spec = option.valueSpec();
                if (spec.kind() == ValueKind.CHOICES) {
                    List<String> items = new ArrayList<>();
                    items.add(UNSET);
                    items.addAll(spec.choices());
                    ComboBox<String> choice = new ComboBox<>(FXCollections.observableArrayList(items));
                    choice.setMaxWidth(Double.MAX_VALUE);
                    choice.setTooltip(new Tooltip("Допустимо: " + String.join(", ", spec.choices())));
                    applyChoiceSelection(option, currentArgs, choice, spec.choices());
                    choiceValues.put(option.flag(), choice);
                    grid.add(choice, 2, row);
                } else {
                    TextField field = new TextField();
                    field.setPromptText(promptFor(option));
                    field.setTooltip(new Tooltip(PingExpertValidator.describeValueSpec(option)));
                    applyTextSelection(option, currentArgs, field);
                    bindTextValidation(option, field);
                    textValues.put(option.flag(), field);
                    grid.add(field, 2, row);
                }
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
                List<String> args = collectArgs(flagChoices, choiceValues, textValues);
                validateTextFields(textValues);
                List<String> validated = PingExpertValidator.validateAndNormalize(args);
                return Optional.of(new PingExpertEntry(!pingOnly && chainCheck.isSelected(), validated));
            } catch (ConfigError ex) {
                Dialog<Void> error = new Dialog<>();
                error.setTitle("Помилка параметрів");
                error.setHeaderText(ex.getMessage());
                error.getDialogPane().getButtonTypes().add(ButtonType.OK);
                error.showAndWait();
            }
        }
    }

    private static String promptFor(PingOption option) {
        String described = PingExpertValidator.describeValueSpec(option);
        if (!described.isBlank()) {
            return described;
        }
        ValueSpec spec = option.valueSpec();
        if (spec != null && spec.hint() != null && !spec.hint().isBlank()) {
            return spec.hint();
        }
        return "";
    }

    private static void bindTextValidation(PingOption option, TextField field) {
        Runnable validate = () -> {
            String text = field.getText().strip();
            if (text.isEmpty()) {
                clearFieldError(field);
                return;
            }
            try {
                PingExpertValidator.validateAndNormalizeValue(option, text);
                clearFieldError(field);
            } catch (ConfigError ex) {
                setFieldError(field, ex.getMessage());
            }
        };
        field.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused) {
                validate.run();
            }
        });
        field.textProperty().addListener((obs, oldText, newText) -> {
            if (field.isFocused()) {
                validate.run();
            }
        });
    }

    private static void validateTextFields(Map<String, TextField> textValues) {
        for (Map.Entry<String, TextField> entry : textValues.entrySet()) {
            TextField field = entry.getValue();
            String text = field.getText().strip();
            if (text.isEmpty()) {
                continue;
            }
            PingOption option = PingOptionCatalog.find(entry.getKey());
            if (option == null) {
                continue;
            }
            try {
                PingExpertValidator.validateAndNormalizeValue(option, text);
            } catch (ConfigError ex) {
                setFieldError(field, ex.getMessage());
                throw new ConfigError(option.flag() + ": " + ex.getMessage());
            }
        }
    }

    private static void setFieldError(TextField field, String message) {
        field.setStyle(FIELD_ERROR);
        field.setTooltip(new Tooltip(message));
    }

    private static void clearFieldError(TextField field) {
        field.setStyle(FIELD_OK);
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

    private static void applyChoiceSelection(
            PingOption option, List<String> args, ComboBox<String> choice, List<String> allowed) {
        for (int i = 0; i < args.size(); i++) {
            if (!args.get(i).equals(option.flag()) || i + 1 >= args.size()) {
                continue;
            }
            String value = args.get(i + 1);
            for (String item : allowed) {
                if (item.equalsIgnoreCase(value)) {
                    choice.getSelectionModel().select(item);
                    return;
                }
            }
            choice.getSelectionModel().select(UNSET);
            return;
        }
        choice.getSelectionModel().select(UNSET);
    }

    private static void applyTextSelection(PingOption option, List<String> args, TextField field) {
        for (int i = 0; i < args.size(); i++) {
            if (!args.get(i).equals(option.flag())) {
                continue;
            }
            if (i + 1 < args.size()) {
                field.setText(args.get(i + 1));
            }
            return;
        }
    }

    private static List<String> collectArgs(
            Map<String, ComboBox<String>> flagChoices,
            Map<String, ComboBox<String>> choiceValues,
            Map<String, TextField> textValues) {
        List<String> args = new ArrayList<>();
        for (PingOption option : PingOptionCatalog.options()) {
            if (option.kind() == Kind.FLAG) {
                ComboBox<String> choice = flagChoices.get(option.flag());
                if (choice != null && BOOL_TRUE.equals(choice.getValue())) {
                    args.add(option.flag());
                }
                continue;
            }
            ComboBox<String> choice = choiceValues.get(option.flag());
            if (choice != null) {
                String value = choice.getValue();
                if (value != null && !value.isBlank() && !UNSET.equals(value)) {
                    args.add(option.flag());
                    args.add(value);
                }
                continue;
            }
            TextField field = textValues.get(option.flag());
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

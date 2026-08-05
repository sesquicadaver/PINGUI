package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.config.PingExpertEntry;
import io.pingui.config.PingPreset;
import io.pingui.config.PingPresets;
import io.pingui.i18n.UiI18n;
import io.pingui.probe.PingExpertCompatibility;
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
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Dialog for per-host expert ping flags (iputils ping). */
public final class PingExpertDialog {
    private static String boolFalse() {
        return UiI18n.get("expert.bool_false");
    }

    private static String boolTrue() {
        return UiI18n.get("expert.bool_true");
    }

    private static String unset() {
        return UiI18n.get("expert.unset");
    }

    private static String afIpv4() {
        return ExpertPingUiRules.afIpv4();
    }

    private static String afIpv6() {
        return ExpertPingUiRules.afIpv6();
    }

    private static final String FIELD_OK = "";
    private static final String FIELD_ERROR = "-fx-border-color: #c0392b; -fx-border-width: 1.5px;";

    private PingExpertDialog() {}

    public static Optional<PingExpertEntry> show(String host, PingExpertEntry current) {
        return show(host, current, false);
    }

    /** @param pingOnly when true, hide chain checkbox (direct ping only, no hop chain). */
    public static Optional<PingExpertEntry> show(String host, PingExpertEntry current, boolean pingOnly) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(UiI18n.get("expert.title", host));
        dialog.setHeaderText(pingOnly ? UiI18n.get("expert.header_ping_only") : UiI18n.get("expert.header"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(580);

        CheckBox chainCheck = new CheckBox(UiI18n.get("expert.chain"));
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
        grid.add(new Label(UiI18n.get("expert.col_option")), 0, row);
        grid.add(new Label(UiI18n.get("expert.col_description")), 1, row);
        grid.add(new Label(UiI18n.get("expert.col_value")), 2, row);
        row++;

        ComboBox<String> addressFamily = new ComboBox<>(FXCollections.observableArrayList(afIpv4(), afIpv6()));
        addressFamily.setMaxWidth(Double.MAX_VALUE);
        addressFamily.setTooltip(new Tooltip(UiI18n.get("expert.af_tooltip")));
        applyAddressFamilySelection(currentArgs, addressFamily);
        grid.add(new Label(UiI18n.get("expert.af")), 0, row);
        grid.add(wrapDescription(UiI18n.get("expert.af_desc")), 1, row);
        grid.add(addressFamily, 2, row);
        row++;

        for (PingOption option : PingOptionCatalog.options()) {
            if ("-4".equals(option.flag()) || "-6".equals(option.flag())) {
                continue;
            }
            grid.add(new Label(option.flag()), 0, row);
            grid.add(wrapDescription(option.description()), 1, row);
            if (option.kind() == Kind.FLAG) {
                ComboBox<String> choice = new ComboBox<>(FXCollections.observableArrayList(boolFalse(), boolTrue()));
                choice.setMaxWidth(Double.MAX_VALUE);
                applyFlagSelection(option, currentArgs, choice);
                flagChoices.put(option.flag(), choice);
                grid.add(choice, 2, row);
            } else {
                ValueSpec spec = option.valueSpec();
                if (spec.kind() == ValueKind.CHOICES) {
                    List<String> items = new ArrayList<>();
                    items.add(unset());
                    items.addAll(spec.choices());
                    ComboBox<String> choice = new ComboBox<>(FXCollections.observableArrayList(items));
                    choice.setMaxWidth(Double.MAX_VALUE);
                    choice.setTooltip(
                            new Tooltip(UiI18n.get("expert.choices_allowed", String.join(", ", spec.choices()))));
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

        wireUiConstraints(host, addressFamily, flagChoices, textValues);

        Label presetStatus = new Label(UiI18n.get("expert.preset_hint"));
        presetStatus.setWrapText(true);
        presetStatus.setStyle("-fx-text-fill: #444;");
        HBox presetsBar = buildPresetsBar(addressFamily, flagChoices, choiceValues, textValues, presetStatus);
        Button mtuWizardButton = new Button(UiI18n.get("expert.mtu_wizard"));
        mtuWizardButton.setTooltip(new Tooltip(UiI18n.get("expert.mtu_wizard_tooltip")));
        Button selfCheckButton = new Button(UiI18n.get("expert.self_check"));
        selfCheckButton.setTooltip(new Tooltip(UiI18n.get("expert.self_check_tooltip")));
        ProgressBar selfCheckBar = new ProgressBar(0);
        selfCheckBar.setMaxWidth(Double.MAX_VALUE);
        selfCheckBar.setVisible(false);
        selfCheckBar.setManaged(false);
        HBox.setHgrow(selfCheckBar, Priority.ALWAYS);
        mtuWizardButton.setOnAction(event -> {
            boolean ipv6 = afIpv6().equals(addressFamily.getValue());
            Window owner = dialog.getDialogPane().getScene() != null
                    ? dialog.getDialogPane().getScene().getWindow()
                    : null;
            List<String> currentFormArgs = collectArgs(addressFamily, flagChoices, choiceValues, textValues);
            MtuDiscoveryDialog.show(owner, host, ipv6, currentFormArgs, result -> {
                applyArgsToForm(result.expertArgs(), addressFamily, flagChoices, choiceValues, textValues);
                String mtu = result.discovery().recommendedMtu().isPresent()
                        ? Integer.toString(result.discovery().recommendedMtu().getAsInt())
                        : "?";
                presetStatus.setText(UiI18n.get("expert.mtu_form_status", mtu, String.join(" ", result.expertArgs())));
            });
        });
        selfCheckButton.setOnAction(event -> {
            boolean ipv6 = afIpv6().equals(addressFamily.getValue());
            Window owner = dialog.getDialogPane().getScene() != null
                    ? dialog.getDialogPane().getScene().getWindow()
                    : null;
            PresetSelfCheckUi.runAsync(
                    owner,
                    host,
                    ipv6,
                    busy -> {
                        selfCheckButton.setDisable(busy);
                        mtuWizardButton.setDisable(busy);
                        selfCheckBar.setVisible(busy);
                        selfCheckBar.setManaged(busy);
                        if (busy) {
                            selfCheckBar.setProgress(0);
                            presetStatus.setText(UiI18n.get("expert.self_check_running"));
                        } else {
                            selfCheckBar.setProgress(1);
                            presetStatus.setText(UiI18n.get("expert.self_check_done"));
                        }
                    },
                    progress -> {
                        selfCheckBar.setProgress(progress.fraction());
                        presetStatus.setText(progress.statusLine());
                    });
        });
        HBox wizardBar = new HBox(6, mtuWizardButton, selfCheckButton);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        VBox content = new VBox(8, chainCheck, presetsBar, wizardBar, selfCheckBar, presetStatus, scroll);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        while (true) {
            Optional<ButtonType> choice = dialog.showAndWait();
            if (choice.isEmpty() || choice.get() != ButtonType.OK) {
                return Optional.empty();
            }
            try {
                List<String> args = collectArgs(addressFamily, flagChoices, choiceValues, textValues);
                validateTextFields(textValues);
                List<String> validated = PingExpertValidator.validateAndNormalize(args);
                return Optional.of(new PingExpertEntry(!pingOnly && chainCheck.isSelected(), validated));
            } catch (ConfigError ex) {
                Dialog<Void> error = new Dialog<>();
                error.setTitle(UiI18n.get("expert.params_error_title"));
                error.setHeaderText(ex.getMessage());
                error.getDialogPane().getButtonTypes().add(ButtonType.OK);
                error.showAndWait();
            }
        }
    }

    private static HBox buildPresetsBar(
            ComboBox<String> addressFamily,
            Map<String, ComboBox<String>> flagChoices,
            Map<String, ComboBox<String>> choiceValues,
            Map<String, TextField> textValues,
            Label presetStatus) {
        HBox bar = new HBox(6);
        Label title = new Label(UiI18n.get("expert.presets"));
        title.setMinWidth(56);
        bar.getChildren().add(title);
        for (PingPreset preset : PingPresets.all()) {
            Button button = new Button(preset.label());
            button.setTooltip(new Tooltip(preset.tooltipText()));
            button.setOnAction(event -> {
                List<String> current = collectArgs(addressFamily, flagChoices, choiceValues, textValues);
                List<String> merged = PingPresets.mergeKeepingAddressFamily(current, preset.args());
                applyArgsToForm(merged, addressFamily, flagChoices, choiceValues, textValues);
                presetStatus.setText(preset.statusLine());
            });
            bar.getChildren().add(button);
        }
        return bar;
    }

    /** Clears form controls and applies validated expert args (including AF). */
    static void applyArgsToForm(
            List<String> args,
            ComboBox<String> addressFamily,
            Map<String, ComboBox<String>> flagChoices,
            Map<String, ComboBox<String>> choiceValues,
            Map<String, TextField> textValues) {
        List<String> safeArgs = args != null ? args : List.of();
        applyAddressFamilySelection(safeArgs, addressFamily);
        for (Map.Entry<String, ComboBox<String>> entry : flagChoices.entrySet()) {
            applyFlagSelection(PingOptionCatalog.find(entry.getKey()), safeArgs, entry.getValue());
        }
        for (Map.Entry<String, ComboBox<String>> entry : choiceValues.entrySet()) {
            PingOption option = PingOptionCatalog.find(entry.getKey());
            if (option == null || option.valueSpec() == null) {
                continue;
            }
            applyChoiceSelection(
                    option, safeArgs, entry.getValue(), option.valueSpec().choices());
        }
        for (Map.Entry<String, TextField> entry : textValues.entrySet()) {
            entry.getValue().clear();
            clearFieldError(entry.getValue());
            PingOption option = PingOptionCatalog.find(entry.getKey());
            if (option != null) {
                applyTextSelection(option, safeArgs, entry.getValue());
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

    private static void applyAddressFamilySelection(List<String> args, ComboBox<String> choice) {
        if (args.contains("-6") && !args.contains("-4")) {
            choice.getSelectionModel().select(afIpv6());
        } else {
            choice.getSelectionModel().select(afIpv4());
        }
    }

    private static void wireUiConstraints(
            String host,
            ComboBox<String> addressFamily,
            Map<String, ComboBox<String>> flagChoices,
            Map<String, TextField> textValues) {
        for (List<String> group : PingExpertCompatibility.MUTUALLY_EXCLUSIVE) {
            if ("-4".equals(group.get(0)) || "-4".equals(group.get(1))) {
                continue;
            }
            wireMutualExclusiveFlags(flagChoices, group.get(0), group.get(1));
        }

        TextField flowLabel = textValues.get("-F");
        PingOption flowOption = PingOptionCatalog.find("-F");
        Runnable updateFlowLabel = () -> {
            if (flowLabel == null) {
                return;
            }
            boolean allowed = ExpertPingUiRules.flowLabelAllowed(host, addressFamily.getValue());
            flowLabel.setDisable(!allowed);
            if (!allowed) {
                flowLabel.clear();
                clearFieldError(flowLabel);
                flowLabel.setTooltip(new Tooltip(ExpertPingUiRules.flowLabelDisabledHint()));
            } else if (flowOption != null) {
                flowLabel.setTooltip(new Tooltip(PingExpertValidator.describeValueSpec(flowOption)));
            }
        };
        addressFamily.valueProperty().addListener((obs, oldValue, newValue) -> updateFlowLabel.run());
        updateFlowLabel.run();

        TextField iface = textValues.get("-I");
        ComboBox<String> bypassRoute = flagChoices.get("-r");
        Runnable updateBypassRoute = () -> {
            if (bypassRoute == null) {
                return;
            }
            boolean hasIface = iface != null && !iface.getText().strip().isEmpty();
            bypassRoute.setDisable(!hasIface);
            if (!hasIface && boolTrue().equals(bypassRoute.getValue())) {
                bypassRoute.getSelectionModel().select(boolFalse());
            }
        };
        if (iface != null) {
            iface.textProperty().addListener((obs, oldText, newText) -> updateBypassRoute.run());
        }
        updateBypassRoute.run();
    }

    private static void wireMutualExclusiveFlags(
            Map<String, ComboBox<String>> flagChoices, String flagA, String flagB) {
        ComboBox<String> boxA = flagChoices.get(flagA);
        ComboBox<String> boxB = flagChoices.get(flagB);
        if (boxA == null || boxB == null) {
            return;
        }
        boxA.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (boolTrue().equals(newValue)) {
                boxB.getSelectionModel().select(boolFalse());
            }
        });
        boxB.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (boolTrue().equals(newValue)) {
                boxA.getSelectionModel().select(boolFalse());
            }
        });
    }

    private static void applyFlagSelection(PingOption option, List<String> args, ComboBox<String> choice) {
        if (option == null) {
            choice.getSelectionModel().select(boolFalse());
            return;
        }
        for (String arg : args) {
            if (option.flag().equals(arg)) {
                choice.getSelectionModel().select(boolTrue());
                return;
            }
        }
        choice.getSelectionModel().select(boolFalse());
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
            choice.getSelectionModel().select(unset());
            return;
        }
        choice.getSelectionModel().select(unset());
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
            ComboBox<String> addressFamily,
            Map<String, ComboBox<String>> flagChoices,
            Map<String, ComboBox<String>> choiceValues,
            Map<String, TextField> textValues) {
        List<String> args = new ArrayList<>();
        String family = addressFamily.getValue();
        if (afIpv4().equals(family)) {
            args.add("-4");
        } else if (afIpv6().equals(family)) {
            args.add("-6");
        }
        for (PingOption option : PingOptionCatalog.options()) {
            if ("-4".equals(option.flag()) || "-6".equals(option.flag())) {
                continue;
            }
            if (option.kind() == Kind.FLAG) {
                ComboBox<String> choice = flagChoices.get(option.flag());
                if (choice != null && boolTrue().equals(choice.getValue())) {
                    args.add(option.flag());
                }
                continue;
            }
            ComboBox<String> choice = choiceValues.get(option.flag());
            if (choice != null) {
                String value = choice.getValue();
                if (value != null && !value.isBlank() && !unset().equals(value)) {
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

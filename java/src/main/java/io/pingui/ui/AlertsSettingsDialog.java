package io.pingui.ui;

import io.pingui.CliAlertOverrides;
import io.pingui.config.AlertConfig;
import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.config.LatencyHighRuleConfig;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * GUI for profile {@code alerts:} — channels + {@code endpoint_down} / {@code latency_high} /
 * {@code notify_resolved} (P20-011 / P21-003 / P23). Not a full NMS.
 */
public final class AlertsSettingsDialog {
    private static final String PRESET_CALM = "Спокійно";
    private static final String PRESET_BALANCED = "Збалансовано";
    private static final String PRESET_SENSITIVE = "Чутливо";
    private static final String PRESET_CUSTOM = "Власні значення";

    private AlertsSettingsDialog() {}

    /** Result from the alerts settings dialog. */
    public record Result(AlertConfig alerts) {}

    /** Dialog field snapshot used by {@link #buildConfig} (unit-tested). */
    public record FormInput(
            boolean desktop,
            String webhookText,
            String rateLimitText,
            boolean notifyResolved,
            boolean endpointDownEnabled,
            String failAfterText,
            String clearAfterText,
            String cooldownText,
            boolean latencyHighEnabled,
            String latencyMultiplierText,
            String latencyFailAfterText,
            String latencyClearAfterText,
            String latencyCooldownText) {}

    /**
     * Shows alert settings and invokes {@code onApply} on successful Apply.
     *
     * @param baseline current effective {@link AlertConfig} (YAML ± CLI already merged)
     * @param cliLocks CLI fields that must not be edited in the dialog
     */
    public static void show(Window owner, AlertConfig baseline, CliAlertOverrides cliLocks, Consumer<Result> onApply) {
        AlertConfig current = baseline != null ? baseline : AlertConfig.disabled();
        CliAlertOverrides locks = cliLocks != null ? cliLocks : CliAlertOverrides.none();
        Objects.requireNonNull(onApply, "onApply");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Сповіщення");
        dialog.setHeaderText("Канали + endpoint_down / latency_high (без NMS)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        CheckBox desktopCheck = new CheckBox("Desktop alerts (системне сповіщення)");
        desktopCheck.setSelected(current.desktopAlerts());
        if (locks.desktopAlerts().isPresent()) {
            desktopCheck.setDisable(true);
            desktopCheck.setTooltip(new Tooltip("Заблоковано CLI (--desktop-alerts)"));
        }

        TextField webhookField = new TextField(current.normalizedWebhook() != null ? current.normalizedWebhook() : "");
        webhookField.setPromptText("https://… або порожньо");
        if (locks.webhookUrl().isPresent()) {
            webhookField.setDisable(true);
            webhookField.setTooltip(new Tooltip("Заблоковано CLI (--alert-webhook)"));
        }

        TextField rateField = new TextField(Integer.toString(current.maxAlertsPerHour()));
        if (locks.rateLimitPerHour().isPresent()) {
            rateField.setDisable(true);
            rateField.setTooltip(new Tooltip("Заблоковано CLI (--alert-rate-limit)"));
        }

        CheckBox notifyResolvedCheck = new CheckBox("Сповіщати про RESOLVED (quality rules)");
        notifyResolvedCheck.setSelected(current.notifyResolved());
        notifyResolvedCheck.setTooltip(new Tooltip("alerts.notify_resolved — окремий emit після clear_after"));

        CheckBox endpointDownCheck = new CheckBox("Правило endpoint_down (недоступність цілі)");
        endpointDownCheck.setSelected(current.endpointDown().enabled());

        ComboBox<String> presetCombo = new ComboBox<>(
                FXCollections.observableArrayList(PRESET_CALM, PRESET_BALANCED, PRESET_SENSITIVE, PRESET_CUSTOM));
        TextField failField =
                new TextField(Integer.toString(current.endpointDown().failAfter()));
        TextField clearField =
                new TextField(Integer.toString(current.endpointDown().clearAfter()));
        TextField cooldownField =
                new TextField(Integer.toString(current.endpointDown().cooldownMinutes()));
        String matched = current.endpointDown().matchingPreset();
        presetCombo.setValue(
                switch (matched) {
                    case "calm" -> PRESET_CALM;
                    case "sensitive" -> PRESET_SENSITIVE;
                    case "balanced" -> PRESET_BALANCED;
                    default -> PRESET_CUSTOM;
                });
        Runnable applyPreset = () -> {
            String selected = presetCombo.getValue();
            if (PRESET_CUSTOM.equals(selected) || selected == null) {
                return;
            }
            EndpointDownRuleConfig preset = EndpointDownRuleConfig.fromPreset(presetKey(selected), true);
            failField.setText(Integer.toString(preset.failAfter()));
            clearField.setText(Integer.toString(preset.clearAfter()));
            cooldownField.setText(Integer.toString(preset.cooldownMinutes()));
        };
        presetCombo.valueProperty().addListener((obs, oldV, newV) -> applyPreset.run());

        CheckBox latencyHighCheck = new CheckBox("Правило latency_high (rtt ≥ multiplier × AVG)");
        latencyHighCheck.setSelected(current.latencyHigh().enabled());
        latencyHighCheck.setTooltip(new Tooltip("Default: 2×AVG, fail_after=3 поспіль (без вікна часу)"));
        TextField latencyMultiplierField =
                new TextField(Double.toString(current.latencyHigh().multiplier()));
        TextField latencyFailField =
                new TextField(Integer.toString(current.latencyHigh().failAfter()));
        TextField latencyClearField =
                new TextField(Integer.toString(current.latencyHigh().clearAfter()));
        TextField latencyCooldownField =
                new TextField(Integer.toString(current.latencyHigh().cooldownMinutes()));

        TextArea statusArea = new TextArea(current.toRedactedString());
        statusArea.setEditable(false);
        statusArea.setWrapText(true);
        statusArea.setPrefRowCount(3);
        statusArea.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Застосувати оновлює профіль, dispatcher і quality engines. "
                + "«Зберегти» → YAML. Default: правила вимкнено.");
        hint.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        applyLabelFieldColumns(grid);
        int row = 0;
        grid.add(desktopCheck, 0, row++, 2, 1);
        grid.add(formLabel("webhook URL:"), 0, row);
        grid.add(webhookField, 1, row++);
        grid.add(formLabel("rate_limit / год:"), 0, row);
        grid.add(rateField, 1, row++);
        grid.add(notifyResolvedCheck, 0, row++, 2, 1);
        grid.add(endpointDownCheck, 0, row++, 2, 1);
        grid.add(formLabel("пресет down:"), 0, row);
        grid.add(presetCombo, 1, row++);
        grid.add(formLabel("fail_after:"), 0, row);
        grid.add(failField, 1, row++);
        grid.add(formLabel("clear_after:"), 0, row);
        grid.add(clearField, 1, row++);
        grid.add(formLabel("cooldown (хв):"), 0, row);
        grid.add(cooldownField, 1, row++);
        grid.add(latencyHighCheck, 0, row++, 2, 1);
        grid.add(formLabel("multiplier:"), 0, row);
        grid.add(latencyMultiplierField, 1, row++);
        grid.add(formLabel("lat fail_after:"), 0, row);
        grid.add(latencyFailField, 1, row++);
        grid.add(formLabel("lat clear_after:"), 0, row);
        grid.add(latencyClearField, 1, row++);
        grid.add(formLabel("lat cooldown:"), 0, row);
        grid.add(latencyCooldownField, 1, row++);
        grid.add(formLabel("Статус:"), 0, row);
        grid.add(statusArea, 1, row);
        GridPane.setHgrow(webhookField, Priority.ALWAYS);
        GridPane.setHgrow(statusArea, Priority.ALWAYS);

        VBox content = new VBox(10, grid, hint);
        content.setPrefWidth(580);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.APPLY) {
            return;
        }
        try {
            FormInput form = new FormInput(
                    desktopCheck.isSelected(),
                    webhookField.getText(),
                    rateField.getText(),
                    notifyResolvedCheck.isSelected(),
                    endpointDownCheck.isSelected(),
                    failField.getText(),
                    clearField.getText(),
                    cooldownField.getText(),
                    latencyHighCheck.isSelected(),
                    latencyMultiplierField.getText(),
                    latencyFailField.getText(),
                    latencyClearField.getText(),
                    latencyCooldownField.getText());
            AlertConfig next = buildConfig(current, form, locks);
            onApply.accept(new Result(next));
        } catch (IllegalArgumentException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setTitle("Сповіщення");
            alert.setHeaderText("Некоректні значення");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Builds validated {@link AlertConfig} from form text, applying CLI locks over form values.
     *
     * @throws IllegalArgumentException on invalid numbers
     */
    public static AlertConfig buildConfig(AlertConfig baseline, FormInput form, CliAlertOverrides locks) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(form, "form");
        CliAlertOverrides effective = locks != null ? locks : CliAlertOverrides.none();

        boolean desktop = effective.desktopAlerts().isPresent()
                ? effective.desktopAlerts().get()
                : form.desktop();
        String webhook =
                effective.webhookUrl().isPresent() ? effective.webhookUrl().get() : blankToNull(form.webhookText());
        int rate = effective.rateLimitPerHour().isPresent()
                ? effective.rateLimitPerHour().getAsInt()
                : parsePositiveInt(form.rateLimitText(), "rate_limit");
        EndpointDownRuleConfig endpointDown = new EndpointDownRuleConfig(
                form.endpointDownEnabled(),
                parsePositiveInt(form.failAfterText(), "fail_after"),
                parsePositiveInt(form.clearAfterText(), "clear_after"),
                parseNonNegativeInt(form.cooldownText(), "cooldown_minutes"));
        LatencyHighRuleConfig latencyHigh = new LatencyHighRuleConfig(
                form.latencyHighEnabled(),
                parsePositiveDouble(form.latencyMultiplierText(), "multiplier"),
                parsePositiveInt(form.latencyFailAfterText(), "lat fail_after"),
                parsePositiveInt(form.latencyClearAfterText(), "lat clear_after"),
                parseNonNegativeInt(form.latencyCooldownText(), "lat cooldown"),
                baseline.latencyHigh().thresholdMs());
        return new AlertConfig(desktop, webhook, rate, form.notifyResolved(), endpointDown, latencyHigh);
    }

    static String presetKey(String uiLabel) {
        if (PRESET_CALM.equals(uiLabel)) {
            return "calm";
        }
        if (PRESET_SENSITIVE.equals(uiLabel)) {
            return "sensitive";
        }
        return "balanced";
    }

    /** Label that refuses to shrink below preferred width (avoids clipped left-column text). */
    static Label formLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    /** Column 0 hugs labels; column 1 grows with fields. */
    static void applyLabelFieldColumns(GridPane grid) {
        Objects.requireNonNull(grid, "grid");
        ColumnConstraints labels = new ColumnConstraints();
        labels.setHgrow(Priority.NEVER);
        labels.setMinWidth(Region.USE_PREF_SIZE);
        labels.setHalignment(HPos.LEFT);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        grid.getColumnConstraints().setAll(labels, fields);
    }

    private static String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }

    private static int parsePositiveInt(String text, String field) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(field + " must be an integer >= 1");
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) {
                throw new IllegalArgumentException(field + " must be >= 1");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be an integer >= 1");
        }
    }

    private static int parseNonNegativeInt(String text, String field) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(field + " must be an integer >= 0");
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                throw new IllegalArgumentException(field + " must be >= 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be an integer >= 0");
        }
    }

    private static double parsePositiveDouble(String text, String field) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(field + " must be a number > 0");
        }
        try {
            double value = Double.parseDouble(raw);
            if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException(field + " must be > 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a number > 0");
        }
    }
}

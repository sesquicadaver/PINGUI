package io.pingui.ui;

import io.pingui.CliAlertOverrides;
import io.pingui.config.AlertConfig;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * GUI for profile {@code alerts:} — desktop / webhook / rate_limit (P20-011). Apply updates
 * in-memory profile + dispatcher; YAML persist via main «Зберегти». Not a full NMS.
 */
public final class AlertsSettingsDialog {
    private AlertsSettingsDialog() {}

    /** Result from the alerts settings dialog. */
    public record Result(AlertConfig alerts) {}

    /** Dialog field snapshot used by {@link #buildConfig} (unit-tested). */
    public record FormInput(boolean desktop, String webhookText, String rateLimitText) {}

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
        dialog.setHeaderText("alerts.desktop / webhook / rate_limit (без NMS)");
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

        TextArea statusArea = new TextArea(current.toRedactedString());
        statusArea.setEditable(false);
        statusArea.setWrapText(true);
        statusArea.setPrefRowCount(2);
        statusArea.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Застосувати оновлює активний профіль і dispatcher. "
                + "«Зберегти» у головному вікні записує YAML. Статус — URL без секретів.");
        hint.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        grid.add(desktopCheck, 0, row++, 2, 1);
        grid.add(new Label("webhook URL:"), 0, row);
        grid.add(webhookField, 1, row++);
        grid.add(new Label("rate_limit / год:"), 0, row);
        grid.add(rateField, 1, row++);
        grid.add(new Label("Статус:"), 0, row);
        grid.add(statusArea, 1, row++);
        GridPane.setHgrow(webhookField, Priority.ALWAYS);
        GridPane.setHgrow(statusArea, Priority.ALWAYS);

        VBox content = new VBox(10, grid, hint);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.APPLY) {
            return;
        }
        try {
            FormInput form = new FormInput(desktopCheck.isSelected(), webhookField.getText(), rateField.getText());
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
     * @throws IllegalArgumentException on invalid rate_limit
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
                : parseRateLimit(form.rateLimitText());
        return new AlertConfig(desktop, webhook, rate);
    }

    private static String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }

    private static int parseRateLimit(String text) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("rate_limit must be an integer >= 1");
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) {
                throw new IllegalArgumentException("rate_limit must be >= 1");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("rate_limit must be an integer >= 1");
        }
    }
}

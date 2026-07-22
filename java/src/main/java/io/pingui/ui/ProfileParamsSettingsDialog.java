package io.pingui.ui;

import io.pingui.CliProfileOverrides;
import io.pingui.config.TracingProfile;
import io.pingui.probe.ProbeMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * GUI for active-profile poll settings: {@code interval}, {@code max_hops}, {@code timeout},
 * {@code probe} (P20-010). Apply updates in-memory profile; YAML persist via main «Зберегти».
 */
public final class ProfileParamsSettingsDialog {
    private ProfileParamsSettingsDialog() {}

    /** Result from the profile params dialog. */
    public record Result(double intervalSeconds, int maxHops, double timeoutSeconds, ProbeMode probeMode) {}

    /** Dialog field snapshot used by {@link #buildResult} (unit-tested). */
    public record FormInput(String intervalText, String maxHopsText, String timeoutText, String probeCli) {}

    /**
     * Shows profile poll settings and invokes {@code onApply} on successful Apply.
     *
     * @param baseline current effective profile (YAML ± CLI already merged)
     * @param cliLocks CLI fields that must not be edited in the dialog
     */
    public static void show(
            Window owner, TracingProfile baseline, CliProfileOverrides cliLocks, Consumer<Result> onApply) {
        Objects.requireNonNull(baseline, "baseline");
        CliProfileOverrides locks = cliLocks != null ? cliLocks : CliProfileOverrides.none();
        Objects.requireNonNull(onApply, "onApply");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Параметри профілю");
        dialog.setHeaderText("interval / max_hops / timeout / probe активного профілю");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        TextField intervalField = new TextField(formatDouble(baseline.intervalSeconds()));
        TextField maxHopsField = new TextField(Integer.toString(baseline.maxHops()));
        TextField timeoutField = new TextField(formatDouble(baseline.timeoutSeconds()));
        ComboBox<String> probeCombo = new ComboBox<>(FXCollections.observableArrayList("auto", "process", "raw"));
        probeCombo.setValue(baseline.probeMode().cliValue());

        lockField(intervalField, locks.intervalSeconds().isPresent(), "--interval");
        lockField(maxHopsField, locks.maxHops().isPresent(), "--max-hops");
        lockField(timeoutField, locks.timeoutSeconds().isPresent(), "--timeout");
        if (locks.probeMode().isPresent()) {
            probeCombo.setDisable(true);
            probeCombo.setTooltip(new Tooltip("Заблоковано CLI (--probe)"));
        }

        Label hint = new Label("Застосувати оновлює активний профіль і перезапускає монітор. "
                + "«Зберегти» у головному вікні записує YAML.");
        hint.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        grid.add(new Label("interval (с):"), 0, row);
        grid.add(intervalField, 1, row++);
        grid.add(new Label("max_hops:"), 0, row);
        grid.add(maxHopsField, 1, row++);
        grid.add(new Label("timeout (с):"), 0, row);
        grid.add(timeoutField, 1, row++);
        grid.add(new Label("probe:"), 0, row);
        grid.add(probeCombo, 1, row++);

        VBox content = new VBox(10, grid, hint);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(420);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.APPLY) {
            return;
        }
        try {
            FormInput form = new FormInput(
                    intervalField.getText(), maxHopsField.getText(), timeoutField.getText(), probeCombo.getValue());
            Result result = buildResult(baseline, form, locks);
            onApply.accept(result);
        } catch (IllegalArgumentException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setTitle("Параметри профілю");
            alert.setHeaderText("Некоректні значення");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Builds validated poll settings from form text, applying CLI locks over form values.
     *
     * @throws IllegalArgumentException on invalid numbers / probe
     */
    public static Result buildResult(TracingProfile baseline, FormInput form, CliProfileOverrides locks) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(form, "form");
        CliProfileOverrides effective = locks != null ? locks : CliProfileOverrides.none();

        double interval = effective.intervalSeconds().isPresent()
                ? effective.intervalSeconds().getAsDouble()
                : parsePositiveDouble(form.intervalText(), "interval");
        int maxHops = effective.maxHops().isPresent()
                ? effective.maxHops().getAsInt()
                : parsePositiveInt(form.maxHopsText(), "max_hops");
        double timeout = effective.timeoutSeconds().isPresent()
                ? effective.timeoutSeconds().getAsDouble()
                : parsePositiveDouble(form.timeoutText(), "timeout");
        ProbeMode probe =
                effective.probeMode().isPresent() ? effective.probeMode().get() : ProbeMode.parse(form.probeCli());

        // Force validation via TracingProfile compact ctor rules.
        TracingProfile validated = baseline.withPollSettings(interval, maxHops, timeout, probe);
        return new Result(
                validated.intervalSeconds(), validated.maxHops(), validated.timeoutSeconds(), validated.probeMode());
    }

    private static void lockField(TextField field, boolean locked, String cliFlag) {
        if (!locked) {
            return;
        }
        field.setDisable(true);
        field.setTooltip(new Tooltip("Заблоковано CLI (" + cliFlag + ")"));
    }

    private static String formatDouble(double value) {
        if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }

    private static double parsePositiveDouble(String text, String field) {
        String raw = text == null ? "" : text.strip();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(field + " must be a positive number");
        }
        try {
            double value = Double.parseDouble(raw);
            if (!(value > 0.0) || !Double.isFinite(value)) {
                throw new IllegalArgumentException(field + " must be positive");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a number");
        }
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
}

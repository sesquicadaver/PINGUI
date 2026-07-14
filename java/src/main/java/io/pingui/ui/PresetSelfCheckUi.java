package io.pingui.ui;

import io.pingui.probe.PresetSelfCheck;
import io.pingui.probe.PresetSelfCheckConfig;
import io.pingui.probe.PresetSelfCheckResult;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Window;

/**
 * Runs {@link PresetSelfCheck} off the FX thread and shows a short informational Alert (P17-030).
 * Not a wizard — no progress dialog / Apply.
 */
public final class PresetSelfCheckUi {
    private PresetSelfCheckUi() {}

    /**
     * Starts the self-check for {@code host}.
     *
     * @param setBusy optional FX-thread callback {@code true} while running / {@code false} when done
     */
    public static void runAsync(Window owner, String host, boolean ipv6, Consumer<Boolean> setBusy) {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must be non-blank");
        }
        Consumer<Boolean> busy = setBusy != null ? setBusy : b -> {};
        busy.accept(true);
        PresetSelfCheckConfig config =
                ipv6 ? PresetSelfCheckConfig.ipv6Defaults() : PresetSelfCheckConfig.ipv4Defaults();

        Thread.ofVirtual().name("preset-self-check").start(() -> {
            try {
                PresetSelfCheckResult result = new PresetSelfCheck().run(host, config);
                Platform.runLater(() -> {
                    busy.accept(false);
                    showResultAlert(owner, host, result);
                });
            } catch (IOException | RuntimeException ex) {
                Platform.runLater(() -> {
                    busy.accept(false);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    if (owner != null) {
                        alert.initOwner(owner);
                    }
                    alert.setTitle("Self-check");
                    alert.setHeaderText("Self-check не вдався");
                    alert.setContentText(ex.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    static void showResultAlert(Window owner, String host, PresetSelfCheckResult result) {
        Alert.AlertType type = result.anyWarn() ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION;
        Alert alert = new Alert(type);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("Self-check — " + host);
        alert.setHeaderText(result.anyWarn() ? "Є попередження (loss ≥ порогу)" : "OK — DF / DSCP / Burst");
        alert.setContentText(formatAlertBody(result));
        alert.showAndWait();
    }

    /** Human-readable Alert body (unit-tested). */
    public static String formatAlertBody(PresetSelfCheckResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Короткий batch (інформаційно; не змінює форму Expert).\n\n");
        for (PresetSelfCheckResult.PresetCheck check : result.checks()) {
            sb.append(check.warn() ? "⚠ " : "✓ ");
            sb.append(check.label())
                    .append(" (")
                    .append(check.presetId())
                    .append("): sent=")
                    .append(check.sent())
                    .append(" lost=")
                    .append(check.lost())
                    .append(String.format(Locale.ROOT, " loss=%.1f%%", check.lossPct()));
            if (check.avgRttMs().isPresent()) {
                sb.append(String.format(
                        Locale.ROOT, " avgRTT=%.2f ms", check.avgRttMs().getAsDouble()));
            } else {
                sb.append(" avgRTT=—");
            }
            sb.append('\n');
            sb.append("  args: ").append(String.join(" ", check.extendedArgs())).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}

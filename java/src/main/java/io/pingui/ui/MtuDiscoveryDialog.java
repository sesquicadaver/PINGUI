package io.pingui.ui;

import io.pingui.config.ConfigError;
import io.pingui.probe.MtuDiscovery;
import io.pingui.probe.MtuDiscoveryConfig;
import io.pingui.probe.MtuDiscoveryResult;
import io.pingui.probe.PingExpertValidator;
import io.pingui.probe.ProcessMtuProbeRunner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Wizard UI for {@link MtuDiscovery} (P17-021): progress, Stop, Alert with recommended MTU, Apply →
 * Expert args ({@code -M do -s <payload>}). Distinct from the Expert preset «MTU probe».
 */
public final class MtuDiscoveryDialog {
    private MtuDiscoveryDialog() {}

    /** Outcome forwarded to the caller on Apply (normalized Expert args). */
    public record Result(MtuDiscoveryResult discovery, List<String> expertArgs) {
        public Result {
            Objects.requireNonNull(discovery, "discovery");
            expertArgs = List.copyOf(expertArgs != null ? expertArgs : List.of());
        }
    }

    /**
     * Shows the MTU sweep wizard for {@code host}.
     *
     * @param ipv6 when true use IPv6 defaults ({@code -6}); else IPv4
     * @param currentExpertArgs existing Expert args to merge {@code -M}/{@code -s} into on Apply
     * @param onApply invoked on FX thread when the user applies a successful recommendation
     */
    public static void show(
            Window owner, String host, boolean ipv6, List<String> currentExpertArgs, Consumer<Result> onApply) {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must be non-blank");
        }
        Consumer<Result> applySink = onApply != null ? onApply : r -> {};
        List<String> baselineArgs = currentExpertArgs != null ? currentExpertArgs : List.of();

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("MTU discovery — " + host);
        dialog.setHeaderText(
                "Перебір payload (-s) з Don't Fragment (-M do). «MTU probe» пресет — інше (фіксований -s).");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label progressLabel = new Label("Натисніть Старт, щоб почати sweep.");
        progressLabel.setWrapText(true);
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);

        Button startButton = new Button("Старт");
        Button stopButton = new Button("Stop");
        stopButton.setDisable(true);
        Button applyButton = new Button("Apply → Expert");
        applyButton.setDisable(true);
        applyButton.setDefaultButton(true);

        AtomicBoolean cancel = new AtomicBoolean(false);
        AtomicBoolean running = new AtomicBoolean(false);
        AtomicBoolean open = new AtomicBoolean(true);
        final MtuDiscoveryResult[] lastResult = new MtuDiscoveryResult[1];

        MtuDiscoveryConfig config = ipv6 ? MtuDiscoveryConfig.ipv6Defaults() : MtuDiscoveryConfig.ipv4Defaults();
        int totalSizes = ((config.startPayload() - config.minPayload()) / config.step()) + 1;

        Runnable resetBusy = () -> {
            running.set(false);
            startButton.setDisable(false);
            stopButton.setDisable(true);
        };

        startButton.setOnAction(e -> {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            cancel.set(false);
            lastResult[0] = null;
            applyButton.setDisable(true);
            startButton.setDisable(true);
            stopButton.setDisable(false);
            bar.setProgress(0);
            progressLabel.setText(
                    "Старт… AF=" + (ipv6 ? "IPv6" : "IPv4") + ", поріг loss " + config.lossThresholdPct() + "%");

            Thread.ofVirtual().name("mtu-discovery").start(() -> {
                try {
                    MtuDiscoveryResult result = new MtuDiscovery(new ProcessMtuProbeRunner())
                            .discover(
                                    host,
                                    config,
                                    cancel::get,
                                    step -> Platform.runLater(() -> {
                                        if (!open.get()) {
                                            return;
                                        }
                                        int done = ((step.payloadBytes() - config.minPayload()) / config.step()) + 1;
                                        bar.setProgress(Math.min(1.0, (double) done / Math.max(1, totalSizes)));
                                        progressLabel.setText(formatStep(step));
                                    }));
                    Platform.runLater(() -> {
                        if (!open.get()) {
                            return;
                        }
                        lastResult[0] = result;
                        resetBusy.run();
                        onDiscoveryFinished(dialog, progressLabel, bar, applyButton, result, config);
                    });
                } catch (IOException | RuntimeException ex) {
                    Platform.runLater(() -> {
                        if (!open.get()) {
                            return;
                        }
                        resetBusy.run();
                        progressLabel.setText("Помилка: " + ex.getMessage());
                        bar.setProgress(0);
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.initOwner(ownerWindow(dialog, owner));
                        alert.setTitle("MTU discovery");
                        alert.setHeaderText("Sweep не вдався");
                        alert.setContentText(ex.getMessage());
                        alert.showAndWait();
                    });
                }
            });
        });

        stopButton.setOnAction(e -> {
            if (!running.get()) {
                return;
            }
            cancel.set(true);
            stopButton.setDisable(true);
            progressLabel.setText(formatStopping(progressLabel.getText()));
        });

        applyButton.setOnAction(e -> {
            MtuDiscoveryResult result = lastResult[0];
            if (result == null || result.maxGoodPayload().isEmpty()) {
                return;
            }
            try {
                // Apply path uses maxGoodPayload for -s (not recommendedMtu = payload + overhead).
                int payload = result.maxGoodPayload().getAsInt();
                List<String> args = mergeMtuDiscoveryArgs(baselineArgs, ipv6, payload);
                applySink.accept(new Result(result, args));
                open.set(false);
                cancel.set(true);
                dialog.setResult(ButtonType.CLOSE);
                dialog.close();
            } catch (ConfigError ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(ownerWindow(dialog, owner));
                alert.setTitle("MTU discovery");
                alert.setHeaderText("Не вдалося зібрати Expert args");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });

        dialog.setOnCloseRequest(ev -> {
            open.set(false);
            cancel.set(true);
        });
        dialog.setOnHidden(ev -> {
            open.set(false);
            cancel.set(true);
        });

        HBox buttons = new HBox(8, startButton, stopButton, applyButton);
        VBox content = new VBox(10, progressLabel, bar, buttons);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(480);
        dialog.showAndWait();
        open.set(false);
        cancel.set(true);
    }

    /** Builds standalone Expert args for Don't-Fragment probes at the given payload. */
    public static List<String> toExpertArgs(boolean ipv6, int payloadBytes) {
        return mergeMtuDiscoveryArgs(List.of(), ipv6, payloadBytes);
    }

    /**
     * Merges discovery {@code -M do -s <payload>} into existing Expert args (keeps other flags; replaces
     * prior {@code -M}/{@code -s}).
     */
    public static List<String> mergeMtuDiscoveryArgs(List<String> currentArgs, boolean ipv6, int payloadBytes) {
        if (payloadBytes < 0) {
            throw new IllegalArgumentException("payloadBytes must be >= 0");
        }
        List<String> merged = new ArrayList<>();
        merged.add(ipv6 ? "-6" : "-4");
        if (currentArgs != null) {
            for (int i = 0; i < currentArgs.size(); i++) {
                String arg = currentArgs.get(i);
                if ("-4".equals(arg) || "-6".equals(arg)) {
                    continue;
                }
                if ("-M".equals(arg) || "-s".equals(arg)) {
                    i++;
                    continue;
                }
                merged.add(arg);
            }
        }
        merged.add("-M");
        merged.add("do");
        merged.add("-s");
        merged.add(Integer.toString(payloadBytes));
        return PingExpertValidator.validateAndNormalize(merged);
    }

    /** True when Expert AF args request IPv6. */
    public static boolean ipv6FromExpertArgs(List<String> args) {
        if (args == null) {
            return false;
        }
        return args.contains("-6") && !args.contains("-4");
    }

    static String formatStep(MtuDiscoveryResult.MtuProbeStep step) {
        return String.format(
                Locale.ROOT,
                "Розмір -s=%d · sent=%d lost=%d · loss=%.1f%%%s",
                step.payloadBytes(),
                step.sent(),
                step.lost(),
                step.lossPct(),
                step.stoppedHere() ? " · STOP (поріг)" : "");
    }

    static String formatStopping(String current) {
        if (current == null || current.isBlank()) {
            return "Зупинка…";
        }
        if (current.contains("зупинка")) {
            return current;
        }
        return current + " (зупинка…)";
    }

    static String formatSummary(MtuDiscoveryResult result, MtuDiscoveryConfig config) {
        if (result.cancelled() && result.maxGoodPayload().isEmpty()) {
            return "Скасовано до першого успішного розміру.";
        }
        if (result.maxGoodPayload().isEmpty()) {
            return "Немає успішного розміру (вже min payload дав loss ≥ " + config.lossThresholdPct() + "%).";
        }
        int payload = result.maxGoodPayload().getAsInt();
        int mtu = result.recommendedMtu().orElse(config.mtuForPayload(payload));
        String stop = result.stoppedOnLoss() ? "stop на loss≥порогу" : "усі розміри до start пройшли";
        String cancelNote = result.cancelled() ? " (перервано)" : "";
        return "Рекомендований MTU ≈ "
                + mtu
                + " (payload -s="
                + payload
                + " + overhead "
                + config.icmpOverhead()
                + "). "
                + stop
                + cancelNote
                + ".";
    }

    private static Window ownerWindow(Dialog<?> dialog, Window fallback) {
        if (dialog.getDialogPane().getScene() != null) {
            return dialog.getDialogPane().getScene().getWindow();
        }
        return fallback;
    }

    private static void onDiscoveryFinished(
            Dialog<ButtonType> dialog,
            Label progressLabel,
            ProgressBar bar,
            Button applyButton,
            MtuDiscoveryResult result,
            MtuDiscoveryConfig config) {
        String summary = formatSummary(result, config);
        progressLabel.setText(summary);
        bar.setProgress(result.cancelled() && result.steps().isEmpty() ? 0 : 1.0);
        boolean canApply = result.maxGoodPayload().isPresent();
        applyButton.setDisable(!canApply);

        Alert.AlertType type = canApply ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING;
        Alert alert = new Alert(type);
        alert.initOwner(ownerWindow(dialog, null));
        alert.setTitle("MTU discovery");
        alert.setHeaderText(canApply ? "Оцінка max MTU" : "MTU не визначено");
        alert.setContentText(summary);
        alert.showAndWait();
    }
}

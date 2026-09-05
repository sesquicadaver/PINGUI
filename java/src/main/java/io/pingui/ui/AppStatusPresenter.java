package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.MonitorService;
import io.pingui.ui.view.StatusPanel;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Centralized application status: durable monitoring summary + transient ops + optional long-op
 * progress/cancel (P31-006).
 */
final class AppStatusPresenter {
    private static final Duration OPS_CLEAR_AFTER = Duration.seconds(8);
    private static final Duration CLOCK_PERIOD = Duration.seconds(1);

    private final StatusPanel panel;
    private final Supplier<MonitorService> monitor;
    private Instant lastCycleAt;
    private Timeline clock;
    private Timeline opsClear;
    private Runnable cancelAction;
    private boolean bootstrapMode = true;

    AppStatusPresenter(StatusPanel panel, Supplier<MonitorService> monitor) {
        this.panel = Objects.requireNonNull(panel, "panel");
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        panel.cancelButton().setOnAction(e -> {
            Runnable action = cancelAction;
            if (action != null) {
                action.run();
            }
        });
        panel.hideProgress();
        panel.clearOps();
    }

    /** Starts the 1 Hz refresh of relative «Last cycle» age and enabled counts. */
    void start() {
        if (clock != null) {
            return;
        }
        clock = new Timeline(new KeyFrame(CLOCK_PERIOD, e -> refreshMonitoring()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    void stop() {
        if (clock != null) {
            clock.stop();
            clock = null;
        }
        cancelOpsClear();
        endProgress();
    }

    /** Loading / bootstrap text on the monitoring line (before first refresh). */
    void setBootstrap(String message) {
        bootstrapMode = true;
        panel.monitoringLabel().setText(message != null ? message : "");
        panel.clearOps();
        endProgress();
    }

    /** Leaves bootstrap mode and paints live monitoring summary. */
    void onServicesReady() {
        bootstrapMode = false;
        refreshMonitoring();
        panel.clearOps();
    }

    void showTransient(String message) {
        cancelOpsClear();
        panel.opsLabel().getStyleClass().remove("pingui-danger");
        panel.setOps(message);
        opsClear = new Timeline(new KeyFrame(OPS_CLEAR_AFTER, e -> panel.clearOps()));
        opsClear.setCycleCount(1);
        opsClear.play();
    }

    void showError(String message) {
        cancelOpsClear();
        if (!panel.opsLabel().getStyleClass().contains("pingui-danger")) {
            panel.opsLabel().getStyleClass().add("pingui-danger");
        }
        panel.setOps(message);
    }

    /** Records a completed poll cycle and refreshes the summary. */
    void notePollCycle(Instant at) {
        if (at != null) {
            lastCycleAt = at;
        }
        if (!bootstrapMode) {
            refreshMonitoring();
        }
    }

    void refreshMonitoring() {
        if (bootstrapMode) {
            return;
        }
        MonitorService service = monitor.get();
        boolean active = service != null && service.isRunning();
        int enabled = service != null ? service.enabledHosts().size() : 0;
        int total = service != null ? service.hosts().size() : 0;
        if (service != null) {
            service.latestPollAt().ifPresent(t -> lastCycleAt = t);
        }
        panel.monitoringLabel().setText(AppStatusFormat.monitoring(active, enabled, total, lastCycleAt, Instant.now()));
    }

    /**
     * Shows indeterminate progress + cancel for a long background op.
     *
     * @param label short ops-line text
     * @param onCancel invoked on Cancel (may be {@code null})
     */
    void beginProgress(String label, Runnable onCancel) {
        cancelOpsClear();
        cancelAction = onCancel;
        panel.opsLabel().getStyleClass().remove("pingui-danger");
        panel.setOps(label != null ? label : "");
        panel.showProgress(onCancel != null);
        panel.cancelButton().setText(UiI18n.get("status.op.cancel"));
    }

    void endProgress() {
        cancelAction = null;
        panel.hideProgress();
    }

    String monitoringText() {
        return panel.monitoringLabel().getText();
    }

    String opsText() {
        return panel.opsLabel().getText();
    }

    private void cancelOpsClear() {
        if (opsClear != null) {
            opsClear.stop();
            opsClear = null;
        }
    }
}

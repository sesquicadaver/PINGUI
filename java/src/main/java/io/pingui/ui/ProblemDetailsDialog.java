package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.ProblemCorrelation;
import io.pingui.monitor.ProblemCorrelationScope;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Detail view for an unread session problem (P22-004 / ADR_HOST_PROBLEM_INDICATOR). Closing acks
 * in the caller. Optional multi-host correlation summary (P29-001).
 */
public final class ProblemDetailsDialog {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private ProblemDetailsDialog() {}

    /** Shows problem details; returns after the user closes the dialog. */
    public static void show(Window owner, HostProblemSummary summary) {
        show(owner, summary, Optional.empty());
    }

    /** Shows problem details plus optional correlation narrative (P29-001). */
    public static void show(Window owner, HostProblemSummary summary, Optional<ProblemCorrelation> correlation) {
        Objects.requireNonNull(summary, "summary");
        Optional<ProblemCorrelation> corr = correlation == null ? Optional.empty() : correlation;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(UiI18n.get("alerts.problem.title"));
        alert.setHeaderText(summary.host());
        TextArea body = new TextArea(formatBody(summary, corr));
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(corr.isPresent() ? 14 : 8);
        body.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label(UiI18n.get("alerts.problem.hint"));
        hint.setWrapText(true);
        VBox content = new VBox(8, body, hint);
        content.setPrefWidth(420);
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        alert.showAndWait();
    }

    /** Human-readable dialog body (unit-tested). */
    static String formatBody(HostProblemSummary summary) {
        return formatBody(summary, Optional.empty());
    }

    /** Human-readable dialog body including optional correlation (P29-001). */
    static String formatBody(HostProblemSummary summary, Optional<ProblemCorrelation> correlation) {
        StringBuilder sb = new StringBuilder();
        sb.append(UiI18n.get("alerts.problem.desc", summary.description())).append('\n');
        sb.append(UiI18n.get("alerts.problem.rule", summary.rule())).append('\n');
        sb.append(UiI18n.get("alerts.problem.state", summary.lastState())).append('\n');
        sb.append(UiI18n.get(
                        "alerts.problem.started",
                        summary.lastStartedAt() == null
                                ? UiI18n.get("host.ms_na")
                                : TIME_FMT.format(summary.lastStartedAt())))
                .append('\n');
        sb.append(UiI18n.get(
                        "alerts.problem.resolved",
                        summary.lastResolvedAt() == null
                                ? UiI18n.get("host.ms_na")
                                : TIME_FMT.format(summary.lastResolvedAt())))
                .append('\n');
        sb.append(UiI18n.get("alerts.problem.max_duration", formatDuration(summary.maxDuration())))
                .append('\n');
        sb.append(UiI18n.get("alerts.problem.fire_count", summary.fireCount()));
        if (correlation != null && correlation.isPresent()) {
            sb.append('\n').append('\n');
            sb.append(formatCorrelation(correlation.get()));
        }
        return sb.toString();
    }

    /** Formats multi-host correlation for the problem dialog (P29-001). */
    static String formatCorrelation(ProblemCorrelation correlation) {
        Objects.requireNonNull(correlation, "correlation");
        StringBuilder sb = new StringBuilder();
        sb.append(UiI18n.get("alerts.correlation.section")).append('\n');
        if (correlation.lastSharedStableHop().isPresent()) {
            sb.append(UiI18n.get(
                            "alerts.correlation.degraded_after",
                            correlation.degradedHostCount(),
                            correlation.totalHostCount(),
                            correlation.lastSharedStableHop().get()))
                    .append('\n');
        } else {
            sb.append(UiI18n.get(
                            "alerts.correlation.degraded",
                            correlation.degradedHostCount(),
                            correlation.totalHostCount()))
                    .append('\n');
        }
        correlation.firstSharedProblemHop().ifPresent(ip -> sb.append(UiI18n.get("alerts.correlation.problem_hop", ip))
                .append('\n'));
        sb.append(UiI18n.get("alerts.correlation.scope", scopeLabel(correlation.scope())))
                .append('\n');
        if (correlation.timeOverlap()) {
            sb.append(UiI18n.get("alerts.correlation.time_overlap", formatDuration(correlation.startSpread())));
        } else {
            sb.append(UiI18n.get("alerts.correlation.time_spread", formatDuration(correlation.startSpread())));
        }
        return sb.toString();
    }

    static String scopeLabel(ProblemCorrelationScope scope) {
        ProblemCorrelationScope value = scope == null ? ProblemCorrelationScope.UNKNOWN : scope;
        return switch (value) {
            case LOCAL -> UiI18n.get("alerts.correlation.scope.local");
            case ISP -> UiI18n.get("alerts.correlation.scope.isp");
            case EDGE -> UiI18n.get("alerts.correlation.scope.edge");
            case UNKNOWN -> UiI18n.get("alerts.correlation.scope.unknown");
        };
    }

    /** Formats a duration for the problem dialog. */
    static String formatDuration(Duration duration) {
        Duration value = duration == null || duration.isNegative() ? Duration.ZERO : duration;
        long totalSeconds = value.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return UiI18n.get("alerts.duration.hms", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return UiI18n.get("alerts.duration.ms", minutes, seconds);
        }
        return UiI18n.get("alerts.duration.s", seconds);
    }
}

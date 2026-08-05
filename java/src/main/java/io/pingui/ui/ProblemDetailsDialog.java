package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.HostProblemSummary;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Detail view for an unread {@code endpoint_down} session problem (P22-004 /
 * ADR_HOST_PROBLEM_INDICATOR). Closing acks in the caller.
 */
public final class ProblemDetailsDialog {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private ProblemDetailsDialog() {}

    /** Shows problem details; returns after the user closes the dialog. */
    public static void show(Window owner, HostProblemSummary summary) {
        Objects.requireNonNull(summary, "summary");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(UiI18n.get("alerts.problem.title"));
        alert.setHeaderText(summary.host());
        TextArea body = new TextArea(formatBody(summary));
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(8);
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
        return sb.toString();
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

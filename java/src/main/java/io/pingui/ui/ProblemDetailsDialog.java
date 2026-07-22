package io.pingui.ui;

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
        alert.setTitle("Проблема доступності");
        alert.setHeaderText(summary.host());
        TextArea body = new TextArea(formatBody(summary));
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(8);
        body.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label("Після закриття значок зникне до наступного FIRING.");
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
        sb.append("Опис: ").append(summary.description()).append('\n');
        sb.append("Правило: ").append(summary.rule()).append('\n');
        sb.append("Стан: ").append(summary.lastState()).append('\n');
        sb.append("Початок: ")
                .append(summary.lastStartedAt() == null ? "—" : TIME_FMT.format(summary.lastStartedAt()))
                .append('\n');
        sb.append("RESOLVED: ")
                .append(summary.lastResolvedAt() == null ? "—" : TIME_FMT.format(summary.lastResolvedAt()))
                .append('\n');
        sb.append("Макс. тривалість: ")
                .append(formatDuration(summary.maxDuration()))
                .append('\n');
        sb.append("Повтори (FIRING): ").append(summary.fireCount());
        return sb.toString();
    }

    /** Formats a duration for the problem dialog (UK-friendly). */
    static String formatDuration(Duration duration) {
        Duration value = duration == null || duration.isNegative() ? Duration.ZERO : duration;
        long totalSeconds = value.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + " год " + minutes + " хв " + seconds + " с";
        }
        if (minutes > 0) {
            return minutes + " хв " + seconds + " с";
        }
        return seconds + " с";
    }
}

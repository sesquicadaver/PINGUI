package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.IncidentTimelineEntry;
import io.pingui.monitor.IncidentTimelineKind;
import io.pingui.monitor.RouteChangeEvent;
import io.pingui.monitor.Severity;
import io.pingui.monitor.SeverityClassifier;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/** One incident-timeline row in the history list (P11-020 / P29-002). */
public record RouteHistoryItem(long id, IncidentTimelineEntry entry) {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public RouteHistoryItem {
        Objects.requireNonNull(entry, "entry");
    }

    /** Compatibility constructor for route-change-only rows. */
    public RouteHistoryItem(long id, RouteChangeEvent event) {
        this(
                id,
                new IncidentTimelineEntry(
                        id,
                        IncidentTimelineKind.ROUTE_CHANGE,
                        "",
                        event.timestamp(),
                        Duration.ZERO,
                        event.newIps().isEmpty() ? "—" : String.join(" → ", event.newIps()),
                        Optional.of(event)));
    }

    public Optional<RouteChangeEvent> routeEvent() {
        return entry.routeReplay();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        Severity severity = severity();
        sb.append(SeverityTheme.glyph(severity)).append(' ');
        if (entry.kind() == IncidentTimelineKind.ROUTE_CHANGE
                && entry.routeReplay().map(ev -> ev.oldIps().isEmpty()).orElse(false)) {
            sb.append(UiI18n.get("history.initial_route")).append(' ');
        }
        sb.append(TIME_FMT.format(entry.observedAt())).append("  ").append(kindLabel(entry.kind()));
        if (entry.state() != null && !entry.state().isBlank()) {
            sb.append(' ').append(entry.state());
        }
        if (entry.duration() != null && !entry.duration().isZero()) {
            sb.append(" (").append(formatDuration(entry.duration())).append(')');
        }
        if (entry.detail() != null && !entry.detail().isBlank()) {
            sb.append("  ").append(entry.detail());
        }
        return sb.toString();
    }

    /** Unified severity for this timeline row (P31-004). */
    public Severity severity() {
        return SeverityClassifier.forTimeline(entry.kind(), entry.state());
    }

    static String kindLabel(IncidentTimelineKind kind) {
        return switch (kind) {
            case ENDPOINT_DOWN -> UiI18n.get("history.kind.endpoint_down");
            case LATENCY_HIGH -> UiI18n.get("history.kind.latency_high");
            case ROUTE_CHANGE -> UiI18n.get("history.kind.route_change");
            case PROBLEM_ACK -> UiI18n.get("history.kind.problem_ack");
            case DNS_CHANGE -> UiI18n.get("history.kind.dns_change");
            case PROBE_ERROR -> UiI18n.get("history.kind.probe_error");
        };
    }

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

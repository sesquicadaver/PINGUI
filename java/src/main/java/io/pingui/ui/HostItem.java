package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.HostPollCounters;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.HostTargetStats;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Observable host row for the JavaFX list. */
public final class HostItem {
    private static final String DISABLED_ROW = "#f5f5f5";
    private static final String WAITING_ROW = "#d3d3d3";

    private final StringProperty host = new SimpleStringProperty();
    private final BooleanProperty enabled = new SimpleBooleanProperty(false);
    private final BooleanProperty pingOnly = new SimpleBooleanProperty(false);
    private final BooleanProperty showPollCounters = new SimpleBooleanProperty(false);
    private final BooleanProperty showMetrics = new SimpleBooleanProperty(false);
    private final BooleanProperty expertConfigured = new SimpleBooleanProperty(false);
    private final BooleanProperty problemUnread = new SimpleBooleanProperty(false);
    private final StringProperty pollCountersText = new SimpleStringProperty("");
    private final StringProperty metricsText = new SimpleStringProperty("");
    private final StringProperty tagsText = new SimpleStringProperty("");
    private final StringProperty rowColor = new SimpleStringProperty(DISABLED_ROW);
    private List<String> tags = List.of();
    private HostProblemSummary problemSummary;

    public HostItem(String host, boolean enabled) {
        this(host, enabled, false, List.of());
    }

    public HostItem(String host, boolean enabled, boolean pingOnly) {
        this(host, enabled, pingOnly, List.of());
    }

    public HostItem(String host, boolean enabled, boolean pingOnly, List<String> tags) {
        this.host.set(host);
        this.enabled.set(enabled);
        this.pingOnly.set(pingOnly);
        setTags(tags);
        if (!enabled) {
            clearMetrics();
        } else {
            rowColor.set(WAITING_ROW);
        }
    }

    public StringProperty hostProperty() {
        return host;
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public BooleanProperty pingOnlyProperty() {
        return pingOnly;
    }

    public BooleanProperty showPollCountersProperty() {
        return showPollCounters;
    }

    public BooleanProperty showMetricsProperty() {
        return showMetrics;
    }

    public BooleanProperty expertConfiguredProperty() {
        return expertConfigured;
    }

    /** True when the endpoint_down badge should be shown (P22-004). */
    public BooleanProperty problemUnreadProperty() {
        return problemUnread;
    }

    public StringProperty pollCountersTextProperty() {
        return pollCountersText;
    }

    public StringProperty metricsTextProperty() {
        return metricsText;
    }

    public StringProperty tagsTextProperty() {
        return tagsText;
    }

    public StringProperty rowColorProperty() {
        return rowColor;
    }

    public String getHost() {
        return host.get();
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean isPingOnly() {
        return pingOnly.get();
    }

    public boolean isExpertConfigured() {
        return expertConfigured.get();
    }

    public void setExpertConfigured(boolean configured) {
        expertConfigured.set(configured);
    }

    public boolean isProblemUnread() {
        return problemUnread.get();
    }

    public HostProblemSummary problemSummary() {
        return problemSummary;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        tagsText.set(this.tags.isEmpty() ? "" : String.join(", ", this.tags));
    }

    public boolean hasTag(String tag) {
        return io.pingui.config.HostTags.matchesFilter(tags, tag);
    }

    public void clearMetrics() {
        showPollCounters.set(false);
        showMetrics.set(false);
        pollCountersText.set("");
        metricsText.set("");
        rowColor.set(isEnabled() ? WAITING_ROW : DISABLED_ROW);
    }

    /**
     * Applies mode-scoped poll liveness (own row) and RTT aggregates (separate row).
     *
     * <p>Poll line uses role labels {@code спроб}/{@code помилки} — not ping/trace (mode is the
     * checkbox). RTT line stays {@code loss/min/avg/max}.
     */
    public void applyMetrics(HostTargetStats stats, HostPollCounters counters) {
        HostPollCounters safeCounters = counters != null ? counters : HostPollCounters.ZERO;
        String pollText = formatPollCounters(safeCounters);
        String rttText = formatRttMetrics(stats);
        if (pollText.isEmpty() && rttText.isEmpty()) {
            clearMetrics();
            if (isEnabled()) {
                rowColor.set(WAITING_ROW);
            }
            return;
        }
        showPollCounters.set(!pollText.isEmpty());
        pollCountersText.set(pollText);
        showMetrics.set(!rttText.isEmpty());
        metricsText.set(rttText);
        if (stats != null) {
            rowColor.set(PingColor.pingColor(stats.avgMs(), stats.timeout() && stats.avgMs() == null));
        } else if (isEnabled()) {
            rowColor.set(WAITING_ROW);
        }
    }

    /** Updates the unread badge from engine summary (null clears). */
    public void applyProblem(HostProblemSummary summary) {
        this.problemSummary = summary;
        problemUnread.set(summary != null && summary.showBadge());
    }

    public void clearProblem() {
        applyProblem(null);
    }

    static String formatPollCounters(HostPollCounters counters) {
        if (counters == null || counters.attempts() <= 0) {
            return "";
        }
        return UiI18n.get(
                "host.poll_counters", counters.attempts(), counters.errors(), Math.round(counters.errorPct()));
    }

    static String formatRttMetrics(HostTargetStats stats) {
        if (stats == null) {
            return "";
        }
        return UiI18n.get(
                "host.rtt_metrics",
                Math.round(stats.lossPct()),
                formatMs(stats.minMs()),
                formatMs(stats.avgMs()),
                formatMs(stats.maxMs()));
    }

    private static String formatMs(Double value) {
        return value == null ? UiI18n.get("host.ms_na") : String.valueOf(value.intValue());
    }
}

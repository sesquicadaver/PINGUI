package io.pingui.ui;

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
    private final BooleanProperty showMetrics = new SimpleBooleanProperty(false);
    private final BooleanProperty expertConfigured = new SimpleBooleanProperty(false);
    private final BooleanProperty problemUnread = new SimpleBooleanProperty(false);
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
        showMetrics.set(false);
        metricsText.set("");
        rowColor.set(isEnabled() ? WAITING_ROW : DISABLED_ROW);
    }

    public void applyMetrics(HostTargetStats stats) {
        showMetrics.set(true);
        metricsText.set(formatMetrics(stats));
        rowColor.set(PingColor.pingColor(stats.avgMs(), stats.timeout() && stats.avgMs() == null));
    }

    /** Updates the unread badge from engine summary (null clears). */
    public void applyProblem(HostProblemSummary summary) {
        this.problemSummary = summary;
        problemUnread.set(summary != null && summary.showBadge());
    }

    public void clearProblem() {
        applyProblem(null);
    }

    static String formatMetrics(HostTargetStats stats) {
        return String.format(
                "loss %.0f%%  min %s  avg %s  max %s ms",
                stats.lossPct(), formatMs(stats.minMs()), formatMs(stats.avgMs()), formatMs(stats.maxMs()));
    }

    private static String formatMs(Double value) {
        return value == null ? "—" : String.valueOf(value.intValue());
    }
}

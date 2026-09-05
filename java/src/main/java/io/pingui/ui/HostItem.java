package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.model.Models.HopNode;
import io.pingui.monitor.EndpointState;
import io.pingui.monitor.HostNetworkStateClassifier;
import io.pingui.monitor.HostPollCounters;
import io.pingui.monitor.HostProbeMode;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.RouteState;
import io.pingui.monitor.Severity;
import io.pingui.monitor.SeverityClassifier;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Observable host row for the JavaFX list. */
public final class HostItem {
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
    private final StringProperty rowColor = new SimpleStringProperty(SeverityTheme.rowColor(Severity.MUTED));
    private final StringProperty stateGlyph = new SimpleStringProperty("");
    private final StringProperty rttColumnText = new SimpleStringProperty("");
    private final StringProperty lossColumnText = new SimpleStringProperty("");
    private final StringProperty modeColumnText = new SimpleStringProperty("");
    private final StringProperty routeGlyph = new SimpleStringProperty("");
    private final StringProperty severityGlyph = new SimpleStringProperty("");
    private final StringProperty rowDetailsTooltip = new SimpleStringProperty("");
    private List<String> tags = List.of();
    private List<HopNode> lastHops = List.of();
    private String lastTargetIp;
    private HostProbeMode probeMode = HostProbeMode.TRACE;
    private HostProblemSummary problemSummary;
    private HostTargetStats lastStats;
    private EndpointState endpointState = EndpointState.UNKNOWN;
    private RouteState routeState = RouteState.NOT_TRACED;
    private Severity severity = Severity.MUTED;
    private boolean routeChangedLatched;
    private Double avgRttMs;
    private Double lossPct;
    private long lastRouteChangeEpochMs;

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
        if (pingOnly) {
            this.probeMode = HostProbeMode.PING_ONLY;
        }
        setTags(tags);
        refreshModeColumn();
        refreshNetworkStates(null);
        if (!enabled) {
            clearMetrics();
        } else {
            refreshSeverity();
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

    /** Single-glyph endpoint availability indicator (P31-001). */
    public StringProperty stateGlyphProperty() {
        return stateGlyph;
    }

    /** Fixed-width RTT column (avg ms). */
    public StringProperty rttColumnTextProperty() {
        return rttColumnText;
    }

    /** Fixed-width loss column (%). */
    public StringProperty lossColumnTextProperty() {
        return lossColumnText;
    }

    /** Short probe mode label: PING / TRACE / MTR / TCP. */
    public StringProperty modeColumnTextProperty() {
        return modeColumnText;
    }

    /** Muted route glyph; never uses the endpoint-down mark (P31-002). */
    public StringProperty routeGlyphProperty() {
        return routeGlyph;
    }

    /** Severity accent glyph for badge / tooltip (P31-004). */
    public StringProperty severityGlyphProperty() {
        return severityGlyph;
    }

    public Severity severity() {
        return severity;
    }

    /** Average RTT in ms when metrics are present; otherwise {@code null} (P31-005 sort). */
    public Double avgRttMs() {
        return avgRttMs;
    }

    /** Packet loss percent when metrics are present; otherwise {@code null} (P31-005 sort). */
    public Double lossPct() {
        return lossPct;
    }

    /**
     * Epoch millis of the last latched route change; {@code 0} when never observed (P31-005 sort).
     */
    public long lastRouteChangeEpochMs() {
        return lastRouteChangeEpochMs;
    }

    /** Poll counters, RTT detail, tags — not shown inline (P31-001). */
    public StringProperty rowDetailsTooltipProperty() {
        return rowDetailsTooltip;
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

    public HostProbeMode getProbeMode() {
        return probeMode;
    }

    public void setProbeMode(HostProbeMode mode) {
        probeMode = mode != null ? mode : HostProbeMode.TRACE;
        pingOnly.set(probeMode == HostProbeMode.PING_ONLY);
        refreshModeColumn();
        refreshNetworkStates(lastStats);
    }

    public EndpointState endpointState() {
        return endpointState;
    }

    public RouteState routeState() {
        return routeState;
    }

    /** Latches CHANGED until the next poll snapshot (P31-002). */
    public void markRouteChanged() {
        routeChangedLatched = true;
        lastRouteChangeEpochMs = System.currentTimeMillis();
        refreshNetworkStates(lastStats);
    }

    /** Clears a previous poll's CHANGED latch before applying a new snapshot. */
    public void clearRouteChangedLatch() {
        routeChangedLatched = false;
    }

    public void applyRouteHops(List<HopNode> hops) {
        applyRouteHops(hops, null);
    }

    public void applyRouteHops(List<HopNode> hops, String targetIp) {
        lastHops = hops != null ? List.copyOf(hops) : List.of();
        lastTargetIp = targetIp;
        refreshNetworkStates(lastStats);
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
        refreshRowDetailsTooltip(pollCountersText.get(), metricsText.get());
    }

    public boolean hasTag(String tag) {
        return io.pingui.config.HostTags.matchesFilter(tags, tag);
    }

    public void clearMetrics() {
        showPollCounters.set(false);
        showMetrics.set(false);
        pollCountersText.set("");
        metricsText.set("");
        rttColumnText.set(formatRttColumn(null));
        lossColumnText.set(formatLossColumn(null));
        lastStats = null;
        avgRttMs = null;
        lossPct = null;
        refreshNetworkStates(null);
        refreshRowDetailsTooltip("", "");
    }

    /**
     * Applies poll liveness and RTT aggregates; updates unified row columns (P31-001).
     *
     * <p>Poll/RTT detail strings feed the row tooltip; columns show state, avg RTT, loss %, mode.
     * Row background follows {@link Severity} (P31-004) — not raw RTT pastel.
     */
    public void applyMetrics(HostTargetStats stats, HostPollCounters counters) {
        HostPollCounters safeCounters = counters != null ? counters : HostPollCounters.ZERO;
        String pollText = formatPollCounters(safeCounters);
        String rttText = formatRttMetrics(stats);
        if (pollText.isEmpty() && rttText.isEmpty()) {
            clearMetrics();
            return;
        }
        showPollCounters.set(!pollText.isEmpty());
        pollCountersText.set(pollText);
        showMetrics.set(!rttText.isEmpty());
        metricsText.set(rttText);
        rttColumnText.set(formatRttColumn(stats != null ? stats.avgMs() : null));
        lossColumnText.set(formatLossColumn(stats));
        lastStats = stats;
        avgRttMs = stats != null ? stats.avgMs() : null;
        lossPct = stats != null ? stats.lossPct() : null;
        refreshNetworkStates(stats);
        refreshRowDetailsTooltip(pollText, rttText);
    }

    /** Updates the unread badge from engine summary (null clears). */
    public void applyProblem(HostProblemSummary summary) {
        this.problemSummary = summary;
        problemUnread.set(summary != null && summary.showBadge());
        refreshSeverity();
    }

    public void clearProblem() {
        applyProblem(null);
    }

    static String formatModeLabel(HostProbeMode mode) {
        HostProbeMode safe = mode != null ? mode : HostProbeMode.TRACE;
        return switch (safe) {
            case PING_ONLY -> UiI18n.get("host.mode.ping");
            case MTR -> UiI18n.get("host.mode.mtr");
            case TCP_CONNECT -> UiI18n.get("host.mode.tcp");
            case TRACE -> UiI18n.get("host.mode.trace");
        };
    }

    static String formatRttColumn(Double avgMs) {
        return avgMs == null ? UiI18n.get("host.ms_na") : String.valueOf(avgMs.intValue());
    }

    static String formatLossColumn(HostTargetStats stats) {
        if (stats == null) {
            return UiI18n.get("host.ms_na");
        }
        return Math.round(stats.lossPct()) + "%";
    }

    static String formatStateGlyph(boolean enabled, HostTargetStats stats) {
        return formatEndpointGlyph(enabled, HostNetworkStateClassifier.endpoint(enabled, stats));
    }

    static String formatEndpointGlyph(boolean enabled, EndpointState state) {
        if (!enabled) {
            return UiI18n.get("host.state.disabled");
        }
        return switch (state != null ? state : EndpointState.UNKNOWN) {
            case UP -> UiI18n.get("host.state.up");
            case DEGRADED -> UiI18n.get("host.state.degraded");
            case DOWN -> UiI18n.get("host.state.down");
            case UNKNOWN -> UiI18n.get("host.state.waiting");
        };
    }

    static String formatRouteGlyph(RouteState state) {
        return switch (state != null ? state : RouteState.NOT_TRACED) {
            case STABLE -> UiI18n.get("host.route.glyph.stable");
            case CHANGED -> UiI18n.get("host.route.glyph.changed");
            case INCOMPLETE -> UiI18n.get("host.route.glyph.incomplete");
            case NOT_TRACED -> UiI18n.get("host.route.glyph.not_traced");
        };
    }

    static String formatEndpointLabel(EndpointState state) {
        return switch (state != null ? state : EndpointState.UNKNOWN) {
            case UP -> UiI18n.get("host.endpoint.up");
            case DEGRADED -> UiI18n.get("host.endpoint.degraded");
            case DOWN -> UiI18n.get("host.endpoint.down");
            case UNKNOWN -> UiI18n.get("host.endpoint.unknown");
        };
    }

    static String formatRouteLabel(RouteState state) {
        return switch (state != null ? state : RouteState.NOT_TRACED) {
            case STABLE -> UiI18n.get("host.route.stable");
            case CHANGED -> UiI18n.get("host.route.changed");
            case INCOMPLETE -> UiI18n.get("host.route.incomplete");
            case NOT_TRACED -> UiI18n.get("host.route.not_traced");
        };
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

    static String formatRowDetailsTooltip(
            String endpointLine,
            String routeLine,
            String severityLine,
            String tagsLine,
            String pollLine,
            String rttLine) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, endpointLine);
        appendLine(sb, routeLine);
        appendLine(sb, severityLine);
        appendLine(sb, tagsLine);
        appendLine(sb, pollLine);
        appendLine(sb, rttLine);
        return sb.toString();
    }

    private void refreshModeColumn() {
        modeColumnText.set(formatModeLabel(probeMode));
    }

    private void refreshNetworkStates(HostTargetStats stats) {
        endpointState = HostNetworkStateClassifier.endpoint(isEnabled(), stats);
        routeState = HostNetworkStateClassifier.route(probeMode, lastHops, routeChangedLatched, lastTargetIp);
        stateGlyph.set(formatEndpointGlyph(isEnabled(), endpointState));
        routeGlyph.set(formatRouteGlyph(routeState));
        refreshSeverity();
        refreshRowDetailsTooltip(pollCountersText.get(), metricsText.get());
    }

    private void refreshSeverity() {
        severity = SeverityClassifier.forHost(isEnabled(), endpointState, routeState, problemSummary);
        severityGlyph.set(SeverityTheme.glyph(severity));
        rowColor.set(SeverityTheme.rowColor(severity));
    }

    private void refreshRowDetailsTooltip(String pollLine, String rttLine) {
        String tagsLine = tags.isEmpty() ? "" : UiI18n.get("host.row_tags", tagsText.get());
        rowDetailsTooltip.set(formatRowDetailsTooltip(
                UiI18n.get("host.row_endpoint", formatEndpointLabel(endpointState)),
                UiI18n.get("host.row_route", formatRouteLabel(routeState)),
                UiI18n.get("host.row_severity", SeverityTheme.label(severity)),
                tagsLine,
                pollLine,
                rttLine));
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        sb.append(line);
    }

    private static String formatMs(Double value) {
        return value == null ? UiI18n.get("host.ms_na") : String.valueOf(value.intValue());
    }
}

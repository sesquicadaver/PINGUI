package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.monitor.EndpointState;
import io.pingui.monitor.HostProbeMode;
import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.RouteState;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Formats compact selected-host inspector lines (P31-003). Pure text — no JavaFX.
 */
public final class HostInspectorFormatter {
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private HostInspectorFormatter() {}

    public record Snapshot(
            String address,
            String resolvedIp,
            String mode,
            String lastPoll,
            String rtt,
            String jitter,
            String loss,
            String endpoint,
            String route,
            String lastRouteChange,
            String problem) {}

    public static Snapshot from(
            String address,
            String resolvedIp,
            HostProbeMode mode,
            Instant lastPollAt,
            HostTargetStats stats,
            HopStatsSummary terminalHopStats,
            EndpointState endpoint,
            RouteState route,
            Instant lastRouteChangeAt,
            HostProblemSummary problem) {
        return new Snapshot(
                nullToNa(address),
                nullToNa(resolvedIp),
                HostItem.formatModeLabel(mode),
                formatInstant(lastPollAt),
                HostItem.formatRttColumn(stats != null ? stats.avgMs() : null),
                formatJitter(terminalHopStats),
                HostItem.formatLossColumn(stats),
                HostItem.formatEndpointLabel(endpoint),
                HostItem.formatRouteLabel(route),
                formatInstant(lastRouteChangeAt),
                formatProblem(problem));
    }

    /**
     * Authoritative endpoint address for inspector/copy (P35-001 / NOC truth).
     *
     * <p>Prefers {@code lastTargetIp} from the monitor (traceroute header / DNS / ping resolve). Does
     * <em>not</em> fall back to the last reachable hop — that is often an intermediate router on an
     * incomplete path and must not be labeled as the destination.
     */
    public static String resolvedEndpointIp(String configuredAddress, String lastTargetIp) {
        if (lastTargetIp != null && !lastTargetIp.isBlank()) {
            return lastTargetIp.strip();
        }
        return "";
    }

    /**
     * @deprecated Misleading: last reachable hop ≠ destination. Use {@link #resolvedEndpointIp}.
     */
    @Deprecated
    public static String resolvedIpFromHops(List<HopNode> hops) {
        if (hops == null || hops.isEmpty()) {
            return "";
        }
        for (int i = hops.size() - 1; i >= 0; i--) {
            HopNode hop = hops.get(i);
            if (hop.isReachable()) {
                return hop.ip();
            }
        }
        return "";
    }

    /** Omits duplicate {@code address → address} noise when resolved equals the configured target. */
    public static String formatAddressLine(String address, String resolvedIp) {
        String safeAddress = nullToNa(address);
        if (resolvedIp == null || resolvedIp.isBlank()) {
            return safeAddress;
        }
        String safeResolved = resolvedIp.strip();
        if (safeAddress.equals(safeResolved) || UiI18n.get("host.ms_na").equals(safeAddress)) {
            return safeAddress;
        }
        return UiI18n.get("inspector.address", safeAddress, safeResolved);
    }

    static String formatInstant(Instant instant) {
        return instant == null ? UiI18n.get("host.ms_na") : TIME_FMT.format(instant);
    }

    static String formatJitter(HopStatsSummary summary) {
        if (summary == null || summary.jitterMs() == null) {
            return UiI18n.get("host.ms_na");
        }
        return String.valueOf(summary.jitterMs().intValue());
    }

    static String formatProblem(HostProblemSummary problem) {
        if (problem == null) {
            return UiI18n.get("inspector.problem.none");
        }
        if (HostProblemSummary.STATE_FIRING.equals(problem.lastState())) {
            return problem.description();
        }
        if (problem.showBadge()) {
            return problem.description();
        }
        return UiI18n.get("inspector.problem.none");
    }

    static String nullToNa(String value) {
        return value == null || value.isBlank() ? UiI18n.get("host.ms_na") : value;
    }
}

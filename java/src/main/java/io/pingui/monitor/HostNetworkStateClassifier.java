package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import java.util.List;

/**
 * Splits host row status into endpoint vs route (P31-002 / P33-002 / P34-003).
 *
 * <p>{@code PING_ONLY} and {@code TCP_CONNECT} are always {@link RouteState#NOT_TRACED}; missing
 * path data is not an error. A reachable last hop counts as target only when it matches {@code
 * targetIp} (when known) — an intermediate router must not look like the endpoint.
 *
 * <p>Endpoint classification prefers the <em>current</em> target sample: a live timeout is {@link
 * EndpointState#DOWN} even when historical avg RTT / session loss would otherwise look healthy.
 */
public final class HostNetworkStateClassifier {
    static final double DOWN_LOSS_PCT = 50.0;
    static final double DEGRADED_LOSS_PCT = 10.0;

    private HostNetworkStateClassifier() {}

    public static EndpointState endpoint(boolean enabled, HostTargetStats stats) {
        if (!enabled) {
            return EndpointState.UNKNOWN;
        }
        if (stats == null) {
            return EndpointState.UNKNOWN;
        }
        // Current target sample timed out → DOWN; do not let session-lifetime avg/loss mask it
        // (P34-003). Historical RTT remains available for display columns.
        if (stats.timeout()) {
            return EndpointState.DOWN;
        }
        if (stats.avgMs() == null) {
            return EndpointState.UNKNOWN;
        }
        if (stats.lossPct() >= DOWN_LOSS_PCT) {
            return EndpointState.DOWN;
        }
        if (stats.lossPct() >= DEGRADED_LOSS_PCT) {
            return EndpointState.DEGRADED;
        }
        return EndpointState.UP;
    }

    public static RouteState route(HostProbeMode mode, List<HopNode> hops, boolean routeChanged) {
        return route(mode, hops, routeChanged, null);
    }

    public static RouteState route(HostProbeMode mode, List<HopNode> hops, boolean routeChanged, String targetIp) {
        HostProbeMode safe = mode != null ? mode : HostProbeMode.TRACE;
        if (safe.isTargetOnly()) {
            return RouteState.NOT_TRACED;
        }
        if (hops == null || hops.isEmpty()) {
            return RouteState.NOT_TRACED;
        }
        if (!targetReached(hops, targetIp)) {
            return RouteState.INCOMPLETE;
        }
        if (routeChanged) {
            return RouteState.CHANGED;
        }
        return RouteState.STABLE;
    }

    static boolean targetReached(List<HopNode> hops) {
        return targetReached(hops, null);
    }

    /**
     * True when the path reaches the real target. With a known {@code targetIp}, the last hop must
     * match it — a reachable intermediate router is still incomplete (P33-002).
     */
    static boolean targetReached(List<HopNode> hops, String targetIp) {
        if (hops == null || hops.isEmpty()) {
            return false;
        }
        HopNode last = hops.get(hops.size() - 1);
        if (!last.isReachable()) {
            return false;
        }
        if (targetIp == null || targetIp.isBlank()) {
            return true;
        }
        return targetIp.equals(last.ip());
    }
}

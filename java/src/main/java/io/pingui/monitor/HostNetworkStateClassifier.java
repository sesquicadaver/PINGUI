package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import java.util.List;

/**
 * Splits host row status into endpoint vs route (P31-002 / pingui-evo-gui §3).
 *
 * <p>{@code PING_ONLY} and {@code TCP_CONNECT} are always {@link RouteState#NOT_TRACED}; missing
 * path data is not an error.
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
        if (stats.timeout() && stats.avgMs() == null) {
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
        HostProbeMode safe = mode != null ? mode : HostProbeMode.TRACE;
        if (safe.isTargetOnly()) {
            return RouteState.NOT_TRACED;
        }
        if (hops == null || hops.isEmpty()) {
            return RouteState.NOT_TRACED;
        }
        if (!targetReached(hops)) {
            return RouteState.INCOMPLETE;
        }
        if (routeChanged) {
            return RouteState.CHANGED;
        }
        return RouteState.STABLE;
    }

    static boolean targetReached(List<HopNode> hops) {
        HopNode last = hops.get(hops.size() - 1);
        return last.isReachable();
    }
}

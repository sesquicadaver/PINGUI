package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Last-known hop IP tracking for inactive route display. */
public final class RouteHistory {
    private RouteHistory() {}

    public static void recordLastKnown(Map<Integer, HopNode> lastKnown, List<HopNode> nodes) {
        for (HopNode node : nodes) {
            if (node.isReachable()) {
                lastKnown.put(node.hop(), node);
            }
        }
    }

    public static List<HopNode> routeWithLastKnownIps(List<HopNode> route, Map<Integer, HopNode> lastKnown) {
        List<HopNode> result = new ArrayList<>();
        for (HopNode node : route) {
            if (node.isReachable()) {
                result.add(node);
                continue;
            }
            HopNode known = lastKnown.get(node.hop());
            if (known == null) {
                result.add(node);
                continue;
            }
            result.add(new HopNode(node.hop(), known.ip(), known.pingMs(), false));
        }
        return List.copyOf(result);
    }
}

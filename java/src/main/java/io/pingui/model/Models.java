package io.pingui.model;

import java.time.Instant;
import java.util.List;

/** Domain constants and factory helpers. */
public final class Models {
    public static final String TIMEOUT_IP = "*";

    private Models() {}

    public static HopNode timeout(int hop) {
        return new HopNode(hop, TIMEOUT_IP, null, true);
    }

    public record HopNode(int hop, String ip, Double pingMs, boolean timeout) {
        public HopNode {
            if (hop < 1) {
                throw new IllegalArgumentException("hop must be >= 1");
            }
        }

        public boolean isReachable() {
            return !timeout && !TIMEOUT_IP.equals(ip);
        }
    }

    public record RouteSnapshot(String target, String targetIp, List<HopNode> nodes, Instant timestamp) {
        public RouteSnapshot {
            nodes = List.copyOf(nodes);
        }

        public RouteSnapshot(String target, String targetIp, List<HopNode> nodes) {
            this(target, targetIp, nodes, Instant.now());
        }

        public List<String> routeIps() {
            return nodes.stream()
                    .filter(HopNode::isReachable)
                    .map(HopNode::ip)
                    .toList();
        }
    }

    public static final class HostSessionData {
        private List<HopNode> currentRoute = List.of();
        private List<HopNode> previousRoute = List.of();
        private final java.util.Map<Integer, HopNode> lastKnownByHop = new java.util.HashMap<>();
        private final java.util.Map<String, java.util.List<Double>> pingHistory = new java.util.HashMap<>();
        private boolean enabled;

        public List<HopNode> getCurrentRoute() {
            return currentRoute;
        }

        public void setCurrentRoute(List<HopNode> currentRoute) {
            this.currentRoute = List.copyOf(currentRoute);
        }

        public List<HopNode> getPreviousRoute() {
            return previousRoute;
        }

        public void setPreviousRoute(List<HopNode> previousRoute) {
            this.previousRoute = List.copyOf(previousRoute);
        }

        public java.util.Map<Integer, HopNode> getLastKnownByHop() {
            return lastKnownByHop;
        }

        public java.util.Map<String, java.util.List<Double>> getPingHistory() {
            return pingHistory;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

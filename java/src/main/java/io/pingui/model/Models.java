package io.pingui.model;

import io.pingui.config.PingExpertEntry;
import java.time.Instant;
import java.util.List;

/** Domain constants and factory helpers. */
public final class Models {
    public static final String TIMEOUT_IP = "*";
    public static final int MAX_HOP_RTT_SAMPLES = 50;

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
            return nodes.stream().filter(HopNode::isReachable).map(HopNode::ip).toList();
        }
    }

    public static final class HopProbeStats {
        private int probes;
        private int successes;
        private final java.util.List<Double> rttSamples = new java.util.ArrayList<>();

        public int getProbes() {
            return probes;
        }

        public int getSuccesses() {
            return successes;
        }

        public java.util.List<Double> getRttSamples() {
            return rttSamples;
        }

        public void recordProbeAttempt() {
            probes++;
        }

        public void recordProbeSuccess(double rttMs) {
            successes++;
            rttSamples.add(rttMs);
        }
    }

    public record HopStatsSummary(Double jitterMs, double lossPct) {}

    public static final class HostSessionData {
        private List<HopNode> currentRoute = List.of();
        private List<HopNode> previousRoute = List.of();
        private final java.util.Map<Integer, HopNode> lastKnownByHop = new java.util.HashMap<>();
        private final java.util.Map<String, java.util.List<Double>> pingHistory = new java.util.HashMap<>();
        private final java.util.Map<Integer, HopProbeStats> hopStats = new java.util.HashMap<>();
        private boolean enabled;
        private boolean pingOnly;
        private PingExpertEntry pingExpert = PingExpertEntry.empty();

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

        public java.util.Map<Integer, HopProbeStats> getHopStats() {
            return hopStats;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPingOnly() {
            return pingOnly;
        }

        public void setPingOnly(boolean pingOnly) {
            this.pingOnly = pingOnly;
        }

        public PingExpertEntry getPingExpert() {
            return pingExpert;
        }

        public void setPingExpert(PingExpertEntry pingExpert) {
            this.pingExpert = pingExpert != null ? pingExpert.normalized() : PingExpertEntry.empty();
        }
    }
}

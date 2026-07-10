package io.pingui.ui;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Hop-by-hop route comparison with optional Δ RTT (P14-010). */
public final class RouteDiff {
    private RouteDiff() {}

    public enum Kind {
        UNCHANGED,
        CHANGED,
        ADDED,
        REMOVED
    }

    /** One hop line in a route diff. */
    public record Row(int hop, String oldIp, String newIp, Double oldRttMs, Double newRttMs, Kind kind) {
        public Row {
            if (hop < 1) {
                throw new IllegalArgumentException("hop must be >= 1");
            }
            if (kind == null) {
                throw new IllegalArgumentException("kind required");
            }
        }

        /** {@code newRtt − oldRtt}, or {@code null} when either side is missing. */
        public Double deltaRttMs() {
            if (oldRttMs == null || newRttMs == null) {
                return null;
            }
            return newRttMs - oldRttMs;
        }

        /** Human-readable Ukrainian summary for ListView cells. */
        public String summary() {
            return switch (kind) {
                case UNCHANGED -> hop + ": " + displayIp(newIp) + formatRtt(newRttMs) + "  (без змін)";
                case CHANGED -> hop + ": " + displayIp(oldIp) + " → " + displayIp(newIp) + formatDelta();
                case ADDED -> hop + ": — → " + displayIp(newIp) + formatRtt(newRttMs) + "  (+)";
                case REMOVED -> hop + ": " + displayIp(oldIp) + formatRtt(oldRttMs) + " → —  (−)";
            };
        }

        private String formatDelta() {
            Double delta = deltaRttMs();
            if (delta == null) {
                return "";
            }
            String sign = delta > 0 ? "+" : "";
            return String.format(Locale.ROOT, "  Δ%s%.1f ms", sign, delta);
        }

        private static String formatRtt(Double rttMs) {
            if (rttMs == null) {
                return "";
            }
            return String.format(Locale.ROOT, " (%.1f ms)", rttMs);
        }

        private static String displayIp(String ip) {
            if (ip == null || ip.isBlank() || Models.TIMEOUT_IP.equals(ip)) {
                return Models.TIMEOUT_IP;
            }
            return HopDisplay.formatHopIp(ip);
        }
    }

    /** Aligns hops by index and classifies each as unchanged / changed / added / removed. */
    public static List<Row> compare(List<HopNode> oldRoute, List<HopNode> newRoute) {
        List<HopNode> oldNodes = oldRoute != null ? oldRoute : List.of();
        List<HopNode> newNodes = newRoute != null ? newRoute : List.of();
        int max = Math.max(oldNodes.size(), newNodes.size());
        List<Row> rows = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            HopNode oldNode = i < oldNodes.size() ? oldNodes.get(i) : null;
            HopNode newNode = i < newNodes.size() ? newNodes.get(i) : null;
            int hop = i + 1;
            if (oldNode == null) {
                rows.add(new Row(hop, null, ipOf(newNode), null, rttOf(newNode), Kind.ADDED));
            } else if (newNode == null) {
                rows.add(new Row(hop, ipOf(oldNode), null, rttOf(oldNode), null, Kind.REMOVED));
            } else if (sameHop(oldNode, newNode)) {
                rows.add(new Row(hop, ipOf(oldNode), ipOf(newNode), rttOf(oldNode), rttOf(newNode), Kind.UNCHANGED));
            } else {
                rows.add(new Row(hop, ipOf(oldNode), ipOf(newNode), rttOf(oldNode), rttOf(newNode), Kind.CHANGED));
            }
        }
        return List.copyOf(rows);
    }

    /** True when at least one hop is not {@link Kind#UNCHANGED}. */
    public static boolean hasChanges(List<Row> rows) {
        return rows.stream().anyMatch(row -> row.kind() != Kind.UNCHANGED);
    }

    private static boolean sameHop(HopNode left, HopNode right) {
        return Objects.equals(normalizeIp(left), normalizeIp(right));
    }

    private static String normalizeIp(HopNode node) {
        if (node == null || node.timeout() || Models.TIMEOUT_IP.equals(node.ip())) {
            return Models.TIMEOUT_IP;
        }
        return node.ip();
    }

    private static String ipOf(HopNode node) {
        return node == null ? null : node.ip();
    }

    private static Double rttOf(HopNode node) {
        return node == null ? null : node.pingMs();
    }
}

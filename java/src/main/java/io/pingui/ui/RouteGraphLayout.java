package io.pingui.ui;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Pure layout helpers for vertical route graph (normalized 0–1 coordinates). */
public final class RouteGraphLayout {
    static final String INACTIVE_NODE = "#b0b0b0";
    static final String ORIGIN_COLOR = "#add8e6";
    private static final double MARGIN_X = 0.03;
    private static final double MARGIN_Y = 0.03;
    private static final double COL_GAP = 0.04;
    private static final double NODE_GAP = 0.006;
    private static final double BOX_LINE_HEIGHT = 0.016;
    private static final double BOX_PAD_Y = 0.012;
    private static final int MAX_PARALLEL_PROBES = 4;

    private RouteGraphLayout() {}

    public record ColumnLayout(double centerX, double width) {}

    public record GraphNode(
            String id, String label, String color, double x, double y, double width, double height) {}

    public record GraphScene(List<GraphNode> nodes, List<Edge> edges) {
        public record Edge(String fromId, String toId, boolean inactive) {}
    }

    public static boolean routesDiffer(List<HopNode> current, List<HopNode> previous) {
        if (previous == null || previous.isEmpty()) {
            return false;
        }
        return !ips(current).equals(ips(previous));
    }

    public static ColumnPair columnLayouts(boolean showPrevious) {
        if (showPrevious) {
            double colWidth = (1.0 - 2 * MARGIN_X - COL_GAP) / 2.0;
            return new ColumnPair(
                    new ColumnLayout(MARGIN_X + colWidth / 2.0, colWidth),
                    new ColumnLayout(MARGIN_X + colWidth + COL_GAP + colWidth / 2.0, colWidth));
        }
        double colWidth = 1.0 - 2 * MARGIN_X;
        return new ColumnPair(null, new ColumnLayout(0.5, colWidth));
    }

    public record ColumnPair(ColumnLayout inactive, ColumnLayout active) {}

    public static boolean columnsSeparated(List<GraphNode> inactive, List<GraphNode> active) {
        if (inactive.isEmpty() || active.isEmpty()) {
            return true;
        }
        double inactiveRight = inactive.stream().mapToDouble(n -> n.x() + n.width() / 2).max().orElse(0);
        double activeLeft = active.stream().mapToDouble(n -> n.x() - n.width() / 2).min().orElse(1);
        return inactiveRight < activeLeft;
    }

    public static GraphScene buildScene(
            List<HopNode> current,
            List<HopNode> previous,
            Function<String, Double> avgPingFn) {
        return buildScene(current, previous, avgPingFn, hop -> null);
    }

    public static GraphScene buildScene(
            List<HopNode> current,
            List<HopNode> previous,
            Function<String, Double> avgPingFn,
            Function<Integer, HopStatsSummary> hopStatsFn) {
        if (current == null || current.isEmpty()) {
            return new GraphScene(List.of(), List.of());
        }
        List<HopNode> prev = previous != null ? previous : List.of();
        boolean showPrevious = routesDiffer(current, prev);
        ColumnPair columns = columnLayouts(showPrevious);

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphScene.Edge> edges = new ArrayList<>();

        if (showPrevious && columns.inactive() != null) {
            ChainResult inactiveChain =
                    chainNodes(prev, avgPingFn, hopStatsFn, columns.inactive(), true, "prev");
            nodes.addAll(inactiveChain.nodes());
            edges.addAll(inactiveChain.edges());
        }
        ChainResult activeChain = chainNodes(current, avgPingFn, hopStatsFn, columns.active(), false, "act");
        nodes.addAll(activeChain.nodes());
        edges.addAll(activeChain.edges());
        return new GraphScene(List.copyOf(nodes), List.copyOf(edges));
    }

    private static ChainResult chainNodes(
            List<HopNode> route,
            Function<String, Double> avgPingFn,
            Function<Integer, HopStatsSummary> hopStatsFn,
            ColumnLayout column,
            boolean inactive,
            String idPrefix) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphScene.Edge> edges = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> colors = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        String pcId = idPrefix + "_localhost";
        String pcLabel = "Ваш ПК";
        labels.add(pcLabel);
        colors.add(inactive ? INACTIVE_NODE : ORIGIN_COLOR);
        ids.add(pcId);

        for (HopNode hop : route) {
            String label = PingColor.nodeLabel(hop, avgPingFn, inactive ? ignored -> null : hopStatsFn);
            String nodeId = idPrefix + "_hop_" + hop.hop() + "_" + hop.ip();
            labels.add(label);
            colors.add(PingColor.nodeColor(hop, avgPingFn, inactive));
            ids.add(nodeId);
        }

        List<Double> heights = labels.stream().map(RouteGraphLayout::boxHeight).toList();
        double nodeWidth = column.width();
        HeightLayout heightLayout = layoutYForHeights(heights);
        List<Double> yCoords = heightLayout.centersY();
        List<Double> layoutHeights = heightLayout.heights();

        String prevId = ids.get(0);
        nodes.add(new GraphNode(
                ids.get(0),
                labels.get(0),
                colors.get(0),
                column.centerX(),
                yCoords.get(0),
                nodeWidth,
                layoutHeights.get(0)));

        for (int index = 1; index < labels.size(); index++) {
            nodes.add(new GraphNode(
                    ids.get(index),
                    labels.get(index),
                    colors.get(index),
                    column.centerX(),
                    yCoords.get(index),
                    nodeWidth,
                    layoutHeights.get(index)));
            edges.add(new GraphScene.Edge(prevId, ids.get(index), inactive));
            prevId = ids.get(index);
        }
        return new ChainResult(nodes, edges);
    }

    private record ChainResult(List<GraphNode> nodes, List<GraphScene.Edge> edges) {}

    record HeightLayout(List<Double> centersY, List<Double> heights) {}

    static List<Double> layoutYForChain(int chainLen) {
        if (chainLen <= 0) {
            return List.of();
        }
        List<Double> heights = new ArrayList<>();
        for (int index = 0; index < chainLen; index++) {
            heights.add(0.05);
        }
        return layoutYForHeights(heights).centersY();
    }

    static HeightLayout layoutYForHeights(List<Double> heights) {
        if (heights.isEmpty()) {
            return new HeightLayout(List.of(), List.of());
        }
        if (heights.size() == 1) {
            return new HeightLayout(List.of(0.5), List.copyOf(heights));
        }
        double gap = NODE_GAP;
        double available = 1.0 - 2 * MARGIN_Y;
        List<Double> scaled = List.copyOf(heights);
        double sumHeights = scaled.stream().mapToDouble(Double::doubleValue).sum();
        double total = sumHeights + gap * (scaled.size() - 1);
        if (total > available) {
            gap = Math.max(0.0, (available - sumHeights) / (scaled.size() - 1));
            if (sumHeights > available) {
                double scale = available / sumHeights;
                scaled = scaled.stream().map(height -> height * scale).toList();
                gap = 0.0;
            }
        }
        double cursorTop = 1.0 - MARGIN_Y;
        List<Double> centers = new ArrayList<>();
        for (int index = 0; index < scaled.size(); index++) {
            double height = scaled.get(index);
            centers.add(cursorTop - height / 2);
            cursorTop -= height;
            if (index < scaled.size() - 1) {
                cursorTop -= gap;
            }
        }
        return new HeightLayout(List.copyOf(centers), List.copyOf(scaled));
    }

    /** Uniform width within a column (parity with Python GraphCanvas). */
    static boolean chainUsesUniformWidth(List<GraphNode> chain, double expectedWidth) {
        return chain.stream().allMatch(node -> Math.abs(node.width() - expectedWidth) < 1e-9);
    }

    static double boxHeight(String label) {
        int lineCount = Math.max(1, label.split("\n", -1).length);
        return BOX_PAD_Y + BOX_LINE_HEIGHT * lineCount;
    }

    static boolean chainNodesDoNotOverlap(List<GraphNode> chain) {
        if (chain.size() < 2) {
            return true;
        }
        for (int index = 0; index < chain.size() - 1; index++) {
            GraphNode upper = chain.get(index);
            GraphNode lower = chain.get(index + 1);
            double upperBottom = upper.y() - upper.height() / 2;
            double lowerTop = lower.y() + lower.height() / 2;
            if (upperBottom < lowerTop - 1e-9) {
                return false;
            }
        }
        return true;
    }

    private static List<String> ips(List<HopNode> route) {
        return route.stream().map(HopNode::ip).toList();
    }
}

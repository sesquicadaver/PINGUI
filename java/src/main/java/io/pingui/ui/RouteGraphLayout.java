package io.pingui.ui;

import io.pingui.model.Models.HopNode;
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
                    chainNodes(prev, avgPingFn, columns.inactive(), true, "prev");
            nodes.addAll(inactiveChain.nodes());
            edges.addAll(inactiveChain.edges());
        }
        ChainResult activeChain = chainNodes(current, avgPingFn, columns.active(), false, "act");
        nodes.addAll(activeChain.nodes());
        edges.addAll(activeChain.edges());
        return new GraphScene(List.copyOf(nodes), List.copyOf(edges));
    }

    private static ChainResult chainNodes(
            List<HopNode> route,
            Function<String, Double> avgPingFn,
            ColumnLayout column,
            boolean inactive,
            String idPrefix) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphScene.Edge> edges = new ArrayList<>();
        List<Double> yCoords = layoutYForChain(route.size() + 1);
        int yIndex = 0;

        String pcId = idPrefix + "_localhost";
        String pcLabel = "Ваш ПК";
        double pcHeight = boxHeight(pcLabel);
        nodes.add(new GraphNode(
                pcId,
                pcLabel,
                inactive ? INACTIVE_NODE : ORIGIN_COLOR,
                column.centerX(),
                yCoords.get(yIndex),
                column.width(),
                pcHeight));
        String prevId = pcId;
        yIndex++;

        for (HopNode hop : route) {
            String label = PingColor.nodeLabel(hop, avgPingFn);
            String nodeId = idPrefix + "_hop_" + hop.hop() + "_" + hop.ip();
            nodes.add(new GraphNode(
                    nodeId,
                    label,
                    PingColor.nodeColor(hop, avgPingFn, inactive),
                    column.centerX(),
                    yCoords.get(yIndex),
                    column.width(),
                    boxHeight(label)));
            edges.add(new GraphScene.Edge(prevId, nodeId, inactive));
            prevId = nodeId;
            yIndex++;
        }
        return new ChainResult(nodes, edges);
    }

    private record ChainResult(List<GraphNode> nodes, List<GraphScene.Edge> edges) {}

    static List<Double> layoutYForChain(int chainLen) {
        if (chainLen <= 0) {
            return List.of();
        }
        if (chainLen == 1) {
            return List.of(0.5);
        }
        double top = 1.0 - MARGIN_Y;
        double bottom = MARGIN_Y;
        double step = (top - bottom) / (chainLen - 1);
        List<Double> coords = new ArrayList<>();
        for (int index = 0; index < chainLen; index++) {
            coords.add(top - index * step);
        }
        return List.copyOf(coords);
    }

    static double boxHeight(String label) {
        int lineCount = Math.max(1, label.split("\n", -1).length);
        return 0.038 + 0.024 * lineCount;
    }

    private static List<String> ips(List<HopNode> route) {
        return route.stream().map(HopNode::ip).toList();
    }
}

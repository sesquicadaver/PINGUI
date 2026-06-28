package io.pingui.ui;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.ui.RouteGraphLayout.GraphNode;
import io.pingui.ui.RouteGraphLayout.GraphScene;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** JavaFX route graph: vertical layout, inactive column left, active right. */
public final class GraphCanvas extends Region {
    private static final double TEXT_PAD = 8.0;
    private static final Font LABEL_FONT = Font.font("Monospace", 11);

    private final Canvas canvas = new Canvas();
    private List<HopNode> currentRoute = List.of();
    private List<HopNode> previousRoute = List.of();
    private Function<String, Double> avgPingFn = ip -> null;
    private Function<Integer, HopStatsSummary> hopStatsFn = hop -> null;

    public GraphCanvas() {
        getChildren().add(canvas);
        widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        heightProperty().addListener((obs, oldVal, newVal) -> redraw());
    }

    public void renderRoute(
            List<HopNode> route,
            Function<String, Double> avgPingFn,
            List<HopNode> previousRoute,
            Function<Integer, HopStatsSummary> hopStatsFn) {
        this.currentRoute = route != null ? List.copyOf(route) : List.of();
        this.previousRoute = previousRoute != null ? List.copyOf(previousRoute) : List.of();
        this.avgPingFn = avgPingFn != null ? avgPingFn : ip -> null;
        this.hopStatsFn = hopStatsFn != null ? hopStatsFn : hop -> null;
        redraw();
    }

    public void renderRoute(
            List<HopNode> route,
            Function<String, Double> avgPingFn,
            List<HopNode> previousRoute) {
        renderRoute(route, avgPingFn, previousRoute, hop -> null);
    }

    private void redraw() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.web("#fafafa"));
        gc.fillRect(0, 0, width, height);

        GraphScene scene = RouteGraphLayout.buildScene(currentRoute, previousRoute, avgPingFn, hopStatsFn);
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode node : scene.nodes()) {
            byId.put(node.id(), node);
        }
        for (GraphScene.Edge edge : scene.edges()) {
            GraphNode src = byId.get(edge.fromId());
            GraphNode dst = byId.get(edge.toId());
            if (src != null && dst != null) {
                drawEdge(gc, src, dst, edge.inactive(), width, height);
            }
        }
        for (GraphNode node : scene.nodes()) {
            drawNode(gc, node, width, height);
        }
    }

    private static void drawNode(GraphicsContext gc, GraphNode node, double width, double height) {
        double boxW = node.width() * width;
        double boxH = node.height() * height;
        double left = (node.x() - node.width() / 2) * width;
        double top = (node.y() - node.height() / 2) * height;
        gc.setFill(Color.web(node.color()));
        gc.setStroke(Color.web("#555555"));
        gc.setLineWidth(1.0);
        gc.fillRoundRect(left, top, boxW, boxH, 4, 4);
        gc.strokeRoundRect(left, top, boxW, boxH, 4, 4);
        gc.setFill(Color.web("#222222"));
        gc.setFont(LABEL_FONT);
        String[] lines = node.label().split("\n", -1);
        double lineHeight = LABEL_FONT.getSize() + 2;
        double textBlockH = lines.length * lineHeight;
        double textY = top + (boxH - textBlockH) / 2 + lineHeight * 0.75;
        for (String line : lines) {
            gc.fillText(line, left + TEXT_PAD, textY);
            textY += lineHeight;
        }
    }

    private static void drawEdge(
            GraphicsContext gc,
            GraphNode src,
            GraphNode dst,
            boolean inactive,
            double width,
            double height) {
        double x1 = src.x() * width;
        double y1 = (src.y() + src.height() / 2) * height;
        double x2 = dst.x() * width;
        double y2 = (dst.y() - dst.height() / 2) * height;
        gc.setStroke(Color.web(inactive ? "#c8c8c8" : "#666666"));
        gc.setLineWidth(inactive ? 1.0 : 1.2);
        if (inactive) {
            gc.setLineDashes(6, 6);
        } else {
            gc.setLineDashes(null);
        }
        gc.strokeLine(x1, y1, x2, y2);
        gc.setLineDashes(null);
    }
}

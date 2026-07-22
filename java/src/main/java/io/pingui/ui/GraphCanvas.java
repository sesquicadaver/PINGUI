package io.pingui.ui;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.ui.RouteGraphInteraction.ViewTransform;
import io.pingui.ui.RouteGraphLayout.GraphNode;
import io.pingui.ui.RouteGraphLayout.GraphScene;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Window;

/**
 * JavaFX route graph: vertical layout, inactive column left, active right (pairs with {@link
 * RouteDiffPresenter}).
 *
 * <p>Hop labels may include async rDNS from {@link io.pingui.dns.DnsResolver}; {@link MainController}
 * registers a listener that redraws via {@link RouteGraphPresenter} when PTR resolves.
 *
 * <p>P20-012 UX: wheel zoom, drag pan, hover tooltip, double-click hop → copy IP.
 */
public final class GraphCanvas extends Region {
    private static final double TEXT_PAD = 6.0;
    private static final double DRAG_THRESHOLD_PX = 4.0;
    private static final Font LABEL_FONT = Font.font("Monospace", 10);

    private final Canvas canvas = new Canvas();
    private final Tooltip hoverTip = new Tooltip();
    private List<HopNode> currentRoute = List.of();
    private List<HopNode> previousRoute = List.of();
    private Function<String, Double> avgPingFn = ip -> null;
    private Function<Integer, HopStatsSummary> hopStatsFn = hop -> null;
    private String staticViewMessage;
    private GraphScene lastScene = new GraphScene(List.of(), List.of());
    private double contentWidth;
    private double contentHeight;
    private ViewTransform transform = ViewTransform.identity();
    private Consumer<String> onHopIpCopied = ip -> {};
    private double pressViewX;
    private double pressViewY;
    private double pressPanX;
    private double pressPanY;
    private boolean dragging;
    private boolean pressMoved;

    public GraphCanvas() {
        getChildren().add(canvas);
        hoverTip.setWrapText(true);
        hoverTip.setMaxWidth(320);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::redraw);
            }
        });
        setOnScroll(this::onScroll);
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        setOnMouseMoved(this::onMouseMoved);
        setOnMouseExited(e -> hoverTip.hide());
        setOnMouseClicked(this::onMouseClicked);
    }

    /** Optional status callback after a hop IP is copied (P20-012). */
    public void setOnHopIpCopied(Consumer<String> listener) {
        this.onHopIpCopied = listener != null ? listener : ip -> {};
    }

    public void renderRoute(
            List<HopNode> route,
            Function<String, Double> avgPingFn,
            List<HopNode> previousRoute,
            Function<Integer, HopStatsSummary> hopStatsFn) {
        this.staticViewMessage = null;
        this.currentRoute = route != null ? List.copyOf(route) : List.of();
        this.previousRoute = previousRoute != null ? List.copyOf(previousRoute) : List.of();
        this.avgPingFn = avgPingFn != null ? avgPingFn : ip -> null;
        this.hopStatsFn = hopStatsFn != null ? hopStatsFn : hop -> null;
        scheduleRedraw();
    }

    public void renderRoute(List<HopNode> route, Function<String, Double> avgPingFn, List<HopNode> previousRoute) {
        renderRoute(route, avgPingFn, previousRoute, hop -> null);
    }

    public void renderStaticView(String message) {
        this.staticViewMessage = message;
        this.currentRoute = List.of();
        this.previousRoute = List.of();
        this.lastScene = new GraphScene(List.of(), List.of());
        this.transform = ViewTransform.identity();
        hoverTip.hide();
        scheduleRedraw();
    }

    /** Package-visible for tests. */
    ViewTransform viewTransform() {
        return transform;
    }

    /** Package-visible for tests — hop IP under view coordinates, if copyable. */
    Optional<String> hopIpAt(double viewX, double viewY) {
        return nodeAt(viewX, viewY).map(GraphNode::hopIp).filter(ip -> ip != null && !ip.isBlank());
    }

    private void scheduleRedraw() {
        if (Platform.isFxApplicationThread()) {
            redraw();
        } else {
            Platform.runLater(this::redraw);
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        double width = getWidth();
        double height = getHeight();
        if (width > 0 && height > 0) {
            redraw();
        }
    }

    private void redraw() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        contentWidth = width;
        contentHeight = height;
        resizeCanvasBuffer(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.web("#fafafa"));
        gc.fillRect(0, 0, width, height);

        if (staticViewMessage != null) {
            drawCenteredMessage(gc, staticViewMessage, width, height);
            lastScene = new GraphScene(List.of(), List.of());
            return;
        }

        lastScene = RouteGraphLayout.buildScene(currentRoute, previousRoute, avgPingFn, hopStatsFn);
        gc.save();
        gc.translate(transform.panX(), transform.panY());
        gc.scale(transform.zoom(), transform.zoom());
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode node : lastScene.nodes()) {
            byId.put(node.id(), node);
        }
        for (GraphScene.Edge edge : lastScene.edges()) {
            GraphNode src = byId.get(edge.fromId());
            GraphNode dst = byId.get(edge.toId());
            if (src != null && dst != null) {
                drawEdge(gc, src, dst, edge.inactive(), width, height);
            }
        }
        for (GraphNode node : lastScene.nodes()) {
            drawNode(gc, node, width, height);
        }
        gc.restore();
    }

    /** JavaFX Canvas may skip repainting when width/height are unchanged (common on Windows SW pipeline). */
    private void resizeCanvasBuffer(double width, double height) {
        if (canvas.getWidth() == width && canvas.getHeight() == height) {
            canvas.setWidth(width + 1.0);
        }
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    private void onScroll(ScrollEvent event) {
        if (staticViewMessage != null) {
            return;
        }
        event.consume();
        double factor = event.getDeltaY() > 0 ? RouteGraphInteraction.ZOOM_STEP : 1.0 / RouteGraphInteraction.ZOOM_STEP;
        transform = transform.zoomAt(event.getX(), event.getY(), factor);
        redraw();
    }

    private void onMousePressed(MouseEvent event) {
        if (staticViewMessage != null || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        pressViewX = event.getX();
        pressViewY = event.getY();
        pressPanX = transform.panX();
        pressPanY = transform.panY();
        dragging = true;
        pressMoved = false;
    }

    private void onMouseDragged(MouseEvent event) {
        if (!dragging || staticViewMessage != null) {
            return;
        }
        double dx = event.getX() - pressViewX;
        double dy = event.getY() - pressViewY;
        if (Math.hypot(dx, dy) >= DRAG_THRESHOLD_PX) {
            pressMoved = true;
        }
        transform = new ViewTransform(transform.zoom(), pressPanX + dx, pressPanY + dy);
        redraw();
    }

    private void onMouseReleased(MouseEvent event) {
        dragging = false;
    }

    private void onMouseMoved(MouseEvent event) {
        if (staticViewMessage != null) {
            hoverTip.hide();
            return;
        }
        Optional<GraphNode> hit = nodeAt(event.getX(), event.getY());
        if (hit.isEmpty()) {
            hoverTip.hide();
            return;
        }
        hoverTip.setText(RouteGraphInteraction.tooltipFor(hit.get()));
        Window window = getScene() != null ? getScene().getWindow() : null;
        Point2D screen = localToScreen(event.getX(), event.getY());
        if (window == null || screen == null) {
            return;
        }
        hoverTip.show(this, screen.getX() + 14, screen.getY() + 10);
    }

    private void onMouseClicked(MouseEvent event) {
        if (staticViewMessage != null || event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) {
            return;
        }
        if (pressMoved) {
            return;
        }
        Optional<GraphNode> hit = nodeAt(event.getX(), event.getY());
        if (hit.isPresent() && hit.get().hopIp() != null && !hit.get().hopIp().isBlank()) {
            copyHopIp(hit.get().hopIp());
            return;
        }
        transform = ViewTransform.identity();
        redraw();
    }

    private Optional<GraphNode> nodeAt(double viewX, double viewY) {
        double worldX = transform.toWorldX(viewX);
        double worldY = transform.toWorldY(viewY);
        return RouteGraphInteraction.findNodeAt(lastScene, contentWidth, contentHeight, worldX, worldY);
    }

    private void copyHopIp(String ip) {
        ClipboardContent content = new ClipboardContent();
        content.putString(ip);
        Clipboard.getSystemClipboard().setContent(content);
        onHopIpCopied.accept(ip);
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
            GraphicsContext gc, GraphNode src, GraphNode dst, boolean inactive, double width, double height) {
        double x1 = src.x() * width;
        double y1 = (src.y() - src.height() / 2) * height;
        double x2 = dst.x() * width;
        double y2 = (dst.y() + dst.height() / 2) * height;
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

    private static void drawCenteredMessage(GraphicsContext gc, String message, double width, double height) {
        Font font = Font.font("Monospace", 18);
        gc.setFill(Color.web("#333333"));
        gc.setFont(font);
        double textWidth = gc.getFont().getSize() * message.length() * 0.55;
        gc.fillText(message, Math.max(TEXT_PAD, (width - textWidth) / 2), height / 2);
    }
}

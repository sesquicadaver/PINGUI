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
 *
 * <p>P24-001…004: canvas buffer resizes only on real size change ({@link #resizeCanvasIfNeeded});
 * pixel paints coalesce to one {@link Platform#runLater} pulse via {@link #requestRedraw()}; route
 * layout ({@link RouteGraphLayout#buildScene}) is cached across pan/zoom until the route/stats
 * change; draw colors and hover tooltip text are cached (P24-004).
 */
public final class GraphCanvas extends Region {
    private static final double TEXT_PAD = 6.0;
    private static final double DRAG_THRESHOLD_PX = 4.0;
    private static final Font LABEL_FONT = Font.font("Monospace", 10);
    private static final Color COLOR_BG = Color.web("#fafafa");
    private static final Color COLOR_NODE_STROKE = Color.web("#555555");
    private static final Color COLOR_LABEL = Color.web("#222222");
    private static final Color COLOR_MESSAGE = Color.web("#333333");
    private static final Color COLOR_EDGE_ACTIVE = Color.web("#666666");
    private static final Color COLOR_EDGE_INACTIVE = Color.web("#c8c8c8");

    /** Documented invalidate ladder: G1–G3 + G4 paint/hover caches. */
    static final String INVALIDATE_STRATEGY =
            "step1-clearRect-no-buffer-churn+coalesced-pulse+cached-graph-scene+paint-hover-cache";

    private final Canvas canvas = new Canvas();
    private final Tooltip hoverTip = new Tooltip();
    private final Map<String, Color> nodeFillCache = new HashMap<>();
    private List<HopNode> currentRoute = List.of();
    private List<HopNode> previousRoute = List.of();
    private Function<String, Double> avgPingFn = ip -> null;
    private Function<Integer, HopStatsSummary> hopStatsFn = hop -> null;
    private String staticViewMessage;
    private GraphScene lastScene = new GraphScene(List.of(), List.of());
    private Map<String, GraphNode> nodesById = Map.of();
    private boolean layoutDirty = true;
    private int layoutBuildCount;
    private String hoveredNodeId;
    private int hoverTipTextUpdates;
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
    private int canvasResizeCount;
    private int paintCount;
    /** Visible across threads if {@code renderRoute} is ever called off the FX thread. */
    private volatile boolean paintDirty;

    private volatile boolean paintScheduled;

    public GraphCanvas() {
        getChildren().add(canvas);
        hoverTip.setWrapText(true);
        hoverTip.setMaxWidth(320);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                requestRedraw();
            }
        });
        setOnScroll(this::onScroll);
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        setOnMouseMoved(this::onMouseMoved);
        setOnMouseExited(e -> clearHover());
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
        layoutDirty = true;
        requestRedraw();
    }

    public void renderRoute(List<HopNode> route, Function<String, Double> avgPingFn, List<HopNode> previousRoute) {
        renderRoute(route, avgPingFn, previousRoute, hop -> null);
    }

    public void renderStaticView(String message) {
        this.staticViewMessage = message;
        this.currentRoute = List.of();
        this.previousRoute = List.of();
        this.lastScene = new GraphScene(List.of(), List.of());
        this.nodesById = Map.of();
        this.layoutDirty = false;
        this.transform = ViewTransform.identity();
        hoverTip.hide();
        requestRedraw();
    }

    /** Package-visible for tests. */
    ViewTransform viewTransform() {
        return transform;
    }

    /** Package-visible for tests — hop IP under view coordinates, if copyable. */
    Optional<String> hopIpAt(double viewX, double viewY) {
        return nodeAt(viewX, viewY).map(GraphNode::hopIp).filter(ip -> ip != null && !ip.isBlank());
    }

    /** Package-visible for tests — canvas buffer width after last sync resize. */
    double canvasBufferWidth() {
        return canvas.getWidth();
    }

    /** Package-visible for tests — canvas buffer height after last sync resize. */
    double canvasBufferHeight() {
        return canvas.getHeight();
    }

    /** Package-visible for tests — how many times the canvas buffer was resized. */
    int canvasResizeCount() {
        return canvasResizeCount;
    }

    /** Package-visible for tests. */
    void resetCanvasResizeCount() {
        canvasResizeCount = 0;
    }

    /** Package-visible for tests — completed {@link #paintPixels()} invocations. */
    int paintCount() {
        return paintCount;
    }

    /** Package-visible for tests. */
    void resetPaintCount() {
        paintCount = 0;
    }

    /** Package-visible for tests — immediate paint (bypasses coalesce). */
    void paintForTest() {
        paintPixels();
    }

    /** Package-visible for tests — schedule coalesced paint like production paths. */
    void requestRedrawForTest() {
        requestRedraw();
    }

    /** Package-visible for tests — how many times {@link RouteGraphLayout#buildScene} ran. */
    int layoutBuildCount() {
        return layoutBuildCount;
    }

    /** Package-visible for tests. */
    void resetLayoutBuildCount() {
        layoutBuildCount = 0;
    }

    /** Package-visible for tests — set pan/zoom without invalidating route layout. */
    void setViewTransformForTest(ViewTransform next) {
        this.transform = next != null ? next : ViewTransform.identity();
    }

    /** Package-visible for tests — times tooltip text was rewritten (hover dedupe). */
    int hoverTipTextUpdates() {
        return hoverTipTextUpdates;
    }

    /** Package-visible for tests. */
    void resetHoverTipTextUpdates() {
        hoverTipTextUpdates = 0;
    }

    /** Package-visible for tests — cached fill for a node color hex (same instance on repeat). */
    Color cachedNodeFillForTest(String hex) {
        return nodeFill(hex);
    }

    /**
     * Coalesced paint request (P24-002). Multiple calls before the next FX pulse produce one {@link
     * #paintPixels()}.
     */
    private void requestRedraw() {
        paintDirty = true;
        if (paintScheduled) {
            return;
        }
        paintScheduled = true;
        Platform.runLater(this::runScheduledPaint);
    }

    private void runScheduledPaint() {
        paintScheduled = false;
        if (!paintDirty) {
            return;
        }
        paintDirty = false;
        paintPixels();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        double width = getWidth();
        double height = getHeight();
        if (width > 0 && height > 0) {
            // Sync buffer resize on real layout size changes; paint coalesces to the next pulse.
            resizeCanvasIfNeeded(width, height);
            requestRedraw();
        }
    }

    private void paintPixels() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        contentWidth = width;
        contentHeight = height;
        // Defensive sync if paint runs before layout (still no-op when sizes match — G1 primitive).
        resizeCanvasIfNeeded(width, height);
        paintCount++;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        gc.setFill(COLOR_BG);
        gc.fillRect(0, 0, width, height);

        if (staticViewMessage != null) {
            drawCenteredMessage(gc, staticViewMessage, width, height);
            lastScene = new GraphScene(List.of(), List.of());
            nodesById = Map.of();
            layoutDirty = false;
            return;
        }

        rebuildSceneLayoutIfNeeded();
        gc.save();
        gc.translate(transform.panX(), transform.panY());
        gc.scale(transform.zoom(), transform.zoom());
        for (GraphScene.Edge edge : lastScene.edges()) {
            GraphNode src = nodesById.get(edge.fromId());
            GraphNode dst = nodesById.get(edge.toId());
            if (src != null && dst != null) {
                drawEdge(gc, src, dst, edge.inactive(), width, height);
            }
        }
        for (GraphNode node : lastScene.nodes()) {
            drawNode(gc, node, width, height);
        }
        gc.restore();
    }

    /**
     * Rebuilds {@link #lastScene} / {@link #nodesById} only when the route model changed (P24-003).
     * Pan/zoom/size paints reuse the cached layout.
     */
    private void rebuildSceneLayoutIfNeeded() {
        if (!layoutDirty) {
            return;
        }
        layoutDirty = false;
        layoutBuildCount++;
        lastScene = RouteGraphLayout.buildScene(currentRoute, previousRoute, avgPingFn, hopStatsFn);
        Map<String, GraphNode> byId = new HashMap<>();
        for (GraphNode node : lastScene.nodes()) {
            byId.put(node.id(), node);
        }
        nodesById = Map.copyOf(byId);
    }

    /**
     * Resize the Canvas buffer only when dimensions actually change. Does not bump buffer size to
     * force a Prism realloc (removed in P24-001). Paint invalidation is via {@code clearRect} in
     * {@link #paintPixels()}.
     */
    private void resizeCanvasIfNeeded(double width, double height) {
        if (canvas.getWidth() == width && canvas.getHeight() == height) {
            return;
        }
        canvas.setWidth(width);
        canvas.setHeight(height);
        canvasResizeCount++;
    }

    private void onScroll(ScrollEvent event) {
        if (staticViewMessage != null) {
            return;
        }
        event.consume();
        double factor = event.getDeltaY() > 0 ? RouteGraphInteraction.ZOOM_STEP : 1.0 / RouteGraphInteraction.ZOOM_STEP;
        transform = transform.zoomAt(event.getX(), event.getY(), factor);
        requestRedraw();
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
        requestRedraw();
    }

    private void onMouseReleased(MouseEvent event) {
        dragging = false;
    }

    private void onMouseMoved(MouseEvent event) {
        if (staticViewMessage != null) {
            clearHover();
            return;
        }
        Optional<GraphNode> hit = nodeAt(event.getX(), event.getY());
        if (hit.isEmpty()) {
            clearHover();
            return;
        }
        GraphNode node = hit.get();
        String nodeId = node.id();
        if (!nodeId.equals(hoveredNodeId)) {
            hoveredNodeId = nodeId;
            hoverTip.setText(RouteGraphInteraction.tooltipFor(node));
            hoverTipTextUpdates++;
        }
        Window window = getScene() != null ? getScene().getWindow() : null;
        Point2D screen = localToScreen(event.getX(), event.getY());
        if (window == null || screen == null) {
            return;
        }
        hoverTip.show(this, screen.getX() + 14, screen.getY() + 10);
    }

    private void clearHover() {
        hoveredNodeId = null;
        hoverTip.hide();
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
        requestRedraw();
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

    private void drawNode(GraphicsContext gc, GraphNode node, double width, double height) {
        double boxW = node.width() * width;
        double boxH = node.height() * height;
        double left = (node.x() - node.width() / 2) * width;
        double top = (node.y() - node.height() / 2) * height;
        gc.setFill(nodeFill(node.color()));
        gc.setStroke(COLOR_NODE_STROKE);
        gc.setLineWidth(1.0);
        gc.fillRoundRect(left, top, boxW, boxH, 4, 4);
        gc.strokeRoundRect(left, top, boxW, boxH, 4, 4);
        gc.setFill(COLOR_LABEL);
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

    private Color nodeFill(String hex) {
        String key = hex != null ? hex : "";
        return nodeFillCache.computeIfAbsent(key, Color::web);
    }

    private static void drawEdge(
            GraphicsContext gc, GraphNode src, GraphNode dst, boolean inactive, double width, double height) {
        double x1 = src.x() * width;
        double y1 = (src.y() - src.height() / 2) * height;
        double x2 = dst.x() * width;
        double y2 = (dst.y() + dst.height() / 2) * height;
        gc.setStroke(inactive ? COLOR_EDGE_INACTIVE : COLOR_EDGE_ACTIVE);
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
        gc.setFill(COLOR_MESSAGE);
        gc.setFont(font);
        double textWidth = gc.getFont().getSize() * message.length() * 0.55;
        gc.fillText(message, Math.max(TEXT_PAD, (width - textWidth) / 2), height / 2);
    }
}

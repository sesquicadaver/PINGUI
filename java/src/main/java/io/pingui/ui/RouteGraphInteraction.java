package io.pingui.ui;

import io.pingui.ui.RouteGraphLayout.GraphNode;
import io.pingui.ui.RouteGraphLayout.GraphScene;
import java.util.Optional;

/**
 * Pure helpers for Extended graph UX: hit-test, tooltip text, zoom/pan transform (P20-012).
 *
 * <p>World coordinates match the untransformed canvas content size used by {@link GraphCanvas}.
 */
public final class RouteGraphInteraction {
    public static final double MIN_ZOOM = 0.5;
    public static final double MAX_ZOOM = 4.0;
    public static final double ZOOM_STEP = 1.1;

    private RouteGraphInteraction() {}

    /** Zoom + pan applied before drawing the normalized scene. */
    public record ViewTransform(double zoom, double panX, double panY) {
        public ViewTransform {
            if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
                throw new IllegalArgumentException("zoom out of range");
            }
        }

        public static ViewTransform identity() {
            return new ViewTransform(1.0, 0.0, 0.0);
        }

        public double toWorldX(double viewX) {
            return (viewX - panX) / zoom;
        }

        public double toWorldY(double viewY) {
            return (viewY - panY) / zoom;
        }

        public ViewTransform panBy(double dx, double dy) {
            return new ViewTransform(zoom, panX + dx, panY + dy);
        }

        /**
         * Zooms by {@code factor} keeping the world point under ({@code viewX},{@code viewY}) stable.
         */
        public ViewTransform zoomAt(double viewX, double viewY, double factor) {
            double nextZoom = clampZoom(zoom * factor);
            if (nextZoom == zoom) {
                return this;
            }
            double worldX = toWorldX(viewX);
            double worldY = toWorldY(viewY);
            double nextPanX = viewX - worldX * nextZoom;
            double nextPanY = viewY - worldY * nextZoom;
            return new ViewTransform(nextZoom, nextPanX, nextPanY);
        }
    }

    public static double clampZoom(double zoom) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    public static boolean contains(
            GraphNode node, double contentWidth, double contentHeight, double worldX, double worldY) {
        double boxW = node.width() * contentWidth;
        double boxH = node.height() * contentHeight;
        double left = (node.x() - node.width() / 2) * contentWidth;
        double top = (node.y() - node.height() / 2) * contentHeight;
        return worldX >= left && worldX <= left + boxW && worldY >= top && worldY <= top + boxH;
    }

    /** Topmost (last drawn) node under the world point, if any. */
    public static Optional<GraphNode> findNodeAt(
            GraphScene scene, double contentWidth, double contentHeight, double worldX, double worldY) {
        if (scene == null || scene.nodes().isEmpty() || contentWidth <= 0 || contentHeight <= 0) {
            return Optional.empty();
        }
        for (int index = scene.nodes().size() - 1; index >= 0; index--) {
            GraphNode node = scene.nodes().get(index);
            if (contains(node, contentWidth, contentHeight, worldX, worldY)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    public static String tooltipFor(GraphNode node) {
        if (node == null) {
            return "";
        }
        String body = node.label() != null ? node.label() : "";
        if (node.hopIp() != null && !node.hopIp().isBlank()) {
            return body + "\n\nПодвійний клік — копіювати IP";
        }
        return body;
    }
}

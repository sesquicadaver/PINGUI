package io.pingui.ui;

/**
 * Persisted main-window geometry (stage bounds + SplitPane divider + view mode).
 *
 * <p>Clamp is pure and injectable so unit tests do not need JavaFX {@code Screen}.
 */
record WindowGeometry(double x, double y, double width, double height, double divider, UiViewMode viewMode) {
    static final double DEFAULT_DIVIDER = 0.35;
    static final double MIN_WIDTH = 580.0;
    static final double MIN_HEIGHT = 400.0;
    static final double MIN_DIVIDER = 0.05;
    static final double MAX_DIVIDER = 0.95;

    WindowGeometry {
        if (viewMode == null) {
            viewMode = UiViewMode.SIMPLE;
        }
        divider = clampDivider(divider);
    }

    static double clampDivider(double divider) {
        if (Double.isNaN(divider) || Double.isInfinite(divider)) {
            return DEFAULT_DIVIDER;
        }
        return Math.max(MIN_DIVIDER, Math.min(MAX_DIVIDER, divider));
    }

    /**
     * Clamps this geometry into {@code visualBounds}. If the window center is outside the rectangle,
     * falls back to placing a default-sized window at the bounds origin.
     *
     * @param visualBoundsX visual bounds min X
     * @param visualBoundsY visual bounds min Y
     * @param visualBoundsW visual bounds width
     * @param visualBoundsH visual bounds height
     * @param defaultWidth fallback width when geometry is unusable
     * @param defaultHeight fallback height when geometry is unusable
     */
    WindowGeometry clamp(
            double visualBoundsX,
            double visualBoundsY,
            double visualBoundsW,
            double visualBoundsH,
            double defaultWidth,
            double defaultHeight) {
        if (visualBoundsW <= 0 || visualBoundsH <= 0 || !finite(visualBoundsW) || !finite(visualBoundsH)) {
            return defaults(defaultWidth, defaultHeight);
        }
        double w = finite(width) ? width : defaultWidth;
        double h = finite(height) ? height : defaultHeight;
        w = Math.max(MIN_WIDTH, Math.min(w, visualBoundsW));
        h = Math.max(MIN_HEIGHT, Math.min(h, visualBoundsH));
        double cx = finite(x) ? x + w / 2.0 : visualBoundsX + visualBoundsW / 2.0;
        double cy = finite(y) ? y + h / 2.0 : visualBoundsY + visualBoundsH / 2.0;
        boolean centerInside = cx >= visualBoundsX
                && cx <= visualBoundsX + visualBoundsW
                && cy >= visualBoundsY
                && cy <= visualBoundsY + visualBoundsH;
        double nx;
        double ny;
        if (centerInside && finite(x) && finite(y)) {
            nx = x;
            ny = y;
        } else {
            nx = visualBoundsX + Math.max(0, (visualBoundsW - w) / 2.0);
            ny = visualBoundsY + Math.max(0, (visualBoundsH - h) / 2.0);
        }
        // Keep the window fully inside the visual bounds when possible.
        nx = Math.min(Math.max(nx, visualBoundsX), visualBoundsX + visualBoundsW - w);
        ny = Math.min(Math.max(ny, visualBoundsY), visualBoundsY + visualBoundsH - h);
        return new WindowGeometry(nx, ny, w, h, divider, viewMode);
    }

    static WindowGeometry defaults(double defaultWidth, double defaultHeight) {
        return new WindowGeometry(
                Double.NaN,
                Double.NaN,
                Math.max(MIN_WIDTH, defaultWidth),
                Math.max(MIN_HEIGHT, defaultHeight),
                DEFAULT_DIVIDER,
                UiViewMode.SIMPLE);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}

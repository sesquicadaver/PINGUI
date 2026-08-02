package io.pingui.ui;

/**
 * Persisted main-window geometry (stage bounds + SplitPane divider + view mode).
 *
 * <p>Clamp is pure and injectable so unit tests do not need JavaFX {@code Screen}.
 */
record WindowGeometry(double x, double y, double width, double height, double divider, UiViewMode viewMode) {
    static final double DEFAULT_DIVIDER = 0.35;
    /** Matches Simple host-column min width ({@code HostListPanel.PANEL_MIN_WIDTH}). */
    static final double MIN_WIDTH = 580.0;
    /** Target left SplitPane width in Extended (host list; graph gets the remainder). */
    static final double EXTENDED_LEFT_WIDTH = 600.0;
    /** Cold-start / missing-prefs width for Simple (content-sized; height stays independent). */
    static final double DEFAULT_SIMPLE_WIDTH = MIN_WIDTH;
    /** Cold-start / missing-prefs width for Extended (graph + history). */
    static final double DEFAULT_EXTENDED_WIDTH = 1400.0;
    /** Cold-start / missing-prefs height for Simple. */
    static final double DEFAULT_SIMPLE_HEIGHT = 700.0;
    /** Cold-start / expand-on-toggle height for Extended. */
    static final double DEFAULT_EXTENDED_HEIGHT = 820.0;

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
     * SplitPane divider so the left pane targets {@code leftWidth} pixels at {@code stageWidth}.
     * Avoids the ~50/50 look when min-width constraints fight a too-small left fraction.
     */
    static double dividerForLeftWidth(double stageWidth, double leftWidth) {
        if (!finite(stageWidth) || stageWidth < MIN_WIDTH || !finite(leftWidth) || leftWidth <= 0) {
            return DEFAULT_DIVIDER;
        }
        return clampDivider(Math.min(leftWidth, stageWidth) / stageWidth);
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

    /**
     * Simple-mode stage width: shrink to laid-out content pref when the stage is wider. Height is
     * never adjusted here (P24 geometry: width-only fit for compact Simple chrome).
     */
    static double fitSimpleWidth(double stageWidth, double contentPrefWidth) {
        if (!finite(stageWidth) || !finite(contentPrefWidth) || contentPrefWidth <= 0) {
            return stageWidth;
        }
        if (stageWidth <= contentPrefWidth + 0.5) {
            return stageWidth;
        }
        return Math.max(MIN_WIDTH, contentPrefWidth);
    }

    /**
     * Extended-mode stage width: expand up to the Extended default when the stage is still Simple-narrow
     * after a mode toggle (never shrinks).
     */
    static double ensureExtendedWidth(double stageWidth, double extendedDefaultWidth) {
        if (!finite(stageWidth) || !finite(extendedDefaultWidth) || extendedDefaultWidth < MIN_WIDTH) {
            return stageWidth;
        }
        if (stageWidth + 0.5 >= extendedDefaultWidth) {
            return stageWidth;
        }
        return extendedDefaultWidth;
    }

    /** Extended-mode stage height: expand up to the Extended default (never shrinks). */
    static double ensureExtendedHeight(double stageHeight, double extendedDefaultHeight) {
        if (!finite(stageHeight) || !finite(extendedDefaultHeight) || extendedDefaultHeight < MIN_HEIGHT) {
            return stageHeight;
        }
        if (stageHeight + 0.5 >= extendedDefaultHeight) {
            return stageHeight;
        }
        return extendedDefaultHeight;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}

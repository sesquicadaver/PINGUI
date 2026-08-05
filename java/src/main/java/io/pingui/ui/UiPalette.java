package io.pingui.ui;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

/**
 * Light-first UI palette shared by CSS ({@code pingui.css}) and Canvas paint.
 *
 * <p><b>Contract:</b> hex constants here must match the looked-up colors documented at the top of
 * {@code io/pingui/ui/pingui.css}. Canvas cannot read CSS vars — keep both in sync (P24-008).
 *
 * <p>Dark mode is out of scope; CSS reserves a {@code .theme-dark} block for later.
 */
public final class UiPalette {
    /** Scene / graph background. */
    public static final String BG_HEX = "#fafafa";
    /** Raised panel surface. */
    public static final String PANEL_HEX = "#ffffff";
    /** Primary body text. */
    public static final String TEXT_HEX = "#222222";
    /** Secondary / muted text. */
    public static final String MUTED_HEX = "#666666";
    /** Message text on canvas. */
    public static final String MESSAGE_HEX = "#333333";
    /** Node stroke / strong border. */
    public static final String STROKE_HEX = "#555555";
    /** Active edge. */
    public static final String EDGE_ACTIVE_HEX = "#666666";
    /** Inactive edge. */
    public static final String EDGE_INACTIVE_HEX = "#c8c8c8";
    /** Danger / problem accent. */
    public static final String DANGER_HEX = "#b71c1c";
    /** Spacing token (px) mirrored in CSS comments. */
    public static final double SPACE_SM = 8.0;

    public static final Color BG = Color.web(BG_HEX);
    public static final Color PANEL = Color.web(PANEL_HEX);
    public static final Color TEXT = Color.web(TEXT_HEX);
    public static final Color MUTED = Color.web(MUTED_HEX);
    public static final Color MESSAGE = Color.web(MESSAGE_HEX);
    public static final Color STROKE = Color.web(STROKE_HEX);
    public static final Color EDGE_ACTIVE = Color.web(EDGE_ACTIVE_HEX);
    public static final Color EDGE_INACTIVE = Color.web(EDGE_INACTIVE_HEX);
    public static final Color DANGER = Color.web(DANGER_HEX);

    private static final String STYLESHEET_RESOURCE = "pingui.css";

    private UiPalette() {}

    /** Attaches the light theme stylesheet to {@code scene}. */
    public static void applyTo(Scene scene) {
        var url = UiPalette.class.getResource(STYLESHEET_RESOURCE);
        if (url == null) {
            throw new IllegalStateException("Missing classpath resource io/pingui/ui/" + STYLESHEET_RESOURCE);
        }
        String external = url.toExternalForm();
        if (!scene.getStylesheets().contains(external)) {
            scene.getStylesheets().add(external);
        }
    }
}

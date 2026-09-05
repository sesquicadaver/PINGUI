package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.Severity;

/**
 * Severity → row color, glyph, CSS style class, and contrast-safe accent (P31-004 / P31-007).
 *
 * <p>Red/critical pastel is reserved for {@link Severity#CRITICAL} only. Accent text colors are
 * chosen for readable contrast on light / pastel surfaces (not color-only status).
 */
public final class SeverityTheme {
    private SeverityTheme() {}

    public static String rowColor(Severity severity) {
        Severity safe = severity != null ? severity : Severity.MUTED;
        return switch (safe) {
            case CRITICAL -> "#ffcdd2";
            case WARNING -> "#ffe0b2";
            case NOTICE -> "#fff9c4";
            case INFO -> "#e8f5e9";
            case MUTED -> "#f5f5f5";
        };
    }

    /**
     * Foreground accent for badges / timeline (must stay in sync with {@code .pingui-severity-*} in
     * {@code pingui.css}).
     */
    public static String accentColor(Severity severity) {
        Severity safe = severity != null ? severity : Severity.MUTED;
        return switch (safe) {
            case CRITICAL -> "#b71c1c";
            case WARNING -> "#bf360c";
            case NOTICE -> "#7a5c00";
            case INFO -> "#222222";
            case MUTED -> "#666666";
        };
    }

    public static String glyph(Severity severity) {
        Severity safe = severity != null ? severity : Severity.MUTED;
        return switch (safe) {
            case CRITICAL -> UiI18n.get("severity.glyph.critical");
            case WARNING -> UiI18n.get("severity.glyph.warning");
            case NOTICE -> UiI18n.get("severity.glyph.notice");
            case INFO -> UiI18n.get("severity.glyph.info");
            case MUTED -> UiI18n.get("severity.glyph.muted");
        };
    }

    public static String label(Severity severity) {
        Severity safe = severity != null ? severity : Severity.MUTED;
        return switch (safe) {
            case CRITICAL -> UiI18n.get("severity.critical");
            case WARNING -> UiI18n.get("severity.warning");
            case NOTICE -> UiI18n.get("severity.notice");
            case INFO -> UiI18n.get("severity.info");
            case MUTED -> UiI18n.get("severity.muted");
        };
    }

    /** CSS style class for badges / timeline accents. */
    public static String styleClass(Severity severity) {
        Severity safe = severity != null ? severity : Severity.MUTED;
        return switch (safe) {
            case CRITICAL -> "pingui-severity-critical";
            case WARNING -> "pingui-severity-warning";
            case NOTICE -> "pingui-severity-notice";
            case INFO -> "pingui-severity-info";
            case MUTED -> "pingui-severity-muted";
        };
    }
}

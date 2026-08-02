package io.pingui.ui;

import io.pingui.i18n.UiI18n;

/**
 * Empty-state copy for Extended history and Simple mode (P20-007 / P25).
 * Unit-tested without JavaFX dialogs.
 */
public final class EmptyStateHints {
    private EmptyStateHints() {}

    /** Default idle status before first probe / feedback. */
    public static String waitingForData() {
        return UiI18n.get("empty.waiting");
    }

    /** Simple mode: event log is hidden — point operators to Extended. */
    public static String simpleNoLog() {
        return UiI18n.get("empty.simple_no_log");
    }

    /** Extended history without SQLite session. */
    public static String noSqlite() {
        return UiI18n.get("empty.no_sqlite");
    }

    /** SQLite connected but no host selected in the history filter. */
    public static String noHostSelected() {
        return UiI18n.get("empty.no_host");
    }

    /** SQLite + host selected, but no route_change rows in the lookback window. */
    public static String emptyHistory() {
        return UiI18n.get("empty.history");
    }

    /**
     * Whether Simple-mode idle status may be replaced with {@link #simpleNoLog()}.
     * Keeps live feedback / probe messages intact.
     */
    public static boolean isReplaceableSimpleStatus(String current) {
        if (current == null || current.isBlank()) {
            return true;
        }
        return current.equals(waitingForData()) || current.equals(simpleNoLog());
    }
}

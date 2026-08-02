package io.pingui.ui;

/**
 * UK empty-state copy for Extended history and Simple mode (P20-007).
 * Unit-tested without JavaFX dialogs.
 */
public final class EmptyStateHints {
    private EmptyStateHints() {}

    /** Default idle status before first probe / feedback. */
    public static String waitingForData() {
        return "Очікування даних…";
    }

    /** Simple mode: event log is hidden — point operators to Extended. */
    public static String simpleNoLog() {
        return "Журнал подій доступний у режимі «Розширений».";
    }

    /** Extended history without SQLite session. */
    public static String noSqlite() {
        return "Історія змін потребує SQLite. Налаштування → База даних…";
    }

    /** SQLite connected but no host selected in the history filter. */
    public static String noHostSelected() {
        return "Оберіть ціль у фільтрі історії.";
    }

    /** SQLite + host selected, but no route_change rows in the lookback window. */
    public static String emptyHistory() {
        return "Поки немає змін маршруту за обраний період.";
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

package io.pingui.ui;

import javafx.scene.paint.Color;

/** Visual cues for {@link RouteDiff.Kind} in the route-diff ListView (P20-004). */
final class RouteDiffStyle {
    private RouteDiffStyle() {}

    /** Short ASCII marker before the summary line. */
    static String prefix(RouteDiff.Kind kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case UNCHANGED -> "= ";
            case CHANGED -> "~ ";
            case ADDED -> "+ ";
            case REMOVED -> "− ";
        };
    }

    /** Text fill for the cell; empty/null item uses black. */
    static Color textFill(RouteDiff.Kind kind) {
        if (kind == null) {
            return Color.BLACK;
        }
        return switch (kind) {
            case UNCHANGED -> Color.web("#6b7280"); // muted gray
            case CHANGED -> Color.web("#b45309"); // amber
            case ADDED -> Color.web("#15803d"); // green
            case REMOVED -> Color.web("#b91c1c"); // red
        };
    }

    /** Full cell text: prefix + summary. */
    static String cellText(RouteDiff.Row row) {
        if (row == null) {
            return null;
        }
        return prefix(row.kind()) + row.summary();
    }
}

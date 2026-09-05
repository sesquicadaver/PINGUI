package io.pingui.ui;

import io.pingui.i18n.UiI18n;
import io.pingui.monitor.EndpointState;
import io.pingui.monitor.RouteState;
import io.pingui.monitor.Severity;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

/**
 * Accessibility helpers for JavaFX chrome (P31-007): accessible names, state tooltips, and row
 * summaries so status is not color-only.
 */
public final class UiAccessibility {
    private UiAccessibility() {}

    /** Sets {@link Node#setAccessibleText(String)} when text is non-blank. */
    public static void name(Node node, String accessibleText) {
        if (node == null) {
            return;
        }
        if (accessibleText == null || accessibleText.isBlank()) {
            node.setAccessibleText(null);
        } else {
            node.setAccessibleText(accessibleText);
        }
    }

    /** Accessible name plus optional help (screen-reader hint). */
    public static void name(Node node, String accessibleText, String help) {
        name(node, accessibleText);
        if (node != null) {
            node.setAccessibleHelp(help != null && !help.isBlank() ? help : null);
        }
    }

    /** Tooltip with a text label (not glyph-only) for sighted users who need non-color cues. */
    public static void textTooltip(Control control, String text) {
        if (control == null) {
            return;
        }
        if (text == null || text.isBlank()) {
            control.setTooltip(null);
        } else {
            Tooltip tip = control.getTooltip();
            if (tip == null) {
                tip = new Tooltip(text);
                control.setTooltip(tip);
            } else {
                tip.setText(text);
            }
        }
    }

    /** Screen-reader / tooltip label for endpoint glyph. */
    public static String endpointName(boolean enabled, EndpointState state) {
        if (!enabled) {
            return UiI18n.get("a11y.endpoint.disabled");
        }
        return HostItem.formatEndpointLabel(state);
    }

    /** Screen-reader / tooltip label for route glyph. */
    public static String routeName(RouteState state) {
        return HostItem.formatRouteLabel(state);
    }

    /** One-line accessible summary for a host list row. */
    public static String hostRowSummary(HostItem item) {
        if (item == null) {
            return "";
        }
        return UiI18n.get(
                "a11y.host_row",
                item.getHost(),
                endpointName(item.isEnabled(), item.endpointState()),
                routeName(item.routeState()),
                SeverityTheme.label(item.severity()),
                nullToDash(item.rttColumnTextProperty().get()),
                nullToDash(item.lossColumnTextProperty().get()),
                nullToDash(item.modeColumnTextProperty().get()));
    }

    /** Accessible name for the problem badge button. */
    public static String problemBadgeName(Severity severity) {
        return UiI18n.get("a11y.problem_badge", SeverityTheme.label(severity));
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? UiI18n.get("host.ms_na") : value;
    }
}

package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.i18n.UiI18n;
import io.pingui.i18n.UiLocale;
import io.pingui.monitor.EndpointState;
import io.pingui.monitor.HostPollCounters;
import io.pingui.monitor.HostTargetStats;
import io.pingui.monitor.RouteState;
import io.pingui.monitor.Severity;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UiAccessibilityTest {
    @BeforeEach
    void ukLocale() {
        UiI18n.setLocale(UiLocale.UK);
    }

    @Test
    void endpointAndRouteNamesAreTextNotGlyphOnly() {
        assertEquals("UP", UiAccessibility.endpointName(true, EndpointState.UP));
        assertEquals("DOWN", UiAccessibility.endpointName(true, EndpointState.DOWN));
        assertEquals(UiI18n.get("a11y.endpoint.disabled"), UiAccessibility.endpointName(false, EndpointState.UP));
        assertEquals("STABLE", UiAccessibility.routeName(RouteState.STABLE));
        assertEquals("NOT TRACED", UiAccessibility.routeName(RouteState.NOT_TRACED));
    }

    @Test
    void hostRowSummaryIncludesHostAndStates() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.applyMetrics(new HostTargetStats(0.0, 10.0, 20.0, 30.0, false), new HostPollCounters(5, 0));
        String summary = UiAccessibility.hostRowSummary(item);
        assertTrue(summary.contains("8.8.8.8"));
        assertTrue(summary.contains("UP") || summary.contains("endpoint"));
        assertFalse(summary.equals(item.stateGlyphProperty().get()));
    }

    @Test
    void nameAndTooltipWireAccessibleText() throws Exception {
        FxTestSupport.runOnFxThread(() -> {
            Label label = new Label("●");
            UiAccessibility.name(label, "UP");
            UiAccessibility.textTooltip(label, "UP");
            assertEquals("UP", label.getAccessibleText());
            assertEquals("UP", label.getTooltip().getText());
        });
    }

    @Test
    void problemBadgeUsesSeverityLabel() {
        String name = UiAccessibility.problemBadgeName(Severity.CRITICAL);
        assertTrue(name.contains(SeverityTheme.label(Severity.CRITICAL)));
    }
}

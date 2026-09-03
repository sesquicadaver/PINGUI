package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostPollCounters;
import io.pingui.monitor.HostProbeMode;
import io.pingui.monitor.HostTargetStats;
import org.junit.jupiter.api.Test;

class HostItemMetricsTest {
    @Test
    void pollCountersUseRoleLabelsNotModeNames() {
        String text = HostItem.formatPollCounters(new HostPollCounters(12, 3));
        assertEquals("спроб 12  помилки 3  25%", text);
        assertFalse(text.toLowerCase().contains("ping"));
        assertFalse(text.toLowerCase().contains("trace"));
    }

    @Test
    void rttMetricsStayOnSeparateFormat() {
        HostTargetStats stats = new HostTargetStats(0.0, 10.0, 12.0, 15.0, false);
        assertEquals("loss 0%  min 10  avg 12  max 15 ms", HostItem.formatRttMetrics(stats));
    }

    @Test
    void modeLabelsAreShort() {
        assertEquals("PING", HostItem.formatModeLabel(HostProbeMode.PING_ONLY));
        assertEquals("TRACE", HostItem.formatModeLabel(HostProbeMode.TRACE));
        assertEquals("MTR", HostItem.formatModeLabel(HostProbeMode.MTR));
        assertEquals("TCP", HostItem.formatModeLabel(HostProbeMode.TCP_CONNECT));
    }

    @Test
    void columnFormattersUseFixedWidthTokens() {
        assertEquals("12", HostItem.formatRttColumn(12.4));
        assertEquals("—", HostItem.formatRttColumn(null));
        assertEquals("25%", HostItem.formatLossColumn(new HostTargetStats(25.0, 1.0, 2.0, 3.0, false)));
        assertEquals("—", HostItem.formatLossColumn(null));
    }

    @Test
    void stateGlyphReflectsAvailability() {
        assertEquals("○", HostItem.formatStateGlyph(false, null));
        assertEquals("◐", HostItem.formatStateGlyph(true, null));
        assertEquals("●", HostItem.formatStateGlyph(true, new HostTargetStats(0.0, 1.0, 12.0, 15.0, false)));
        assertEquals("✕", HostItem.formatStateGlyph(true, new HostTargetStats(100.0, null, null, null, true)));
    }

    @Test
    void applyMetricsUpdatesUnifiedColumnsAndTooltip() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.setProbeMode(HostProbeMode.TRACE);
        item.applyMetrics(new HostTargetStats(0.0, 10.0, 12.0, 15.0, false), new HostPollCounters(4, 1));
        assertTrue(item.showPollCountersProperty().get());
        assertTrue(item.showMetricsProperty().get());
        assertEquals("спроб 4  помилки 1  25%", item.pollCountersTextProperty().get());
        assertEquals(
                "loss 0%  min 10  avg 12  max 15 ms", item.metricsTextProperty().get());
        assertEquals("●", item.stateGlyphProperty().get());
        assertEquals("12", item.rttColumnTextProperty().get());
        assertEquals("0%", item.lossColumnTextProperty().get());
        assertEquals("TRACE", item.modeColumnTextProperty().get());
        assertEquals(io.pingui.monitor.EndpointState.UP, item.endpointState());
        assertEquals(io.pingui.monitor.RouteState.NOT_TRACED, item.routeState());
        assertEquals(io.pingui.monitor.Severity.INFO, item.severity());
        assertEquals("#e8f5e9", item.rowColorProperty().get());
        assertTrue(item.rowDetailsTooltipProperty().get().contains("Endpoint: UP"));
        assertTrue(item.rowDetailsTooltipProperty().get().contains("Route: NOT TRACED"));
        assertTrue(item.rowDetailsTooltipProperty().get().contains("Severity:"));
        assertTrue(item.rowDetailsTooltipProperty().get().contains("спроб 4"));
        assertTrue(item.rowDetailsTooltipProperty().get().contains("avg 12"));
    }

    @Test
    void applyMetricsShowsCountersWithoutRttYet() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.applyMetrics(null, new HostPollCounters(2, 0));
        assertTrue(item.showPollCountersProperty().get());
        assertFalse(item.showMetricsProperty().get());
        assertEquals("спроб 2  помилки 0  0%", item.pollCountersTextProperty().get());
        assertEquals("◐", item.stateGlyphProperty().get());
        assertEquals("—", item.rttColumnTextProperty().get());
    }

    @Test
    void applyMetricsClearsWhenNoCountersAndNoStats() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.applyMetrics(null, new HostPollCounters(1, 0));
        item.applyMetrics(null, HostPollCounters.ZERO);
        assertFalse(item.showPollCountersProperty().get());
        assertFalse(item.showMetricsProperty().get());
        assertEquals("", item.pollCountersTextProperty().get());
        assertEquals("", item.metricsTextProperty().get());
        assertEquals("—", item.rttColumnTextProperty().get());
    }

    @Test
    void rowDetailsTooltipIncludesTags() {
        HostItem item = new HostItem("8.8.8.8", true, false, java.util.List.of("dc", "vpn"));
        item.applyMetrics(new HostTargetStats(0.0, 10.0, 12.0, 15.0, false), HostPollCounters.ZERO);
        assertTrue(item.rowDetailsTooltipProperty().get().contains("dc, vpn"));
    }

    @Test
    void pingOnlyRouteIsNotTracedAndNotDownGlyph() {
        HostItem item = new HostItem("8.8.8.8", true, true);
        item.applyMetrics(new HostTargetStats(0.0, 10.0, 12.0, 15.0, false), HostPollCounters.ZERO);
        item.applyRouteHops(java.util.List.of(new io.pingui.model.Models.HopNode(1, "8.8.8.8", 12.0, false)));
        assertEquals(io.pingui.monitor.RouteState.NOT_TRACED, item.routeState());
        assertEquals("–", item.routeGlyphProperty().get());
        assertEquals("●", item.stateGlyphProperty().get());
        assertTrue(item.rowDetailsTooltipProperty().get().contains("NOT TRACED"));
        assertFalse(item.routeGlyphProperty().get().contains("✕"));
    }

    @Test
    void routeChangedLatchDoesNotOverrideIncomplete() {
        HostItem item = new HostItem("8.8.8.8", true);
        item.setProbeMode(HostProbeMode.TRACE);
        item.markRouteChanged();
        item.applyRouteHops(java.util.List.of(io.pingui.model.Models.timeout(1)));
        assertEquals(io.pingui.monitor.RouteState.INCOMPLETE, item.routeState());
        assertEquals("…", item.routeGlyphProperty().get());
    }
}

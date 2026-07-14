package io.pingui.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrometheusExporterTest {
    @Test
    void scrapeContainsAdrMetricsAndEscapesLabels() {
        PrometheusExporter exporter = new PrometheusExporter();
        exporter.recordRtt("host\"a", 1, 12.5);
        exporter.incrementRouteChange("8.8.8.8");
        exporter.incrementRouteChange("8.8.8.8");
        exporter.recordReachable("8.8.8.8", true);
        exporter.recordTraceDuration("8.8.8.8", "trace", 42.0);

        String text = exporter.scrape();
        assertTrue(text.contains("# TYPE pingui_rtt_ms gauge"));
        assertTrue(text.contains("pingui_rtt_ms{host=\"host\\\"a\",hop=\"1\"} 12.5"));
        assertTrue(text.contains("# TYPE pingui_route_change_total counter"));
        assertTrue(text.contains("pingui_route_change_total{host=\"8.8.8.8\"} 2"));
        assertTrue(text.contains("pingui_target_reachable{host=\"8.8.8.8\"} 1.0"));
        assertTrue(text.contains("pingui_trace_duration_ms{host=\"8.8.8.8\",probe_mode=\"trace\"} 42.0"));
    }

    @Test
    void clearHostRttDropsStaleHops() {
        PrometheusExporter exporter = new PrometheusExporter();
        exporter.recordRtt("h", 1, 1.0);
        exporter.recordRtt("h", 2, 2.0);
        exporter.clearHostRtt("h");
        exporter.recordRtt("h", 1, 3.0);
        String text = exporter.scrape();
        assertTrue(text.contains("hop=\"1\"} 3.0"));
        assertTrue(!text.contains("hop=\"2\""));
    }

    @Test
    void escapeLabelHandlesBackslashAndNewline() {
        assertEquals("a\\\\b\\nc", PrometheusExporter.escapeLabel("a\\b\nc"));
    }
}

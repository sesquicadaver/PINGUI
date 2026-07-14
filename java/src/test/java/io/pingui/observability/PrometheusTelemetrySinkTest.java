package io.pingui.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.telemetry.MetricNames;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrometheusTelemetrySinkTest {
    @Test
    void idAndNotEventsOnly() {
        PrometheusTelemetrySink sink = new PrometheusTelemetrySink(new PrometheusExporter());
        assertEquals(PrometheusTelemetrySink.ID, sink.id());
        assertFalse(sink.eventsOnly());
    }

    @Test
    void samplesAndEventsUpdateExporterScrape() {
        PrometheusExporter exporter = new PrometheusExporter();
        PrometheusTelemetrySink sink = new PrometheusTelemetrySink(exporter);
        Instant ts = Instant.parse("2026-07-14T08:00:00Z");
        MapLabels labels = MapLabels.of("lab", "trace");

        sink.onSample(new MetricSample(MetricNames.TARGET_REACHABLE, 1.0, "8.8.8.8", null, labels.map(), ts));
        sink.onSample(new MetricSample(MetricNames.TRACE_DURATION_MS, 42.0, "8.8.8.8", null, labels.map(), ts));
        sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 5.0, labels.map(), ts));
        sink.onSample(MetricSample.rttMs("8.8.8.8", 2, 10.0, labels.map(), ts));
        sink.onSample(new MetricSample(MetricNames.HOP_LOSS_PCT, 0.0, "8.8.8.8", 1, labels.map(), ts));
        sink.onEvent(TelemetryEvent.routeChange("8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), labels.map(), ts));
        sink.onEvent(TelemetryEvent.routeChange("8.8.8.8", List.of(), List.of("8.8.8.8"), labels.map(), ts));

        String text = exporter.scrape();
        assertTrue(text.contains("pingui_target_reachable{host=\"8.8.8.8\"} 1.0"));
        assertTrue(text.contains("pingui_rtt_ms{host=\"8.8.8.8\",hop=\"2\"} 10.0"));
        assertTrue(text.contains("pingui_trace_duration_ms{host=\"8.8.8.8\",probe_mode=\"trace\"} 42.0"));
        assertTrue(text.contains("pingui_route_change_total{host=\"8.8.8.8\"} 1"));
    }

    @Test
    void failureReachableDoesNotClearPriorRtt() {
        PrometheusExporter exporter = new PrometheusExporter();
        PrometheusTelemetrySink sink = new PrometheusTelemetrySink(exporter);
        Instant ts = Instant.parse("2026-07-14T08:00:00Z");
        MapLabels labels = MapLabels.of("lab", "trace");
        sink.onSample(new MetricSample(MetricNames.TARGET_REACHABLE, 1.0, "h", null, labels.map(), ts));
        sink.onSample(MetricSample.rttMs("h", 1, 3.0, labels.map(), ts));
        sink.onSample(new MetricSample(MetricNames.TRACE_DURATION_MS, 9.0, "h", null, labels.map(), ts));
        sink.onEvent(TelemetryEvent.probeError("h", "down", labels.map(), ts));
        String text = exporter.scrape();
        assertTrue(text.contains("pingui_target_reachable{host=\"h\"} 0.0"));
        assertTrue(text.contains("hop=\"1\"} 3.0"));
    }

    @Test
    void unreachableSuccessClearsStaleHops() {
        PrometheusExporter exporter = new PrometheusExporter();
        PrometheusTelemetrySink sink = new PrometheusTelemetrySink(exporter);
        Instant ts = Instant.parse("2026-07-14T08:00:00Z");
        MapLabels labels = MapLabels.of("lab", "trace");
        sink.onSample(new MetricSample(MetricNames.TARGET_REACHABLE, 1.0, "h", null, labels.map(), ts));
        sink.onSample(MetricSample.rttMs("h", 1, 1.0, labels.map(), ts));
        sink.onSample(MetricSample.rttMs("h", 2, 2.0, labels.map(), ts));
        sink.onSample(new MetricSample(MetricNames.TARGET_REACHABLE, 0.0, "h", null, labels.map(), ts));
        sink.onSample(MetricSample.rttMs("h", 1, 5.0, labels.map(), ts));
        String text = exporter.scrape();
        assertTrue(text.contains("hop=\"1\"} 5.0"));
        assertTrue(!text.contains("hop=\"2\""));
    }

    private record MapLabels(java.util.Map<String, String> map) {
        static MapLabels of(String profile, String probeMode) {
            return new MapLabels(MetricNames.javaLabels(profile, probeMode));
        }
    }
}

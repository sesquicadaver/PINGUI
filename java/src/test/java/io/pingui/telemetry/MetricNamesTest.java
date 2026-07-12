package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricNamesTest {
    @Test
    void canonicalNamesMatchPrometheusContract() {
        assertEquals("pingui_rtt_ms", MetricNames.RTT_MS);
        assertEquals("pingui_target_reachable", MetricNames.TARGET_REACHABLE);
        assertEquals("pingui_trace_duration_ms", MetricNames.TRACE_DURATION_MS);
        assertEquals("pingui_hop_loss_pct", MetricNames.HOP_LOSS_PCT);
        assertEquals("pingui_route_change_total", MetricNames.ROUTE_CHANGE_TOTAL);
    }

    @Test
    void javaLabelsIncludeProfileProbeModeAndEdition() {
        Map<String, String> labels = MetricNames.javaLabels("noc", "trace");
        assertEquals("noc", labels.get(MetricNames.LABEL_PROFILE));
        assertEquals("trace", labels.get(MetricNames.LABEL_PROBE_MODE));
        assertEquals(MetricNames.EDITION_JAVA, labels.get(MetricNames.LABEL_EDITION));
        assertTrue(labels.keySet()
                .containsAll(java.util.Set.of(
                        MetricNames.LABEL_EDITION, MetricNames.LABEL_PROBE_MODE, MetricNames.LABEL_PROFILE)));
    }

    @Test
    void blankProfileDefaults() {
        assertEquals(
                "default",
                MetricNames.labels(null, "mtr", MetricNames.EDITION_PYTHON).get(MetricNames.LABEL_PROFILE));
    }

    @Test
    void rejectsBlankProbeMode() {
        assertThrows(IllegalArgumentException.class, () -> MetricNames.labels("p", " ", MetricNames.EDITION_JAVA));
    }
}

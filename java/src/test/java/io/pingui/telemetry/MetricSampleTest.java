package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricSampleTest {
    private static final String PYTHON_SAMPLE =
            """
            {"kind":"sample","name":"pingui_rtt_ms","value":12.5,"host":"8.8.8.8","hop":3,\
            "labels":{"probe_mode":"traceroute","profile":"noc"},"timestamp":"2026-07-12T13:00:00Z"}
            """;

    @Test
    void roundTripJson() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("profile", "noc");
        labels.put("probe_mode", "traceroute");
        MetricSample original =
                MetricSample.rttMs("8.8.8.8", 3, 12.5, labels, Instant.parse("2026-07-12T13:00:00Z"));
        MetricSample parsed = MetricSample.fromJson(original.toJson());
        assertEquals(original, parsed);
        assertTrue(original.toJson().contains("\"kind\":\"sample\""));
        assertTrue(original.toJson().contains("\"labels\":{\"probe_mode\":\"traceroute\",\"profile\":\"noc\"}"));
    }

    @Test
    void parsesPythonContractSample() {
        MetricSample sample = MetricSample.fromJson(PYTHON_SAMPLE);
        assertEquals("pingui_rtt_ms", sample.name());
        assertEquals(12.5, sample.value());
        assertEquals("8.8.8.8", sample.host());
        assertEquals(3, sample.hop());
        assertEquals("noc", sample.labels().get("profile"));
        assertEquals(Instant.parse("2026-07-12T13:00:00Z"), sample.timestamp());
    }

    @Test
    void allowsNullHopForHostLevelGauge() {
        MetricSample sample = new MetricSample(
                "pingui_target_reachable",
                1.0,
                "1.1.1.1",
                null,
                Map.of("profile", "default"),
                Instant.parse("2026-07-12T13:00:00Z"));
        assertNull(MetricSample.fromJson(sample.toJson()).hop());
    }

    @Test
    void rejectsNonFiniteValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MetricSample(
                        "pingui_rtt_ms", Double.NaN, "x", 1, Map.of(), Instant.now()));
    }

    @Test
    void rejectsFractionalHopInJson() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MetricSample.fromJson(
                        "{\"kind\":\"sample\",\"name\":\"pingui_rtt_ms\",\"value\":1,\"host\":\"h\","
                                + "\"hop\":1.5,\"labels\":{},\"timestamp\":\"2026-07-12T13:00:00Z\"}"));
    }

    @Test
    void rejectsBlankHost() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MetricSample("pingui_rtt_ms", 1.0, " ", 1, Map.of(), Instant.now()));
    }

    @Test
    void rejectsInvalidHop() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MetricSample("pingui_rtt_ms", 1.0, "x", 0, Map.of(), Instant.now()));
    }
}

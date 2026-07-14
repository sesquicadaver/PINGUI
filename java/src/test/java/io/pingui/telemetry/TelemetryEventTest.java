package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelemetryEventTest {
    private static final String PYTHON_ROUTE_CHANGE =
            """
            {"kind":"event","event":"route_change","host":"8.8.8.8",\
            "labels":{"profile":"noc"},"message":null,\
            "old_ips":["10.0.0.1","192.168.1.1"],"new_ips":["10.0.0.1","8.8.8.8"],\
            "timestamp":"2026-07-12T13:00:00Z"}
            """;

    @Test
    void roundTripRouteChange() {
        TelemetryEvent original = TelemetryEvent.routeChange(
                "8.8.8.8",
                List.of("10.0.0.1"),
                List.of("8.8.8.8"),
                Map.of("profile", "noc"),
                Instant.parse("2026-07-12T13:00:00Z"));
        TelemetryEvent parsed = TelemetryEvent.fromJson(original.toJson());
        assertEquals(original, parsed);
        assertTrue(original.toJson().contains("\"kind\":\"event\""));
        assertTrue(original.toJson().contains("\"event\":\"route_change\""));
    }

    @Test
    void parsesPythonContractSample() {
        TelemetryEvent event = TelemetryEvent.fromJson(PYTHON_ROUTE_CHANGE);
        assertEquals(TelemetryEvent.ROUTE_CHANGE, event.event());
        assertEquals("8.8.8.8", event.host());
        assertEquals(List.of("10.0.0.1", "192.168.1.1"), event.oldIps());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), event.newIps());
        assertNull(event.message());
        assertEquals("noc", event.labels().get("profile"));
    }

    @Test
    void roundTripProbeError() {
        TelemetryEvent original = TelemetryEvent.probeError(
                "1.1.1.1", "timeout", Map.of("profile", "default"), Instant.parse("2026-07-12T13:00:00Z"));
        assertEquals(original, TelemetryEvent.fromJson(original.toJson()));
        assertEquals(TelemetryEvent.PROBE_ERROR, original.event());
    }

    @Test
    void collidingLabelKeysDoNotOverrideTopLevelFields() {
        TelemetryEvent event = TelemetryEvent.fromJson(
                """
                {"kind":"event","event":"probe_error","host":"h",\
                "labels":{"message":"from-label","timestamp":"from-label"},\
                "message":"real-message","old_ips":[],"new_ips":[],\
                "timestamp":"2026-07-12T13:00:00Z"}
                """);
        assertEquals("real-message", event.message());
        assertEquals(Instant.parse("2026-07-12T13:00:00Z"), event.timestamp());
        assertEquals("from-label", event.labels().get("message"));
    }

    @Test
    void rejectsBlankEvent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TelemetryEvent(" ", "h", Map.of(), null, List.of(), List.of(), Instant.now()));
    }

    @Test
    void rejectsSampleKind() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TelemetryEvent.fromJson(
                        "{\"kind\":\"sample\",\"event\":\"x\",\"host\":\"h\",\"timestamp\":\"2026-07-12T13:00:00Z\"}"));
    }
}

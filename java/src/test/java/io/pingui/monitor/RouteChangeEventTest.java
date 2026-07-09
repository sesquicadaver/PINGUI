package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteChangeEventTest {
    private static final String PYTHON_SAMPLE =
            """
            {
              "event": "route_change",
              "host": "8.8.8.8",
              "old_ips": ["10.0.0.1", "192.168.1.1"],
              "new_ips": ["10.0.0.1", "8.8.8.8"],
              "timestamp": "2026-07-09T07:30:00Z",
              "profile": "default"
            }
            """;

    @Test
    void roundTripJson() {
        RouteChangeEvent original = RouteChangeEvent.fromRouteChange(
                "8.8.8.8", List.of("10.0.0.1"), List.of("8.8.8.8"), "noc", Instant.parse("2026-07-09T07:30:00Z"));
        RouteChangeEvent parsed = RouteChangeEvent.fromJson(original.toJson());
        assertEquals(original.host(), parsed.host());
        assertEquals(original.oldIps(), parsed.oldIps());
        assertEquals(original.newIps(), parsed.newIps());
        assertEquals(original.timestamp(), parsed.timestamp());
        assertEquals(original.profile(), parsed.profile());
    }

    @Test
    void parsesPythonContractSample() {
        RouteChangeEvent event = RouteChangeEvent.fromJson(PYTHON_SAMPLE);
        assertEquals("8.8.8.8", event.host());
        assertEquals(List.of("10.0.0.1", "192.168.1.1"), event.oldIps());
        assertEquals(List.of("10.0.0.1", "8.8.8.8"), event.newIps());
        assertEquals("default", event.profile());
        assertEquals(Instant.parse("2026-07-09T07:30:00Z"), event.timestamp());
        assertTrue(event.toJson().contains("\"event\":\"route_change\""));
    }

    @Test
    void rejectsUnsupportedEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RouteChangeEvent.fromJson("{\"event\":\"probe_error\",\"host\":\"x\"}"));
    }
}

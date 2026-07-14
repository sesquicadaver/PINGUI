package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Shared golden events + field assertions for LOG sink contracts (P16-072 / SPIKE_LOG_SINKS).
 *
 * <p>Syslog MSG is {@link TelemetryEvent#toJson()}; GELF exposes the same semantics via structured
 * fields and embeds the canonical JSON in {@code _payload}.
 */
final class TelemetryLogFieldFixture {
    static final Instant TS = Instant.parse("2026-07-14T10:00:00Z");
    static final String HOST = "8.8.8.8";
    static final Map<String, String> LABELS = MetricNames.javaLabels("noc", "trace");

    private TelemetryLogFieldFixture() {}

    /** Canonical route_change event used by both syslog and GELF contracts. */
    static TelemetryEvent routeChange() {
        return TelemetryEvent.routeChange(HOST, List.of("10.0.0.1"), List.of("8.8.8.8"), LABELS, TS);
    }

    /** Canonical probe_error event used by both syslog and GELF contracts. */
    static TelemetryEvent probeError() {
        return TelemetryEvent.probeError(HOST, "timeout", LABELS, TS);
    }

    /** High-freq sample that must not appear on {@code events_only} remote LOG sinks. */
    static MetricSample droppedSample() {
        return MetricSample.rttMs(HOST, 1, 1.0, LABELS, TS);
    }

    /** Asserts canonical event JSON fields shared across sinks. */
    static void assertSharedEventFields(String eventJson) {
        TelemetryEvent parsed = TelemetryEvent.fromJson(eventJson);
        assertEquals(HOST, parsed.host());
        assertEquals(TS, parsed.timestamp());
        assertEquals("noc", parsed.labels().get(MetricNames.LABEL_PROFILE));
        assertEquals("trace", parsed.labels().get(MetricNames.LABEL_PROBE_MODE));
        assertTrue(
                TelemetryEvent.ROUTE_CHANGE.equals(parsed.event()) || TelemetryEvent.PROBE_ERROR.equals(parsed.event()),
                "unexpected event type: " + parsed.event());
        if (TelemetryEvent.ROUTE_CHANGE.equals(parsed.event())) {
            assertEquals(List.of("10.0.0.1"), parsed.oldIps());
            assertEquals(List.of("8.8.8.8"), parsed.newIps());
            assertNull(parsed.message());
        } else {
            assertEquals("timeout", parsed.message());
            assertTrue(parsed.oldIps().isEmpty());
            assertTrue(parsed.newIps().isEmpty());
        }
    }

    /** Asserts GELF envelope carries the same semantics as the shared JSON fixture. */
    static void assertGelfSharesEventFields(String gelfJson, TelemetryEvent expected) {
        assertTrue(gelfJson.contains("\"version\":\"1.1\""));
        assertTrue(gelfJson.contains("\"host\":\"" + expected.host() + "\""));
        assertTrue(gelfJson.contains("\"short_message\":\"" + expected.event() + "\""));
        assertTrue(gelfJson.contains("\"_event\":\"" + expected.event() + "\""));
        assertTrue(gelfJson.contains("\"_profile\":\"noc\""));
        assertTrue(gelfJson.contains("\"_probe_mode\":\"trace\""));
        if (TelemetryEvent.ROUTE_CHANGE.equals(expected.event())) {
            assertTrue(gelfJson.contains("\"_old_ips\":[\"10.0.0.1\"]"));
            assertTrue(gelfJson.contains("\"_new_ips\":[\"8.8.8.8\"]"));
        } else {
            assertTrue(gelfJson.contains("\"full_message\":\"timeout\""));
        }
        String payload = extractQuotedField(gelfJson, "_payload");
        assertEquals(expected.toJson(), payload);
        assertSharedEventFields(payload);
    }

    /** Extracts syslog MSG (canonical event JSON) after RFC 5424 header {@code - - - }. */
    static String syslogMsgJson(String rfc5424Line) {
        String marker = " - - - ";
        int idx = rfc5424Line.indexOf(marker);
        if (idx < 0) {
            throw new IllegalArgumentException("RFC 5424 line missing NILVALUE MSG header: " + rfc5424Line);
        }
        return rfc5424Line.substring(idx + marker.length());
    }

    private static String extractQuotedField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalArgumentException("missing field " + field);
        }
        int i = start + key.length();
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\' && i < json.length()) {
                char n = json.charAt(i++);
                if (n == 'u' && i + 3 < json.length()) {
                    String hex = json.substring(i, i + 4);
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                } else {
                    sb.append(
                            switch (n) {
                                case '"' -> '"';
                                case '\\' -> '\\';
                                case '/' -> '/';
                                case 'b' -> '\b';
                                case 'f' -> '\f';
                                case 'n' -> '\n';
                                case 'r' -> '\r';
                                case 't' -> '\t';
                                default -> n;
                            });
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlRotateSinkTest {
    @TempDir
    Path tempDir;

    @Test
    void idIsJsonl() {
        try (JsonlRotateSink sink = new JsonlRotateSink(tempDir)) {
            assertEquals(JsonlRotateSink.ID, sink.id());
        }
    }

    @Test
    void rejectsNonPositiveMaxBytes() {
        assertThrows(IllegalArgumentException.class, () -> new JsonlRotateSink(tempDir, 0L));
    }

    @Test
    void writesSampleAndEventAsJsonlLines() throws Exception {
        Instant ts = Instant.parse("2026-07-12T16:30:00Z");
        Clock clock = Clock.fixed(ts, ZoneOffset.UTC);
        Path expected = JsonlRotateSink.pathFor(tempDir.toAbsolutePath().normalize(), LocalDate.of(2026, 7, 12), 0);

        try (JsonlRotateSink sink = new JsonlRotateSink(tempDir, JsonlRotateSink.DEFAULT_MAX_BYTES, clock)) {
            Map<String, String> labels = MetricNames.javaLabels("noc", "trace");
            sink.onSample(MetricSample.rttMs("8.8.8.8", 1, 9.5, labels, ts));
            sink.onEvent(TelemetryEvent.probeError("8.8.8.8", "timeout", labels, ts));
            assertEquals(expected, sink.currentPath());
        }

        List<String> lines = Files.readAllLines(expected, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        MetricSample sample = MetricSample.fromJson(lines.get(0));
        assertEquals(MetricNames.RTT_MS, sample.name());
        assertEquals(9.5, sample.value());
        TelemetryEvent event = TelemetryEvent.fromJson(lines.get(1));
        assertEquals(TelemetryEvent.PROBE_ERROR, event.event());
        assertEquals("timeout", event.message());
    }

    @Test
    void rotatesOnUtcDayBoundary() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-12T23:59:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        Path day1 = JsonlRotateSink.pathFor(tempDir.toAbsolutePath().normalize(), LocalDate.of(2026, 7, 12), 0);
        Path day2 = JsonlRotateSink.pathFor(tempDir.toAbsolutePath().normalize(), LocalDate.of(2026, 7, 13), 0);

        try (JsonlRotateSink sink = new JsonlRotateSink(tempDir, JsonlRotateSink.DEFAULT_MAX_BYTES, clock)) {
            sink.onEvent(TelemetryEvent.probeError("1.1.1.1", "a", Map.of(), Instant.parse("2026-07-12T23:59:00Z")));
            assertEquals(day1, sink.currentPath());

            now.set(Instant.parse("2026-07-13T00:00:01Z"));
            sink.onEvent(TelemetryEvent.probeError("1.1.1.1", "b", Map.of(), Instant.parse("2026-07-13T00:00:01Z")));
            assertEquals(day2, sink.currentPath());
        }

        assertEquals(1, Files.readAllLines(day1, StandardCharsets.UTF_8).size());
        assertEquals(1, Files.readAllLines(day2, StandardCharsets.UTF_8).size());
    }

    @Test
    void rotatesOnMaxBytes() throws Exception {
        Instant ts = Instant.parse("2026-07-12T12:00:00Z");
        Clock clock = Clock.fixed(ts, ZoneOffset.UTC);
        Path dir = tempDir.toAbsolutePath().normalize();
        Path part0 = JsonlRotateSink.pathFor(dir, LocalDate.of(2026, 7, 12), 0);
        Path part1 = JsonlRotateSink.pathFor(dir, LocalDate.of(2026, 7, 12), 1);

        // Tiny limit forces second write into .1
        try (JsonlRotateSink sink = new JsonlRotateSink(tempDir, 80L, clock)) {
            sink.onEvent(TelemetryEvent.probeError("8.8.8.8", "one", Map.of("k", "v"), ts));
            assertEquals(part0, sink.currentPath());
            sink.onEvent(TelemetryEvent.probeError("8.8.8.8", "two", Map.of("k", "v"), ts));
            assertEquals(part1, sink.currentPath());
        }

        assertTrue(Files.exists(part0));
        assertTrue(Files.exists(part1));
        assertEquals(1, Files.readAllLines(part0, StandardCharsets.UTF_8).size());
        assertEquals(1, Files.readAllLines(part1, StandardCharsets.UTF_8).size());
        assertFalse(Files.readAllLines(part0, StandardCharsets.UTF_8).get(0).isBlank());
    }

    @Test
    void nullPayloadIsIgnored() {
        try (JsonlRotateSink sink = new JsonlRotateSink(tempDir, 1024L, Clock.systemUTC())) {
            sink.onSample(null);
            sink.onEvent(null);
            assertEquals(null, sink.currentPath());
        }
    }
}

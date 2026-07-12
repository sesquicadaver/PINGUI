package io.pingui.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.persistence.SessionDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryRetentionJobTest {
    @TempDir
    Path tempDir;

    @Test
    void purgesOldSqliteSamplesAndEvents() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC);
        Path dbPath = tempDir.resolve("ret.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            Map<String, String> labels = MetricNames.javaLabels("default", "trace");
            db.insertTelemetrySample(
                    MetricSample.rttMs("8.8.8.8", 1, 1.0, labels, Instant.parse("2026-06-01T00:00:00Z")));
            db.insertTelemetrySample(
                    MetricSample.rttMs("8.8.8.8", 1, 2.0, labels, Instant.parse("2026-07-10T00:00:00Z")));
            db.insertTelemetryEvent(
                    TelemetryEvent.probeError("8.8.8.8", "old", labels, Instant.parse("2026-05-01T00:00:00Z")));
            db.insertTelemetryEvent(
                    TelemetryEvent.probeError("8.8.8.8", "new", labels, Instant.parse("2026-07-11T00:00:00Z")));

            TelemetryRetentionJob.Result result = TelemetryRetentionJob.run(db, null, 30, clock);
            assertEquals(1, result.samplesDeleted());
            assertEquals(1, result.eventsDeleted());
            assertEquals(0, result.jsonlFilesDeleted());
            assertEquals(1, db.countTelemetrySamples());
            assertEquals(1, db.countTelemetryEvents());
            assertEquals("new", db.listTelemetryEvents("8.8.8.8", 10).get(0).message());
        }
    }

    @Test
    void purgesOldJsonlDayFiles() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC);
        Path dir = tempDir.resolve("jsonl");
        Files.createDirectories(dir);
        Path keep = dir.resolve("telemetry.jsonl.2026-07-05");
        Path drop = dir.resolve("telemetry.jsonl.2026-06-01");
        Path dropPart = dir.resolve("telemetry.jsonl.2026-05-15.1");
        Files.writeString(keep, "{}\n");
        Files.writeString(drop, "{}\n");
        Files.writeString(dropPart, "{}\n");

        TelemetryRetentionJob.Result result = TelemetryRetentionJob.run(null, dir, 10, clock);
        assertEquals(0, result.samplesDeleted());
        assertEquals(2, result.jsonlFilesDeleted());
        assertTrue(Files.exists(keep));
        assertFalse(Files.exists(drop));
        assertFalse(Files.exists(dropPart));
    }
}

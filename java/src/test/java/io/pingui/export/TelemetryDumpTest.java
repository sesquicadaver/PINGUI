package io.pingui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.persistence.SessionDatabase;
import io.pingui.telemetry.MetricNames;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryDumpTest {
    @TempDir
    Path tempDir;

    @Test
    void exportJsonContainsSamplesAndEvents() throws Exception {
        Path dbPath = tempDir.resolve("t.db");
        Path out = tempDir.resolve("dump.json");
        Instant ts = Instant.parse("2026-07-12T18:00:00Z");
        Map<String, String> labels = MetricNames.javaLabels("noc", "trace");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            db.insertTelemetrySample(MetricSample.rttMs("8.8.8.8", 1, 9.5, labels, ts));
            db.insertTelemetryEvent(TelemetryEvent.probeError("8.8.8.8", "timeout", labels, ts));
            TelemetryDump.export(db, out);
        }
        String json = Files.readString(out);
        assertTrue(json.startsWith("{\"samples\":["));
        assertTrue(json.contains("\"kind\":\"sample\""));
        assertTrue(json.contains("\"kind\":\"event\""));
        assertTrue(json.contains("probe_error"));
        assertTrue(json.contains(MetricNames.RTT_MS));
    }

    @Test
    void exportCsvHasHeaderAndRows() throws Exception {
        Path dbPath = tempDir.resolve("t2.db");
        Path out = tempDir.resolve("dump.csv");
        Instant ts = Instant.parse("2026-07-12T18:00:00Z");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            db.insertTelemetrySample(
                    MetricSample.rttMs("1.1.1.1", 2, 1.0, MetricNames.javaLabels("default", "mtr"), ts));
            TelemetryDump.export(db, out);
        }
        String csv = Files.readString(out);
        assertTrue(csv.startsWith("kind,name_or_event,value,host,hop,message,observed_at,payload_json\n"));
        assertTrue(csv.contains("sample,"));
        assertTrue(csv.contains("1.1.1.1"));
    }

    @Test
    void rejectsUnknownExtension() throws Exception {
        Path dbPath = tempDir.resolve("t3.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertThrows(IllegalArgumentException.class, () -> TelemetryDump.export(db, tempDir.resolve("out.txt")));
        }
    }

    @Test
    void emptyDatabaseWritesEmptyContainers() throws Exception {
        Path dbPath = tempDir.resolve("empty.db");
        Path out = tempDir.resolve("empty.json");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            TelemetryDump.export(db, out);
        }
        assertEquals("{\"samples\":[],\"events\":[]}", Files.readString(out));
    }
}

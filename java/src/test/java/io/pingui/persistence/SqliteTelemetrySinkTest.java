package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.telemetry.MetricNames;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteTelemetrySinkTest {
    @TempDir
    Path tempDir;

    @Test
    void freshDatabaseIsSchemaV4() {
        Path dbPath = tempDir.resolve("telemetry.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(4, db.schemaVersion());
            assertEquals(0, db.countTelemetrySamples());
            assertEquals(0, db.countTelemetryEvents());
        }
    }

    @Test
    void sinkPersistsSampleAndEventRoundtrip() {
        Path dbPath = tempDir.resolve("sink.db");
        Instant ts = Instant.parse("2026-07-12T16:00:00Z");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            SqliteTelemetrySink sink = new SqliteTelemetrySink(db);
            assertEquals(SqliteTelemetrySink.ID, sink.id());

            Map<String, String> labels = MetricNames.javaLabels("noc", "trace");
            MetricSample sample = MetricSample.rttMs("8.8.8.8", 2, 12.5, labels, ts);
            TelemetryEvent event = TelemetryEvent.probeError("8.8.8.8", "timeout", labels, ts);

            sink.onSample(sample);
            sink.onEvent(event);
            sink.close();

            assertEquals(1, db.countTelemetrySamples());
            assertEquals(1, db.countTelemetryEvents());

            List<MetricSample> samples = db.listTelemetrySamples("8.8.8.8", 10);
            assertEquals(1, samples.size());
            assertEquals(MetricNames.RTT_MS, samples.get(0).name());
            assertEquals(12.5, samples.get(0).value());
            assertEquals(2, samples.get(0).hop());
            assertEquals("noc", samples.get(0).labels().get(MetricNames.LABEL_PROFILE));

            List<TelemetryEvent> events = db.listTelemetryEvents("8.8.8.8", 10);
            assertEquals(1, events.size());
            assertEquals(TelemetryEvent.PROBE_ERROR, events.get(0).event());
            assertEquals("timeout", events.get(0).message());
        }
    }

    @Test
    void sampleWithoutHostSessionRowIsAllowed() {
        Path dbPath = tempDir.resolve("no-fk.db");
        try (SessionDatabase db = new SessionDatabase(dbPath);
                SqliteTelemetrySink sink = new SqliteTelemetrySink(db)) {
            sink.onSample(MetricSample.rttMs(
                    "1.1.1.1",
                    1,
                    3.0,
                    MetricNames.javaLabels("default", "mtr"),
                    Instant.parse("2026-07-12T16:01:00Z")));
            assertEquals(1, db.countTelemetrySamples());
            assertTrue(db.listHosts().isEmpty());
        }
    }
}

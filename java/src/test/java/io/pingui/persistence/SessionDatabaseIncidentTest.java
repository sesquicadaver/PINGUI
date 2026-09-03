package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.QualityAlertEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDatabaseIncidentTest {
    @TempDir
    Path tempDir;

    @Test
    void openResolveAndMttrWithoutJsonParse() {
        Path dbPath = tempDir.resolve("incident.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(SessionDatabase.SCHEMA_VERSION, db.schemaVersion());
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.8.8", data);

            Instant t0 = Instant.parse("2026-09-03T10:00:00Z");
            Instant t1 = Instant.parse("2026-09-03T10:05:00Z");
            db.openOrRefreshIncident(
                    "8.8.8.8", IncidentRecord.KIND_ENDPOINT_DOWN, IncidentRecord.SEVERITY_CRITICAL, t0, null, "{}");
            assertEquals(1, db.listActiveIncidents(10).size());
            db.openOrRefreshIncident(
                    "8.8.8.8",
                    IncidentRecord.KIND_ENDPOINT_DOWN,
                    IncidentRecord.SEVERITY_CRITICAL,
                    t0.plusSeconds(30),
                    null,
                    "{}");
            assertEquals(1, db.listIncidents("8.8.8.8", 10).size());
            assertEquals(2, db.listIncidents("8.8.8.8", 10).get(0).occurrences());

            assertTrue(db.resolveIncident("8.8.8.8", IncidentRecord.KIND_ENDPOINT_DOWN, t1));
            assertTrue(db.listActiveIncidents(10).isEmpty());
            IncidentRecord resolved = db.listIncidents("8.8.8.8", 10).get(0);
            assertEquals(IncidentRecord.STATE_RESOLVED, resolved.state());
            assertEquals(300, resolved.duration().orElseThrow().getSeconds());
            assertEquals(
                    300.0,
                    db.averageResolvedDurationSeconds(IncidentRecord.KIND_ENDPOINT_DOWN)
                            .orElseThrow());
        }
    }

    @Test
    void writerSyncsIncidentOnQualityAndAck() {
        Path dbPath = tempDir.resolve("writer-incident.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            Instant fire = Instant.parse("2026-09-03T11:00:00Z");
            Instant resolve = Instant.parse("2026-09-03T11:02:00Z");
            Instant ack = Instant.parse("2026-09-03T11:01:00Z");

            writer.writeQualityAlert(
                    QualityAlertEvent.endpointDownFiring("1.1.1.1", "noc", fire, Map.of("fail_after", 3)));
            assertEquals(1, database.listActiveIncidents(5).size());
            assertEquals(1, database.countEvents(PersistenceEventType.ENDPOINT_DOWN));

            writer.writeProblemAck("1.1.1.1", ack);
            IncidentRecord open = database.listActiveIncidents(5).get(0);
            assertEquals(ack, open.acknowledgedAt());

            writer.writeQualityAlert(
                    QualityAlertEvent.endpointDownResolved("1.1.1.1", "noc", resolve, Map.of("clear_after", 2)));
            assertTrue(database.listActiveIncidents(5).isEmpty());
            assertFalse(database.listIncidents("1.1.1.1", 5).get(0).active());
            assertEquals(
                    120.0,
                    database.averageResolvedDurationSeconds(IncidentRecord.KIND_ENDPOINT_DOWN)
                            .orElseThrow());
        }
    }

    @Test
    void latencyHighUsesWarningSeverity() {
        Path dbPath = tempDir.resolve("latency.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            writer.writeQualityAlert(QualityAlertEvent.latencyHighFiring(
                    "9.9.9.9", "noc", Instant.parse("2026-09-03T12:00:00Z"), Map.of("rtt_ms", 120.5)));
            List<IncidentRecord> rows = database.listIncidents("9.9.9.9", 5);
            assertEquals(1, rows.size());
            assertEquals(IncidentRecord.KIND_LATENCY_HIGH, rows.get(0).kind());
            assertEquals(IncidentRecord.SEVERITY_WARNING, rows.get(0).severity());
            assertEquals(120.5, rows.get(0).peakValue());
        }
    }
}

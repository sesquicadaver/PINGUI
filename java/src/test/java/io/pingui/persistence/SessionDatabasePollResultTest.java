package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HostSessionData;
import io.pingui.probe.ProbeOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDatabasePollResultTest {
    @TempDir
    Path tempDir;

    @Test
    void insertAndListPollResult() {
        Path dbPath = tempDir.resolve("poll.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(SessionDatabase.SCHEMA_VERSION, db.schemaVersion());
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.8.8", data);
            Instant at = Instant.parse("2026-09-03T13:00:00Z");
            long id = db.insertPollResult(
                    "8.8.8.8", at, "ping_only", true, 12.5, null, null, 40.0, null, null, ProbeOutcome.SUCCESS, true);
            assertTrue(id > 0);
            assertEquals(1, db.countPollResults());
            PollResultRecord row = db.listPollResults("8.8.8.8", 5).get(0);
            assertEquals("ping_only", row.probeMode());
            assertEquals(true, row.reachable());
            assertEquals(12.5, row.terminalRttMs());
            assertNull(row.lossPercent());
            assertEquals(40.0, row.durationMs());
            assertNull(row.routeId());
            assertNull(row.errorCode());
            assertEquals(ProbeOutcome.SUCCESS, row.probeOutcome());
            assertTrue(row.targetSampled());
        }
    }

    @Test
    void writerRecordsSuccessAndFailure() {
        Path dbPath = tempDir.resolve("writer-poll.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            writer.writePollResult(
                    "1.1.1.1",
                    "trace",
                    Instant.parse("2026-09-03T13:10:00Z"),
                    true,
                    8.0,
                    null,
                    null,
                    120.0,
                    null,
                    null,
                    ProbeOutcome.SUCCESS,
                    true);
            writer.writePollResult(
                    "1.1.1.1",
                    "trace",
                    Instant.parse("2026-09-03T13:11:00Z"),
                    false,
                    null,
                    null,
                    null,
                    50.0,
                    null,
                    "timeout",
                    ProbeOutcome.TIMEOUT,
                    true);
            List<PollResultRecord> rows = database.listPollResults("1.1.1.1", 10);
            assertEquals(2, rows.size());
            assertEquals("timeout", rows.get(0).errorCode());
            assertFalse(rows.get(0).reachable());
            assertEquals(ProbeOutcome.TIMEOUT, rows.get(0).probeOutcome());
            assertEquals(true, rows.get(1).reachable());
            assertEquals(ProbeOutcome.SUCCESS, rows.get(1).probeOutcome());
        }
    }
}

package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PollResultRecord;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PollResultEffectsPollResultTest {
    @TempDir
    Path tempDir;

    @Test
    void recordPollResultFromSnapshotAndError() {
        Path dbPath = tempDir.resolve("effects-poll.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(writer);

            RouteSnapshot snapshot = new RouteSnapshot(
                    "8.8.8.8",
                    "8.8.8.8",
                    List.of(new HopNode(1, "8.8.8.8", 9.0, false)),
                    Instant.parse("2026-09-03T13:20:00Z"));
            effects.recordPollResult("8.8.8.8", HostProbeMode.PING_ONLY, snapshot, 33.0, null);
            effects.recordPollResult("8.8.8.8", HostProbeMode.TRACE, null, 10.0, "no hops");

            List<PollResultRecord> rows = database.listPollResults("8.8.8.8", 10);
            assertEquals(2, rows.size());
            assertEquals("no hops", rows.get(0).errorCode());
            assertEquals(false, rows.get(0).reachable());
            assertEquals("ping_only", rows.get(1).probeMode());
            assertEquals(true, rows.get(1).reachable());
            assertEquals(9.0, rows.get(1).terminalRttMs());
            assertEquals(0.0, rows.get(1).lossPercent());
            assertNull(rows.get(1).jitterMs());
        }
    }
}

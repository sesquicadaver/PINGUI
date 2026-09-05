package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PollResultRecord;
import io.pingui.persistence.SessionDatabase;
import io.pingui.probe.ProbeOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PollResultEffectsPollResultTest {
    @TempDir
    Path tempDir;

    @Test
    void recordPollResultDoesNotInventLossAndStoresOutcome() {
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
            effects.recordPollResult(
                    "8.8.8.8", HostProbeMode.PING_ONLY, snapshot, 33.0, null, ProbeOutcome.SUCCESS, true);
            effects.recordPollResult(
                    "8.8.8.8", HostProbeMode.TRACE, null, 10.0, "no hops", ProbeOutcome.NETWORK_ERROR, true);

            List<PollResultRecord> rows = database.listPollResults("8.8.8.8", 10);
            assertEquals(2, rows.size());
            assertEquals("no hops", rows.get(0).errorCode());
            assertEquals(false, rows.get(0).reachable());
            assertEquals(ProbeOutcome.NETWORK_ERROR, rows.get(0).probeOutcome());
            assertNull(rows.get(0).lossPercent());
            assertEquals("ping_only", rows.get(1).probeMode());
            assertEquals(true, rows.get(1).reachable());
            assertEquals(9.0, rows.get(1).terminalRttMs());
            assertNull(rows.get(1).lossPercent());
            assertNull(rows.get(1).jitterMs());
            assertEquals(ProbeOutcome.SUCCESS, rows.get(1).probeOutcome());
            assertEquals(true, rows.get(1).targetSampled());
        }
    }

    @Test
    void recordPollResultUsesMeasuredJitterFromSeries() {
        Path dbPath = tempDir.resolve("effects-jitter.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(writer);
            effects.setMeasuredHopStatsResolver(host -> new HopStatsSummary(2.5, 10.0));

            RouteSnapshot snapshot =
                    new RouteSnapshot("1.1.1.1", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 8.0, false)));
            effects.recordPollResult(
                    "1.1.1.1", HostProbeMode.PING_ONLY, snapshot, 12.0, null, ProbeOutcome.SUCCESS, true);

            PollResultRecord row = database.listPollResults("1.1.1.1", 1).get(0);
            assertEquals(2.5, row.jitterMs());
            assertEquals(10.0, row.lossPercent());
        }
    }
}

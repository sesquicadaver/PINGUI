package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PollResultRecord;
import io.pingui.persistence.SessionDatabase;
import io.pingui.probe.ProbeOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompletedPollTest {
    @TempDir
    Path tempDir;

    @Test
    void terminalHopPrefersTargetIpMatch() {
        RouteSnapshot snapshot = new RouteSnapshot(
                "8.8.8.8",
                "8.8.8.8",
                List.of(new HopNode(1, "10.0.0.1", 4.0, false), new HopNode(2, "8.8.8.8", 8.0, false)));
        assertEquals(2, CompletedPoll.terminalHop(snapshot).hop());
        assertEquals("8.8.8.8", CompletedPoll.terminalHop(snapshot).ip());
    }

    @Test
    void successProjectsLossJitterWithoutMutatingPrior() {
        HopProbeStats prior = new HopProbeStats();
        prior.recordProbeAttempt();
        prior.recordProbeSuccess(5.0);
        prior.recordProbeAttempt();
        prior.recordProbeSuccess(7.0);
        int probesBefore = prior.getProbes();

        RouteSnapshot snapshot =
                new RouteSnapshot("1.1.1.1", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 9.0, false)));
        CompletedPoll poll = CompletedPoll.success(
                "1.1.1.1",
                HostProbeMode.PING_ONLY,
                snapshot,
                12.0,
                ProbeOutcome.SUCCESS,
                true,
                Instant.parse("2026-09-07T10:00:00Z"),
                prior,
                PollSampleScope.FULL,
                Map.of());

        assertEquals(probesBefore, prior.getProbes(), "prior must stay immutable");
        assertNotNull(poll.measuredTerminalStats());
        // Single success after a 2-probe prior window → loss is measured (0%).
        assertEquals(0.0, poll.measuredTerminalStats().lossPct());
        assertNotNull(poll.measuredTerminalStats().jitterMs());
    }

    @Test
    void singlePacketPollResultLossIsNull() {
        RouteSnapshot snapshot =
                new RouteSnapshot("1.1.1.1", "1.1.1.1", List.of(new HopNode(1, "1.1.1.1", 9.0, false)));
        CompletedPoll poll = CompletedPoll.success(
                "1.1.1.1",
                HostProbeMode.PING_ONLY,
                snapshot,
                12.0,
                ProbeOutcome.SUCCESS,
                true,
                Instant.parse("2026-09-07T10:00:00Z"),
                null,
                PollSampleScope.FULL,
                Map.of());
        assertNotNull(poll.measuredTerminalStats());
        assertNull(poll.measuredTerminalStats().lossPct());
        assertNull(poll.measuredTerminalStats().jitterMs());
    }

    @Test
    void recordCompletedPollDoesNotTouchSessionStore() throws Exception {
        Path dbPath = tempDir.resolve("completed-poll.db");
        AtomicInteger storeReads = new AtomicInteger();
        try (SessionDatabase database = new SessionDatabase(dbPath);
                SessionStore store = new SessionStore(List.of("8.8.8.8"), database)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(writer);
            effects.setMeasuredHopStatsResolver(host -> {
                storeReads.incrementAndGet();
                return store.hopStatsSummary(host, 1);
            });
            effects.setLastKnownHopIpsResolver(host -> {
                storeReads.incrementAndGet();
                return Map.of();
            });

            RouteSnapshot snapshot =
                    new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "8.8.8.8", 11.0, false)));
            HopStatsSummary measured =
                    HopStats.summarizeAfter(null, snapshot.nodes().get(0));
            CompletedPoll poll = new CompletedPoll(
                    "8.8.8.8",
                    HostProbeMode.PING_ONLY,
                    snapshot,
                    15.0,
                    null,
                    ProbeOutcome.SUCCESS,
                    true,
                    Instant.parse("2026-09-07T10:00:00Z"),
                    measured,
                    Map.of(1, "8.8.8.8"));

            effects.recordCompletedPoll(poll);
            // Mutate store after record — must not change already-written poll_result.
            store.applyPollSnapshot("8.8.8.8", snapshot, PollSampleScope.FULL, false);

            assertEquals(0, storeReads.get(), "CompletedPoll path must not read SessionStore resolvers");
            PollResultRecord row = database.listPollResults("8.8.8.8", 1).get(0);
            assertEquals(true, row.reachable());
            assertEquals(11.0, row.terminalRttMs());
            assertNull(row.lossPercent(), "single-packet loss must be null (P34-006)");
            assertNull(row.jitterMs());
            assertTrue(row.targetSampled());
        }
    }

    @Test
    void guiAndDaemonListenersShareSamePollResultWhenUsingCompletedPoll() throws Exception {
        Path dbPath = tempDir.resolve("parity.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(writer);

            RouteSnapshot snapshot = new RouteSnapshot(
                    "8.8.8.8",
                    "8.8.8.8",
                    List.of(new HopNode(1, "10.0.0.1", 4.0, false), new HopNode(2, "8.8.8.8", 9.0, false)),
                    Instant.parse("2026-09-07T11:00:00Z"));
            CompletedPoll poll = CompletedPoll.success(
                    "8.8.8.8",
                    HostProbeMode.TRACE,
                    snapshot,
                    40.0,
                    ProbeOutcome.SUCCESS,
                    true,
                    Instant.parse("2026-09-07T11:00:00Z"),
                    null,
                    PollSampleScope.FULL,
                    Map.of(1, "10.0.0.1"));

            // Simulate daemon then GUI store projection after the same CompletedPoll write.
            SessionStore daemonStore = new SessionStore(List.of("8.8.8.8"));
            SessionStore guiStore = new SessionStore(List.of("8.8.8.8"));
            effects.recordCompletedPoll(poll);
            daemonStore.applyPollSnapshot("8.8.8.8", snapshot, PollSampleScope.FULL, false);
            guiStore.applyPollSnapshot("8.8.8.8", snapshot, PollSampleScope.FULL, false);

            List<PollResultRecord> rows = database.listPollResults("8.8.8.8", 10);
            assertEquals(1, rows.size());
            assertEquals(9.0, rows.get(0).terminalRttMs());
            assertEquals(ProbeOutcome.SUCCESS, rows.get(0).probeOutcome());
            assertFalse(daemonStore.get("8.8.8.8").getCurrentRoute().isEmpty());
            assertFalse(guiStore.get("8.8.8.8").getCurrentRoute().isEmpty());
            daemonStore.close();
            guiStore.close();
        }
    }
}

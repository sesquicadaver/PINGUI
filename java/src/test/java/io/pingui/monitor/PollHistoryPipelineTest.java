package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.dns.DnsControlEvent;
import io.pingui.dns.DnsLookupOutcome;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventType;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PollResultRecord;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.SessionPersistenceWriter;
import io.pingui.probe.ProbeOutcome;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P35-007: CompletedPoll history via SessionPersistenceWriter control lane. */
class PollHistoryPipelineTest {
    @TempDir
    Path tempDir;

    @Test
    void offerPollHistoryWritesRouteAndPollResultInOneTransaction() throws Exception {
        Path dbPath = tempDir.resolve("poll-history.db");
        try (SessionDatabase database = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer = new SessionPersistenceWriter(database, null)) {
            PersistenceEventWriter events = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(events);
            effects.setSessionPersistenceWriter(writer);

            RouteSnapshot snapshot = new RouteSnapshot(
                    "host.example",
                    "9.9.9.9",
                    List.of(new HopNode(1, "10.0.0.1", 4.0, false), new HopNode(2, "9.9.9.9", 12.0, false)),
                    Instant.parse("2026-09-07T20:00:00Z"));
            CompletedPoll poll = new CompletedPoll(
                    "host.example",
                    HostProbeMode.TRACE,
                    snapshot,
                    33.0,
                    null,
                    ProbeOutcome.SUCCESS,
                    true,
                    Instant.parse("2026-09-07T20:00:00Z"),
                    new HopStatsSummary(null, null),
                    Map.of(1, "10.0.0.1", 2, "9.9.9.9"));

            effects.recordCompletedPoll(poll);
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));

            List<PollResultRecord> rows = database.listPollResults("host.example", 5);
            assertEquals(1, rows.size());
            PollResultRecord row = rows.get(0);
            assertEquals(true, row.reachable());
            assertEquals(12.0, row.terminalRttMs());
            assertNotNull(row.routeId());
            assertTrue(row.targetSampled());
            assertEquals(1, database.listRoutes("host.example", 10).size());
        }
    }

    @Test
    void failureBatchWritesProbeErrorAndNullReachablePollResult() throws Exception {
        Path dbPath = tempDir.resolve("poll-fail.db");
        try (SessionDatabase database = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer = new SessionPersistenceWriter(database, null)) {
            PersistenceEventWriter events = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(events);
            effects.setSessionPersistenceWriter(writer);

            CompletedPoll poll = CompletedPoll.failure(
                    "down.example",
                    HostProbeMode.TRACE,
                    50.0,
                    "timeout",
                    ProbeOutcome.TIMEOUT,
                    Instant.parse("2026-09-07T20:01:00Z"));
            effects.recordFailedPoll(poll, "timeout");
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));

            assertEquals(
                    1,
                    database.listEvents(PersistenceEventType.PROBE_ERROR, "down.example", Instant.EPOCH, 5)
                            .size());
            PollResultRecord row = database.listPollResults("down.example", 1).get(0);
            assertNull(row.reachable());
            assertEquals("timeout", row.errorCode());
            assertEquals(false, row.targetSampled());
        }
    }

    @Test
    void recordCompletedPollDoesNotBlockCallerOnSlowApplier() throws Exception {
        Path dbPath = tempDir.resolve("poll-async.db");
        AtomicInteger applyCount = new AtomicInteger();
        try (SessionDatabase database = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer = new SessionPersistenceWriter(database, null)) {
            PersistenceEventWriter events = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(events);
            effects.setSessionPersistenceWriter(writer);
            writer.setPollHistoryApplier(batch -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                applyCount.incrementAndGet();
                effects.applyPollBatchSync(batch);
            });

            CompletedPoll poll = CompletedPoll.failure(
                    "slow.example", HostProbeMode.MTR, 1.0, "x", ProbeOutcome.NETWORK_ERROR, Instant.now());
            long started = System.nanoTime();
            effects.recordCompletedPoll(poll);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            assertTrue(elapsedMs < 150, "enqueue must not wait for slow JDBC apply, took " + elapsedMs + "ms");
            assertEquals(0, applyCount.get());
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));
            assertEquals(1, applyCount.get());
            assertEquals(1, database.listPollResults("slow.example", 1).size());
        }
    }

    @Test
    void asyncSideEffectsUseControlLaneJdbc() throws Exception {
        Path dbPath = tempDir.resolve("poll-side.db");
        try (SessionDatabase database = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer = new SessionPersistenceWriter(database, null)) {
            PersistenceEventWriter events = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(events);
            effects.setSessionPersistenceWriter(writer);
            effects.setEndpointDownRule(new EndpointDownRuleConfig(true, 2, 1, 15));
            effects.setAlertDispatcher(AlertDispatcher.noop());

            RouteSnapshot down = new RouteSnapshot("q.example", "1.1.1.1", List.of(new HopNode(1, "*", null, true)));
            effects.evaluateEndpointDown("q.example", down);
            effects.evaluateEndpointDown("q.example", down);
            effects.dispatchRouteChangeAlert("q.example", List.of("1.1.1.1"), List.of("8.8.8.8"));
            effects.persistBaselineRouteChange("base.example", List.of("9.9.9.9"));

            MonitorService service =
                    new MonitorService(2.0, 20, 0.5, (h, m, t) -> new RouteSnapshot(h, "1.1.1.1", List.of()));
            service.setPersistenceEventWriter(events);
            service.setSessionPersistenceWriter(writer);
            service.applyDnsControlEvent(new DnsControlEvent(
                    "q.example",
                    "ok",
                    "resolved",
                    List.of(),
                    List.of("1.1.1.1"),
                    1L,
                    DnsLookupOutcome.OK,
                    Instant.parse("2026-09-07T20:02:00Z")));
            writer.offerJdbc(() -> events.writeProblemAck("q.example", Instant.now()));

            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));
            assertTrue(database.countEvents(PersistenceEventType.ROUTE_CHANGE) >= 1);
            assertTrue(database.countEvents(PersistenceEventType.ENDPOINT_DOWN) >= 1);
            assertTrue(database.countEvents(PersistenceEventType.DNS_CHANGE) >= 1);
            assertTrue(database.countEvents(PersistenceEventType.PROBLEM_ACK) >= 1);
            service.close();
        }
    }
}

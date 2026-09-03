package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.RouteChangeEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDatabaseTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundtrip() {
        Path dbPath = tempDir.resolve("session.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(SessionDatabase.SCHEMA_VERSION, db.schemaVersion());

            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            data.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
            data.setPreviousRoute(List.of(new HopNode(1, "192.168.1.1", 2.0, false)));
            data.getLastKnownByHop().put(2, new HopNode(2, "8.8.8.8", 10.0, false));
            data.getPingHistory().put("10.0.0.1", List.of(1.0, 2.0));
            HopProbeStats stats = new HopProbeStats();
            stats.recordProbeSuccess(3.5);
            data.getHopStats().put(1, stats);

            db.save("host.example", data);
            HostSessionData loaded = db.load("host.example");

            assertNotNull(loaded);
            assertTrue(loaded.isEnabled());
            assertEquals("10.0.0.1", loaded.getCurrentRoute().get(0).ip());
            assertEquals("192.168.1.1", loaded.getPreviousRoute().get(0).ip());
            assertEquals("8.8.8.8", loaded.getLastKnownByHop().get(2).ip());
            assertEquals(List.of(1.0, 2.0), loaded.getPingHistory().get("10.0.0.1"));
            assertEquals(1, loaded.getHopStats().get(1).getSuccesses());
        }
    }

    @Test
    void deleteAndRename() {
        Path dbPath = tempDir.resolve("session.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(false);
            db.save("old", data);
            db.rename("old", "new");

            assertNull(db.load("old"));
            assertNotNull(db.load("new"));

            db.delete("new");
            assertNull(db.load("new"));
        }
    }

    @Test
    void renamePreservesRouteChangeEvents() {
        Path dbPath = tempDir.resolve("rename-events.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("old", data);

            Instant when = Instant.parse("2026-07-09T10:00:00Z");
            db.insertRouteChange("old", "default", List.of("10.0.0.1"), List.of("192.168.1.1"), when);

            db.rename("old", "new");

            List<PersistenceEventRecord> rows =
                    db.listEvents(PersistenceEventType.ROUTE_CHANGE, "new", when.minusSeconds(60), 10);
            assertEquals(1, rows.size());
            assertEquals(
                    "new", RouteChangeEvent.fromJson(rows.get(0).payloadJson()).host());
            assertTrue(db.listEvents(PersistenceEventType.ROUTE_CHANGE, "old", when.minusSeconds(60), 10)
                    .isEmpty());
        }
    }

    @Test
    void renameKeepsStableHostId() {
        Path dbPath = tempDir.resolve("stable-id.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("old.example", data);
            long idBefore = db.hostId("old.example").orElseThrow();

            Instant when = Instant.parse("2026-09-03T12:00:00Z");
            db.insertRouteChange("old.example", "default", List.of("10.0.0.1"), List.of("8.8.8.8"), when);
            db.rename("old.example", "new.example");

            assertTrue(db.hostId("old.example").isEmpty());
            assertEquals(idBefore, db.hostId("new.example").orElseThrow());
            assertEquals(
                    1,
                    db.listEvents(PersistenceEventType.ROUTE_CHANGE, "new.example", when.minusSeconds(1), 5)
                            .size());
        }
    }

    @Test
    void insertAndPurgePersistenceEvent() {
        Path dbPath = tempDir.resolve("events.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.8.8", data);

            Instant when = Instant.parse("2026-07-09T09:00:00Z");
            db.insertRouteChange("8.8.8.8", "default", List.of("1.1.1.1"), List.of("9.9.9.9"), when);

            assertEquals(1, db.deleteEventsByType(PersistenceEventType.ROUTE_CHANGE));
            assertEquals(0, db.deleteEventsByType(PersistenceEventType.ROUTE_CHANGE));
        }
    }

    @Test
    void listRouteChangeEventsFiltersByHostAndTime() {
        Path dbPath = tempDir.resolve("list.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.8.8", data);
            db.save("1.1.1.1", data);

            Instant recent = Instant.parse("2026-07-09T10:00:00Z");
            Instant old = Instant.parse("2026-07-08T10:00:00Z");
            RouteChangeEvent recentEvent = RouteChangeEvent.fromRouteChange(
                    "8.8.8.8", List.of("10.0.0.1"), List.of("192.168.1.1"), "default", recent);
            db.insertRouteChange("8.8.8.8", "default", List.of("10.0.0.1"), List.of("192.168.1.1"), recent);
            db.insertRouteChange("8.8.8.8", "default", List.of("1.0.0.1"), List.of("2.0.0.1"), old);
            db.insertRouteChange("1.1.1.1", "default", List.of("9.9.9.9"), List.of("8.8.8.8"), recent);

            Instant since = Instant.parse("2026-07-09T00:00:00Z");
            List<PersistenceEventRecord> rows = db.listEvents(PersistenceEventType.ROUTE_CHANGE, "8.8.8.8", since, 10);
            assertEquals(1, rows.size());
            assertEquals(recent, rows.get(0).observedAt());
            assertEquals(recentEvent.toJson(), rows.get(0).payloadJson());
        }
    }

    @Test
    void listHostEventsReturnsMixedTypesNewestFirst() {
        Path dbPath = tempDir.resolve("host-events.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.8.8", data);
            Instant t1 = Instant.parse("2026-09-03T10:00:00Z");
            Instant t2 = Instant.parse("2026-09-03T10:01:00Z");
            db.insertRouteChange("8.8.8.8", "default", List.of("10.0.0.1"), List.of("8.8.8.8"), t1);
            db.insertEvent(
                    PersistenceEventType.ENDPOINT_DOWN, "8.8.8.8", "default", "firing", null, null, null, "{}", t2);
            List<PersistenceEventRecord> rows = db.listHostEvents("8.8.8.8", Instant.EPOCH, 10);
            assertEquals(2, rows.size());
            assertEquals(PersistenceEventType.ENDPOINT_DOWN, rows.get(0).eventType());
            assertEquals(PersistenceEventType.ROUTE_CHANGE, rows.get(1).eventType());
        }
    }

    @Test
    void codecMatchesPythonShape() {
        HopNode hop = new HopNode(1, "10.0.0.1", 5.0, false);
        String json = SessionJsonCodec.routeToJson(List.of(hop));
        assertTrue(json.contains("\"is_timeout\":false"));
        assertEquals("10.0.0.1", SessionJsonCodec.routeFromJson(json).get(0).ip());

        HopNode timeout = Models.timeout(2);
        String timeoutJson = SessionJsonCodec.routeToJson(List.of(timeout));
        assertTrue(timeoutJson.contains("\"is_timeout\":true"));
        assertTrue(SessionJsonCodec.routeFromJson(timeoutJson).get(0).timeout());

        Map<Integer, HopProbeStats> stats =
                SessionJsonCodec.hopStatsFromJson("{\"1\":{\"probes\":2,\"successes\":1,\"rtt_samples\":[4.0]}}");
        assertEquals(2, stats.get(1).getProbes());
        assertEquals(1, stats.get(1).getSuccesses());
        assertEquals(List.of(4.0), stats.get(1).getRttSamples());
    }

    @Test
    void loadMissingHostReturnsNull() {
        Path dbPath = tempDir.resolve("empty.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertNull(db.load("missing.example"));
        }
    }

    @Test
    void renameMissingHostIsNoOp() {
        Path dbPath = tempDir.resolve("rename.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            db.rename("missing", "also-missing");
            assertNull(db.load("also-missing"));
        }
    }

    @Test
    void listHostsReturnsSortedNames() {
        Path dbPath = tempDir.resolve("list-hosts.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("z.example", data);
            db.save("a.example", data);
            db.save("m.example", data);

            assertEquals(List.of("a.example", "m.example", "z.example"), db.listHosts());
        }
    }

    @Test
    void disabledHostRoundtrip() {
        Path dbPath = tempDir.resolve("disabled.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(false);
            db.save("host", data);
            HostSessionData loaded = db.load("host");
            assertNotNull(loaded);
            assertFalse(loaded.isEnabled());
        }
    }
}

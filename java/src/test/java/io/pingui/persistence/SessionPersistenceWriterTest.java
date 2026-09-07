package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HostSessionData;
import io.pingui.persistence.timeseries.MemoryTimeSeriesBackend;
import io.pingui.persistence.timeseries.PingSample;
import io.pingui.persistence.timeseries.RouteEvent;
import io.pingui.telemetry.DropPolicy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionPersistenceWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void savesHostOffCallerThread(@TempDir Path dir) throws Exception {
        Path dbPath = dir.resolve("async.db");
        try (SessionDatabase db = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer = new SessionPersistenceWriter(db, null)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            data.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 4.0, false)));
            assertTrue(writer.offerSave("8.8.8.8", data));
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));
            HostSessionData loaded = db.load("8.8.8.8");
            assertEquals("10.0.0.1", loaded.getCurrentRoute().get(0).ip());
        }
    }

    @Test
    void writesTimeSeriesAsync() throws Exception {
        MemoryTimeSeriesBackend backend = new MemoryTimeSeriesBackend();
        try (SessionPersistenceWriter writer = new SessionPersistenceWriter(null, backend)) {
            Instant now = Instant.parse("2026-09-05T12:00:00Z");
            writer.offerPingSamples(List.of(new PingSample("h", 1, "1.1.1.1", 5.0, now)));
            writer.offerRouteEvent(new RouteEvent("h", List.of("1.1.1.1"), false, now));
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));
            assertEquals(1, backend.pingSamples().size());
            assertEquals(1, backend.routeEvents().size());
        }
    }

    @Test
    void dropNewestIncrementsCounter() throws Exception {
        MemoryTimeSeriesBackend backend = new MemoryTimeSeriesBackend();
        try (SessionPersistenceWriter writer = new SessionPersistenceWriter(1, DropPolicy.DROP_NEWEST, null, backend)) {
            Instant now = Instant.now();
            writer.offerPingSamples(List.of(new PingSample("a", 1, "1.1.1.1", 1.0, now)));
            long drops = 0;
            for (int i = 0; i < 200; i++) {
                writer.offerPingSamples(List.of(new PingSample("b", 1, "2.2.2.2", 2.0, now)));
                drops = writer.droppedCount();
                if (drops > 0) {
                    break;
                }
            }
            assertTrue(drops > 0, "expected overflow drops under DROP_NEWEST");
            writer.awaitIdle(Duration.ofSeconds(5));
        }
    }

    @Test
    void telemetryOverflowDoesNotDropDelete() throws Exception {
        Path dbPath = tempDir.resolve("control-lane.db");
        MemoryTimeSeriesBackend backend = new MemoryTimeSeriesBackend();
        try (SessionDatabase db = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer =
                        new SessionPersistenceWriter(1, DropPolicy.DROP_OLDEST, db, backend)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("victim.example", data);
            assertNotNull(db.load("victim.example"));

            Instant now = Instant.now();
            long drops = 0;
            for (int i = 0; i < 500; i++) {
                writer.offerPingSamples(List.of(new PingSample("noise", 1, "1.1.1.1", 1.0, now)));
                drops = writer.droppedCount();
            }
            assertTrue(drops > 0, "telemetry lane must drop under overflow");

            assertTrue(writer.offerDelete("victim.example"));
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));
            assertNull(db.load("victim.example"), "delete must survive telemetry overflow");
        }
    }

    @Test
    void telemetryOverflowDoesNotDropRename() throws Exception {
        Path dbPath = tempDir.resolve("rename-lane.db");
        MemoryTimeSeriesBackend backend = new MemoryTimeSeriesBackend();
        try (SessionDatabase db = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer =
                        new SessionPersistenceWriter(1, DropPolicy.DROP_OLDEST, db, backend)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("old.example", data);
            assertNotNull(db.load("old.example"));

            Instant now = Instant.now();
            long drops = 0;
            for (int i = 0; i < 500; i++) {
                writer.offerPingSamples(List.of(new PingSample("noise", 1, "1.1.1.1", 1.0, now)));
                drops = writer.droppedCount();
            }
            assertTrue(drops > 0, "telemetry lane must drop under overflow");

            assertTrue(writer.offerRename("old.example", "new.example"));
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));
            assertNull(db.load("old.example"), "rename must clear old address under overflow");
            assertNotNull(db.load("new.example"), "rename must survive telemetry overflow");
        }
    }

    @Test
    void coalescesSaveHostToLatestSnapshot() throws Exception {
        Path dbPath = tempDir.resolve("coalesce.db");
        try (SessionDatabase db = new SessionDatabase(dbPath);
                SessionPersistenceWriter writer = new SessionPersistenceWriter(db, null)) {
            for (int i = 0; i < 50; i++) {
                HostSessionData mid = new HostSessionData();
                mid.setEnabled(true);
                mid.setCurrentRoute(List.of(new HopNode(1, "10.0.0." + (i % 200 + 1), 1.0, false)));
                writer.offerSave("host", mid);
            }
            HostSessionData last = new HostSessionData();
            last.setEnabled(true);
            last.setCurrentRoute(List.of(new HopNode(1, "9.9.9.9", 9.0, false)));
            writer.offerSave("host", last);
            assertTrue(writer.awaitIdle(Duration.ofSeconds(5)));
            HostSessionData loaded = db.load("host");
            assertEquals("9.9.9.9", loaded.getCurrentRoute().get(0).ip());
        }
    }

    @Test
    void closeStopsWorkerBeforeReturning() throws Exception {
        Path dbPath = tempDir.resolve("close.db");
        SessionDatabase db = new SessionDatabase(dbPath);
        SessionPersistenceWriter writer = new SessionPersistenceWriter(db, null);
        HostSessionData data = new HostSessionData();
        data.setEnabled(true);
        writer.offerSave("h", data);
        writer.close();
        assertFalse(writer.workerAliveForTests());
        assertNotNull(db.load("h"));
        db.close();
    }

    @Test
    void copySnapshotIsIndependentOfLaterMutation() {
        HostSessionData original = new HostSessionData();
        original.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 4.0, false)));
        HostSessionData copy = original.copy();
        original.setCurrentRoute(List.of(new HopNode(1, "9.9.9.9", 1.0, false)));
        assertEquals("10.0.0.1", copy.getCurrentRoute().get(0).ip());
        assertEquals("9.9.9.9", original.getCurrentRoute().get(0).ip());
    }
}

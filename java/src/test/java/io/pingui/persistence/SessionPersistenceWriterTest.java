package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            // Fill capacity with a barrier that will not be consumed until we stop... use ping jobs.
            // Block worker by flooding faster than process — with capacity 1, second offer drops.
            writer.offerPingSamples(List.of(new PingSample("a", 1, "1.1.1.1", 1.0, now)));
            // Keep offering until at least one drop (queue full + DROP_NEWEST).
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
    void copySnapshotIsIndependentOfLaterMutation() {
        HostSessionData original = new HostSessionData();
        original.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 4.0, false)));
        HostSessionData copy = original.copy();
        original.setCurrentRoute(List.of(new HopNode(1, "9.9.9.9", 1.0, false)));
        assertEquals("10.0.0.1", copy.getCurrentRoute().get(0).ip());
        assertEquals("9.9.9.9", original.getCurrentRoute().get(0).ip());
    }
}

package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionStorePersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void updateRoutePersistsAcrossReopen() {
        Path dbPath = tempDir.resolve("session.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            SessionStore store = new SessionStore(List.of("8.8.8.8"), db);
            RouteSnapshot snapshot =
                    new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 4.0, false)));
            store.updateRoute("8.8.8.8", snapshot);
            store.appendPingSamples("8.8.8.8", snapshot);
            store.close();
        }

        try (SessionDatabase db2 = new SessionDatabase(dbPath)) {
            SessionStore restored = new SessionStore(List.of("8.8.8.8"), db2);
            assertEquals(
                    "10.0.0.1", restored.get("8.8.8.8").getCurrentRoute().get(0).ip());
            assertEquals(List.of(4.0), restored.get("8.8.8.8").getPingHistory().get("10.0.0.1"));
            restored.close();
        }
    }

    @Test
    void hopStatsPersistAcrossReopen() {
        Path dbPath = tempDir.resolve("hop-stats.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            SessionStore store = new SessionStore(List.of("8.8.8.8"), db);
            RouteSnapshot snapshot = new RouteSnapshot(
                    "8.8.8.8",
                    "8.8.8.8",
                    List.of(new HopNode(1, "10.0.0.1", 5.0, false), io.pingui.model.Models.timeout(2)));
            store.appendPingSamples("8.8.8.8", snapshot);
            assertEquals(0.0, store.hopStatsSummary("8.8.8.8", 1).lossPct());
            assertEquals(100.0, store.hopStatsSummary("8.8.8.8", 2).lossPct());
            store.close();
        }

        try (SessionDatabase db2 = new SessionDatabase(dbPath)) {
            SessionStore restored = new SessionStore(List.of("8.8.8.8"), db2);
            assertEquals(0.0, restored.hopStatsSummary("8.8.8.8", 1).lossPct());
            assertEquals(100.0, restored.hopStatsSummary("8.8.8.8", 2).lossPct());
            restored.close();
        }
    }

    @Test
    void appendPingSamplesAfterDbReopen() {
        Path dbPath = tempDir.resolve("append-reopen.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            SessionStore store = new SessionStore(List.of("8.8.8.8"), db);
            RouteSnapshot snapshot =
                    new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 4.0, false)));
            store.appendPingSamples("8.8.8.8", snapshot);
            store.close();
        }

        try (SessionDatabase db2 = new SessionDatabase(dbPath)) {
            SessionStore restored = new SessionStore(List.of("8.8.8.8"), db2);
            RouteSnapshot next =
                    new RouteSnapshot("8.8.8.8", "8.8.8.8", List.of(new HopNode(1, "10.0.0.1", 6.0, false)));
            restored.appendPingSamples("8.8.8.8", next);
            assertEquals(
                    List.of(4.0, 6.0), restored.get("8.8.8.8").getPingHistory().get("10.0.0.1"));
            restored.close();
        }
    }

    @Test
    void removeHostDeletesRow() {
        Path dbPath = tempDir.resolve("delete.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            SessionStore store = new SessionStore(List.of("host"), db);
            store.setEnabled("host", true);
            store.removeHost("host");
            store.close();
        }

        try (SessionDatabase reopened = new SessionDatabase(dbPath)) {
            assertNull(reopened.load("host"));
        }
    }
}

package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P35-009: signature and hops_json share one stabilized hop-indexed chain. */
class RouteSignatureTest {
    @TempDir
    Path tempDir;

    @Test
    void fromHopsDoesNotInventHopsOutsideSnapshot() {
        assertEquals(
                "1=10.0.0.1",
                RouteSignature.fromHops(
                        List.of(new HopNode(1, "10.0.0.1", 1.0, false)),
                        Map.of(1, "10.0.0.1", 2, "10.0.0.2", 3, "8.8.8.8")));
    }

    @Test
    void stabilizeUnionsSnapshotAndLastKnown() {
        List<HopNode> shortSnap = List.of(new HopNode(1, "10.0.0.1", 1.0, false), Models.timeout(2));
        Map<Integer, String> known = Map.of(1, "10.0.0.1", 2, "10.0.0.2", 3, "8.8.8.8");
        List<HopNode> stabilized = RouteSignature.stabilize(shortSnap, known);
        assertEquals(3, stabilized.size());
        assertEquals("1=10.0.0.1|2=10.0.0.2|3=8.8.8.8", RouteSignature.fromHops(stabilized));
        String hopsJson = SessionJsonCodec.routeToJson(stabilized);
        assertTrue(hopsJson.contains("10.0.0.2"));
        assertTrue(hopsJson.contains("8.8.8.8"));
        assertFalse(hopsJson.contains("\"is_timeout\":true"));
    }

    @Test
    void observeRouteKeepsSignatureAlignedWithHopsJson() {
        Path dbPath = tempDir.resolve("sig-hops.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            db.ensureHostExists("8.8.8.8");
            PersistenceEventWriter writer = new PersistenceEventWriter(db);
            Map<Integer, String> known = Map.of(1, "10.0.0.1", 2, "10.0.0.2", 3, "8.8.8.8");
            Long id = writer.observeRoute(
                    "8.8.8.8",
                    List.of(new HopNode(1, "10.0.0.1", 1.0, false), Models.timeout(2)),
                    Instant.parse("2026-09-07T21:00:00Z"),
                    known);
            assertEquals(1L, id);
            RouteRecord row = db.listRoutes("8.8.8.8", 1).get(0);
            assertEquals("1=10.0.0.1|2=10.0.0.2|3=8.8.8.8", row.signature());
            assertEquals(RouteSignature.fromHops(SessionJsonCodec.routeFromJson(row.hopsJson())), row.signature());
            assertTrue(row.hopsJson().contains("8.8.8.8"));
        }
    }
}

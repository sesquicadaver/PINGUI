package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HostSessionData;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDatabaseRouteTest {
    @TempDir
    Path tempDir;

    @Test
    void signatureUsesStarForTimeouts() {
        assertEquals(
                "10.0.0.1|*|8.8.8.8",
                RouteSignature.fromHops(List.of(
                        new HopNode(1, "10.0.0.1", 1.0, false),
                        Models.timeout(2),
                        new HopNode(3, "8.8.8.8", 9.0, false))));
    }

    @Test
    void upsertDedupsAndIncrementsSeenCount() {
        Path dbPath = tempDir.resolve("route.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(SessionDatabase.SCHEMA_VERSION, db.schemaVersion());
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.8.8", data);
            Instant t0 = Instant.parse("2026-09-03T14:00:00Z");
            Instant t1 = Instant.parse("2026-09-03T14:05:00Z");
            String sig = "10.0.0.1|8.8.8.8";
            String hops = SessionJsonCodec.routeToJson(
                    List.of(new HopNode(1, "10.0.0.1", 1.0, false), new HopNode(2, "8.8.8.8", 8.0, false)));
            long id1 = db.upsertRoute("8.8.8.8", sig, hops, t0);
            long id2 = db.upsertRoute("8.8.8.8", sig, hops, t1);
            assertEquals(id1, id2);
            assertEquals(1, db.countRoutes());
            RouteRecord row = db.listRoutes("8.8.8.8", 5).get(0);
            assertEquals(2, row.seenCount());
            assertEquals(t0, row.firstSeen());
            assertEquals(t1, row.lastSeen());
            assertEquals(sig, row.signature());
        }
    }
}

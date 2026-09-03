package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PollResultRecord;
import io.pingui.persistence.RouteRecord;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PollResultEffectsRouteTest {
    @TempDir
    Path tempDir;

    @Test
    void recordPollResultLinksDedupedRouteId() {
        Path dbPath = tempDir.resolve("route-poll.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            PollResultEffects effects = new PollResultEffects(new AlertRuleEngine());
            effects.setPersistenceEventWriter(writer);

            RouteSnapshot snapshot = new RouteSnapshot(
                    "8.8.8.8",
                    "8.8.8.8",
                    List.of(new HopNode(1, "10.0.0.1", 1.0, false), new HopNode(2, "8.8.8.8", 9.0, false)),
                    Instant.parse("2026-09-03T14:10:00Z"));
            effects.recordPollResult("8.8.8.8", HostProbeMode.TRACE, snapshot, 55.0, null);
            effects.recordPollResult("8.8.8.8", HostProbeMode.TRACE, snapshot, 56.0, null);

            assertEquals(1, database.countRoutes());
            assertEquals(2, database.countPollResults());
            RouteRecord route = database.listRoutes("8.8.8.8", 1).get(0);
            assertEquals(2, route.seenCount());
            assertEquals("10.0.0.1|8.8.8.8", route.signature());
            PollResultRecord poll = database.listPollResults("8.8.8.8", 1).get(0);
            assertNotNull(poll.routeId());
            assertEquals(route.id(), poll.routeId());
            assertTrue(route.hopsJson().contains("8.8.8.8"));
        }
    }
}

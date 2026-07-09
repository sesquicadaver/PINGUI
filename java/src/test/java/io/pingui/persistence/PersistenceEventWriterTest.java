package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.RouteChangeEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceEventWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void probeErrorPayloadEscapesQuotes() {
        String json = PersistenceEventWriter.probeErrorPayload("h", "say \"hi\"");
        assertTrue(json.contains("\\\"hi\\\""));
        assertTrue(json.contains("\"host\":\"h\""));
    }

    @Test
    void skipsRouteChangeWhenPolicyDisabled() {
        Path dbPath = tempDir.resolve("policy.db");
        PersistencePolicyHolder holder = new PersistencePolicyHolder();
        holder.setPending(PersistencePolicy.of(false, true));
        holder.applyPendingAfterCycle();
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database, holder);
            writer.writeRouteChange(RouteChangeEvent.fromRouteChange(
                    "8.8.8.8", List.of("10.0.0.1"), List.of("192.168.1.1"), "office", Instant.now()));
            assertEquals(0, database.countEvents(PersistenceEventType.ROUTE_CHANGE));
        }
    }

    @Test
    void writesProbeErrorWhenPolicyAllows() {
        Path dbPath = tempDir.resolve("probe.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            writer.writeProbeError("host", "timeout");
            assertEquals(1, database.countEvents(PersistenceEventType.PROBE_ERROR));
        }
    }
}

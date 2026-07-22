package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.QualityAlertEvent;
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

    @Test
    void writesEndpointDownQualityAlert() {
        Path dbPath = tempDir.resolve("quality.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            PersistenceEventWriter writer = new PersistenceEventWriter(database);
            QualityAlertEvent firing = QualityAlertEvent.endpointDownFiring(
                    "8.8.8.8", "noc", Instant.parse("2026-07-22T12:00:00Z"), java.util.Map.of("fail_after", 3));
            writer.writeQualityAlert(firing);
            assertEquals(1, database.countEvents(PersistenceEventType.ENDPOINT_DOWN));
            assertTrue(database.listEvents(PersistenceEventType.ENDPOINT_DOWN, "8.8.8.8", Instant.EPOCH, 10)
                    .get(0)
                    .payloadJson()
                    .contains("\"state\":\"firing\""));
        }
    }
}

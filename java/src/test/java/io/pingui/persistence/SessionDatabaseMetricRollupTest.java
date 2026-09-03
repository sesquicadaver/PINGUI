package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pingui.model.Models.HostSessionData;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDatabaseMetricRollupTest {
    @TempDir
    Path tempDir;

    @Test
    void upsertMergesWeightedAverages() {
        Path dbPath = tempDir.resolve("rollup.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(SessionDatabase.SCHEMA_VERSION, db.schemaVersion());
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.4.4", data);
            long hostId = db.hostId("8.8.4.4").orElseThrow();
            Instant start = Instant.parse("2026-09-01T10:00:00Z");
            db.upsertMetricRollup(hostId, start, 300, 2, 1.0, 10.0, 12.0, 14.0, 0.0);
            db.upsertMetricRollup(hostId, start, 300, 2, 0.5, 8.0, 16.0, 20.0, 2.0);
            List<MetricRollupRecord> rows = db.listMetricRollups("8.8.4.4", 300, 5);
            assertEquals(1, rows.size());
            MetricRollupRecord row = rows.get(0);
            assertEquals(4, row.samples());
            assertEquals(0.75, row.uptimeRatio());
            assertEquals(8.0, row.rttMin());
            assertEquals(20.0, row.rttMax());
            assertEquals(14.0, row.rttAvg());
            assertEquals(1.0, row.lossAvg());
        }
    }
}

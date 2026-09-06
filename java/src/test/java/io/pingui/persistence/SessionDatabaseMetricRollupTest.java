package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HostSessionData;
import io.pingui.probe.ProbeOutcome;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionDatabaseMetricRollupTest {
    @TempDir
    Path tempDir;

    @Test
    void upsertMergesAdditiveCountersAndAveragesOnRead() {
        Path dbPath = tempDir.resolve("rollup.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(SessionDatabase.SCHEMA_VERSION, db.schemaVersion());
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("8.8.4.4", data);
            long hostId = db.hostId("8.8.4.4").orElseThrow();
            Instant start = Instant.parse("2026-09-01T10:00:00Z");
            // 2 samples, all reachable, RTT sum 24 (avg 12), loss sum 0
            db.upsertMetricRollup(hostId, start, 300, 2, 2, 2, 2, 24.0, 10.0, 14.0, 2, 0.0);
            // 2 samples, 50% up, RTT sum 32 (avg 16), loss sum 4
            db.upsertMetricRollup(hostId, start, 300, 2, 2, 1, 2, 32.0, 8.0, 20.0, 2, 4.0);
            List<MetricRollupRecord> rows = db.listMetricRollups("8.8.4.4", 300, 5);
            assertEquals(1, rows.size());
            MetricRollupRecord row = rows.get(0);
            assertEquals(4, row.samples());
            assertEquals(0.75, row.uptimeRatio());
            assertEquals(8.0, row.rttMin());
            assertEquals(20.0, row.rttMax());
            assertEquals(14.0, row.rttAvg());
            assertEquals(1.0, row.lossAvg());
            assertEquals(4, row.rttSamples());
            assertEquals(4, row.lossSamples());
        }
    }

    @Test
    void nullMetricsDoNotInflateAverages() {
        Path dbPath = tempDir.resolve("rollup-null.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            db.save("1.1.1.1", data);
            long hostId = db.hostId("1.1.1.1").orElseThrow();
            Instant start = Instant.parse("2026-09-01T11:00:00Z");
            // one poll with RTT only
            db.upsertMetricRollup(hostId, start, 300, 1, 1, 1, 1, 10.0, 10.0, 10.0, 0, 0.0);
            // one poll with loss only
            db.upsertMetricRollup(hostId, start, 300, 1, 1, 0, 0, 0.0, null, null, 1, 50.0);
            MetricRollupRecord row = db.listMetricRollups("1.1.1.1", 300, 1).get(0);
            assertEquals(2, row.sampleCount());
            assertEquals(10.0, row.rttAvg());
            assertEquals(1, row.rttSamples());
            assertEquals(50.0, row.lossAvg());
            assertEquals(1, row.lossSamples());
            assertEquals(0.5, row.uptimeRatio());
        }
    }

    @Test
    void migratesV13RollupToV14() throws Exception {
        Path dbPath = tempDir.resolve("v13.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE schema_meta (version INTEGER NOT NULL)");
            st.execute("INSERT INTO schema_meta(version) VALUES (13)");
            st.execute(
                    """
                    CREATE TABLE host_session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        address TEXT NOT NULL UNIQUE,
                        enabled INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            st.execute(
                    """
                    INSERT INTO host_session(address, enabled, created_at, updated_at)
                    VALUES ('8.8.8.8', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """);
            st.execute(
                    """
                    CREATE TABLE metric_rollup (
                        host_id INTEGER NOT NULL,
                        bucket_start TEXT NOT NULL,
                        bucket_size INTEGER NOT NULL,
                        samples INTEGER NOT NULL,
                        uptime_ratio REAL,
                        rtt_min REAL,
                        rtt_avg REAL,
                        rtt_max REAL,
                        loss_avg REAL,
                        PRIMARY KEY(host_id, bucket_start, bucket_size)
                    )
                    """);
            st.execute(
                    """
                    INSERT INTO metric_rollup(
                        host_id, bucket_start, bucket_size, samples,
                        uptime_ratio, rtt_min, rtt_avg, rtt_max, loss_avg)
                    VALUES (1, '2026-08-01T10:00:00Z', 300, 4, 0.75, 8.0, 14.0, 20.0, 1.0)
                    """);
            // Minimal poll_result v13 so create IF NOT EXISTS is happy later
            st.execute(
                    """
                    CREATE TABLE poll_result (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        host_id INTEGER NOT NULL,
                        observed_at TEXT NOT NULL,
                        probe_mode TEXT NOT NULL,
                        reachable INTEGER,
                        terminal_rtt_ms REAL,
                        jitter_ms REAL,
                        loss_percent REAL,
                        duration_ms REAL,
                        route_id INTEGER,
                        error_code TEXT,
                        probe_outcome TEXT NOT NULL,
                        target_sampled INTEGER NOT NULL
                    )
                    """);
        }
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(14, db.schemaVersion());
            MetricRollupRecord row = db.listMetricRollups("8.8.8.8", 300, 1).get(0);
            assertEquals(4, row.sampleCount());
            assertEquals(0.75, row.uptimeRatio());
            assertEquals(14.0, row.rttAvg());
            assertEquals(1.0, row.lossAvg());
            assertEquals(4, row.rttSamples());
            assertEquals(4, row.lossSamples());
        }
    }

    @Test
    void migratesV12PollResultAndRollupToV14() throws Exception {
        Path dbPath = tempDir.resolve("v12.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE schema_meta (version INTEGER NOT NULL)");
            st.execute("INSERT INTO schema_meta(version) VALUES (12)");
            st.execute(
                    """
                    CREATE TABLE host_session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        address TEXT NOT NULL UNIQUE,
                        enabled INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            st.execute(
                    """
                    INSERT INTO host_session(address, enabled, created_at, updated_at)
                    VALUES ('8.8.8.8', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """);
            st.execute(
                    """
                    CREATE TABLE poll_result (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        host_id INTEGER NOT NULL,
                        observed_at TEXT NOT NULL,
                        probe_mode TEXT NOT NULL,
                        reachable INTEGER,
                        terminal_rtt_ms REAL,
                        jitter_ms REAL,
                        loss_percent REAL,
                        duration_ms REAL,
                        route_id INTEGER,
                        error_code TEXT,
                        FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                    )
                    """);
            st.execute(
                    """
                    INSERT INTO poll_result(
                        host_id, observed_at, probe_mode, reachable, terminal_rtt_ms,
                        jitter_ms, loss_percent, duration_ms, route_id, error_code)
                    VALUES
                        (1, '2026-08-01T10:00:00Z', 'ping_only', 1, 12.0, NULL, 0.0, 40.0, NULL, NULL),
                        (1, '2026-08-01T10:01:00Z', 'ping_only', 0, NULL, NULL, NULL, 40.0, NULL, NULL),
                        (1, '2026-08-01T10:02:00Z', 'ping_only', 0, NULL, NULL, NULL, 40.0, NULL, 'dns')
                    """);
            st.execute(
                    """
                    CREATE TABLE metric_rollup (
                        host_id INTEGER NOT NULL,
                        bucket_start TEXT NOT NULL,
                        bucket_size INTEGER NOT NULL,
                        samples INTEGER NOT NULL,
                        uptime_ratio REAL,
                        rtt_min REAL,
                        rtt_avg REAL,
                        rtt_max REAL,
                        loss_avg REAL,
                        PRIMARY KEY(host_id, bucket_start, bucket_size)
                    )
                    """);
            st.execute(
                    """
                    INSERT INTO metric_rollup(
                        host_id, bucket_start, bucket_size, samples,
                        uptime_ratio, rtt_min, rtt_avg, rtt_max, loss_avg)
                    VALUES (1, '2026-08-01T10:00:00Z', 300, 4, 0.75, 8.0, 14.0, 20.0, 1.0)
                    """);
        }
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(14, db.schemaVersion());
            List<PollResultRecord> polls = db.listPollResults("8.8.8.8", 10);
            assertEquals(3, polls.size());
            // listPollResults is newest-first
            assertEquals(ProbeOutcome.NETWORK_ERROR, polls.get(0).probeOutcome());
            assertEquals(ProbeOutcome.TIMEOUT, polls.get(1).probeOutcome());
            assertEquals(ProbeOutcome.SUCCESS, polls.get(2).probeOutcome());
            assertTrue(polls.get(2).targetSampled());
            MetricRollupRecord row = db.listMetricRollups("8.8.8.8", 300, 1).get(0);
            assertEquals(4, row.sampleCount());
            assertEquals(0.75, row.uptimeRatio());
            assertEquals(14.0, row.rttAvg());
            assertEquals(1.0, row.lossAvg());
            assertEquals(4, row.rttSamples());
            assertEquals(4, row.lossSamples());
        }
    }

    @Test
    void rejectsSchemaOlderThanV12() throws Exception {
        Path dbPath = tempDir.resolve("v11.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE schema_meta (version INTEGER NOT NULL)");
            st.execute("INSERT INTO schema_meta(version) VALUES (11)");
        }
        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("11"));
    }
}

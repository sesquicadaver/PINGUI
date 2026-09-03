package io.pingui.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.export.SessionReportExporter;
import io.pingui.model.Models.HostSessionData;
import io.pingui.telemetry.MetricSample;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P26-003 / P27-001: reopen/corrupt failure coverage, legacy schema rejection, concurrent export.
 *
 * <p>Complements {@link SessionDatabaseTest} (happy path) and
 * {@link io.pingui.monitor.SessionStorePersistenceTest} (append-after-reopen).
 */
class SessionDatabaseHardeningTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsLegacyV1DatabaseWithoutMigration() throws Exception {
        Path dbPath = tempDir.resolve("legacy-v1.db");
        seedLegacyV1Database(dbPath, "legacy.example");

        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("Unsupported session DB schema version"));
        assertTrue(ex.getMessage().contains("required " + SessionDatabase.SCHEMA_VERSION));
    }

    @Test
    void rejectsLegacyV3DatabaseWithoutMigration() throws Exception {
        Path dbPath = tempDir.resolve("legacy-v3.db");
        seedLegacyV3Database(dbPath, "v3.example");

        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("Unsupported session DB schema version"));
    }

    @Test
    void rejectsLegacyV7DatabaseWithoutMigration() throws Exception {
        Path dbPath = tempDir.resolve("legacy-v7.db");
        seedLegacyV7Database(dbPath, "v7.example");

        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("Unsupported session DB schema version"));
        assertTrue(ex.getMessage().contains("required " + SessionDatabase.SCHEMA_VERSION));
    }

    @Test
    void rejectsLegacyV8DatabaseWithoutMigration() throws Exception {
        Path dbPath = tempDir.resolve("legacy-v8.db");
        seedLegacyV8Database(dbPath, "v8.example");

        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("Unsupported session DB schema version"));
        assertTrue(ex.getMessage().contains("required " + SessionDatabase.SCHEMA_VERSION));
    }

    @Test
    void rejectsLegacyV9DatabaseWithoutMigration() throws Exception {
        Path dbPath = tempDir.resolve("legacy-v9.db");
        seedLegacyV9Database(dbPath, "v9.example");

        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("Unsupported session DB schema version"));
        assertTrue(ex.getMessage().contains("required " + SessionDatabase.SCHEMA_VERSION));
    }

    @Test
    void freshDatabaseAcceptsTelemetryInsert() {
        Path dbPath = tempDir.resolve("fresh-v5.db");
        try (SessionDatabase db = new SessionDatabase(dbPath)) {
            assertEquals(SessionDatabase.SCHEMA_VERSION, db.schemaVersion());
            MetricSample sample = new MetricSample(
                    "rtt_ms", 12.5, "fresh.example", 1, Map.of(), Instant.parse("2026-08-05T12:00:00Z"));
            db.insertTelemetrySample(sample);
            assertEquals(1, db.countTelemetrySamples());
            assertEquals(0, db.countTelemetryEvents());
            assertEquals(
                    12.5, db.listTelemetrySamples("fresh.example", 1).get(0).value());
        }
    }

    @Test
    void corruptDatabaseFileFailsOpenWithPersistenceException() throws Exception {
        Path dbPath = tempDir.resolve("corrupt.db");
        Files.writeString(dbPath, "this is not a sqlite database", StandardCharsets.UTF_8);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("Failed to open session database"));
    }

    @Test
    void truncatedSqliteHeaderFailsOpenWithPersistenceException() throws Exception {
        Path dbPath = tempDir.resolve("truncated.db");
        // Valid magic prefix but truncated body → SQLite rejects as malformed/incomplete.
        byte[] partial = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
        Files.write(dbPath, partial);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> new SessionDatabase(dbPath));
        assertTrue(ex.getMessage().contains("Failed to open session database"));
    }

    /**
     * Smoke only: synchronized SessionDatabase serializes DB access; this checks no-throw under
     * concurrent callers, not SQLite WAL concurrency or export snapshot atomicity.
     */
    @Test
    void concurrentExportAndSaveDoNotThrow() throws Exception {
        Path dbPath = tempDir.resolve("concurrent.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            HostSessionData seed = new HostSessionData();
            seed.setEnabled(true);
            database.save("export-host", seed);

            int writers = 4;
            int exporters = 4;
            int tasks = writers + exporters;
            ExecutorService pool = Executors.newFixedThreadPool(tasks);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger exportIndex = new AtomicInteger();
            List<Future<Void>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < writers; i++) {
                    int idx = i;
                    futures.add(pool.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        HostSessionData data = new HostSessionData();
                        data.setEnabled(true);
                        database.save("w-" + idx, data);
                        return null;
                    }));
                }
                for (int i = 0; i < exporters; i++) {
                    futures.add(pool.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        Path out = tempDir.resolve("smoke-" + exportIndex.getAndIncrement() + ".csv");
                        SessionReportExporter.exportCsv(database, out);
                        assertTrue(Files.size(out) > 0);
                        return null;
                    }));
                }
                start.countDown();
                for (Future<Void> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdown();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
            assertTrue(database.listHosts().size() >= 1 + writers);
        }
    }

    /** Pre-v2 shape: no {@code hop_stats_json}, schema_meta = 1. */
    private static void seedLegacyV1Database(Path dbPath, String host) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE host_session (
                        host TEXT PRIMARY KEY,
                        enabled INTEGER NOT NULL,
                        current_route_json TEXT NOT NULL,
                        previous_route_json TEXT NOT NULL,
                        last_known_json TEXT NOT NULL,
                        ping_history_json TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(version) VALUES (1)");
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO host_session(
                        host, enabled, current_route_json, previous_route_json,
                        last_known_json, ping_history_json, updated_at
                    ) VALUES (?, 1, '[]', '[]', '{}', '{}', ?)
                    """)) {
                ps.setString(1, host);
                ps.setString(2, Instant.parse("2026-01-01T00:00:00Z").toString());
                ps.executeUpdate();
            }
        }
    }

    /** v3 shape: hop_stats + persistence_event, no telemetry tables, schema_meta = 3. */
    private static void seedLegacyV3Database(Path dbPath, String host) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE host_session (
                        host TEXT PRIMARY KEY,
                        enabled INTEGER NOT NULL,
                        current_route_json TEXT NOT NULL,
                        previous_route_json TEXT NOT NULL,
                        last_known_json TEXT NOT NULL,
                        ping_history_json TEXT NOT NULL,
                        hop_stats_json TEXT NOT NULL DEFAULT '{}',
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE persistence_event (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_type TEXT NOT NULL,
                        host TEXT NOT NULL,
                        profile TEXT,
                        payload_json TEXT NOT NULL,
                        observed_at TEXT NOT NULL,
                        FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(version) VALUES (3)");
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO host_session(
                        host, enabled, current_route_json, previous_route_json,
                        last_known_json, ping_history_json, hop_stats_json, updated_at
                    ) VALUES (?, 1, '[]', '[]', '{}', '{}', '{}', ?)
                    """)) {
                ps.setString(1, host);
                ps.setString(2, Instant.parse("2026-06-01T00:00:00Z").toString());
                ps.executeUpdate();
            }
        }
    }

    /** v7 shape: address as TEXT PK + child tables keyed by host text, schema_meta = 7. */
    private static void seedLegacyV7Database(Path dbPath, String host) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE host_session (
                        host TEXT PRIMARY KEY,
                        enabled INTEGER NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE session_route_hop (
                        host TEXT NOT NULL,
                        route_kind TEXT NOT NULL,
                        hop INTEGER NOT NULL,
                        ip TEXT,
                        ping_ms REAL,
                        is_timeout INTEGER NOT NULL,
                        PRIMARY KEY (host, route_kind, hop)
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(version) VALUES (7)");
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO host_session(host, enabled, updated_at) VALUES (?, 1, ?)
                    """)) {
                ps.setString(1, host);
                ps.setString(2, Instant.parse("2026-08-01T00:00:00Z").toString());
                ps.executeUpdate();
            }
        }
    }

    /** v8 shape: stable host id, no incident table, schema_meta = 8. */
    private static void seedLegacyV8Database(Path dbPath, String host) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE host_session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        address TEXT NOT NULL UNIQUE,
                        enabled INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(version) VALUES (8)");
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO host_session(address, enabled, created_at, updated_at)
                    VALUES (?, 1, ?, ?)
                    """)) {
                String at = Instant.parse("2026-09-01T00:00:00Z").toString();
                ps.setString(1, host);
                ps.setString(2, at);
                ps.setString(3, at);
                ps.executeUpdate();
            }
        }
    }

    /** v9 shape: host id + incident, no poll_result, schema_meta = 9. */
    private static void seedLegacyV9Database(Path dbPath, String host) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE host_session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        address TEXT NOT NULL UNIQUE,
                        enabled INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE incident (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        host_id INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        state TEXT NOT NULL,
                        started_at TEXT NOT NULL,
                        ended_at TEXT,
                        acknowledged_at TEXT,
                        occurrences INTEGER NOT NULL DEFAULT 1,
                        peak_value REAL,
                        details_json TEXT NOT NULL DEFAULT '{}'
                    )
                    """);
            statement.execute("INSERT INTO schema_meta(version) VALUES (9)");
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO host_session(address, enabled, created_at, updated_at)
                    VALUES (?, 1, ?, ?)
                    """)) {
                String at = Instant.parse("2026-09-03T00:00:00Z").toString();
                ps.setString(1, host);
                ps.setString(2, at);
                ps.setString(3, at);
                ps.executeUpdate();
            }
        }
    }
}

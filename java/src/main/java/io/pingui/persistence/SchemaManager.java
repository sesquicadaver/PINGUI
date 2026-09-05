package io.pingui.persistence;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages DDL setup and schema migration for the session SQLite database.
 *
 * <p>Handles {@code CREATE TABLE IF NOT EXISTS}, version seeding, read-only version assertion,
 * and the in-place v13 → v14 migration. Package-private — all external access goes through
 * {@link SessionDatabase}.
 */
final class SchemaManager {

    /** Current Java session DB schema (v14 = accurate metric_rollup + atomic retention, P32-004). */
    static final int SCHEMA_VERSION = 14;

    /** Minimum version that can be migrated forward (v13 has probe_outcome). */
    static final int MIN_MIGRATE_FROM = 13;

    private final DbCommit commit;
    private final Path path;
    private final SessionDatabase.OpenMode openMode;

    SchemaManager(DbCommit commit, Path path, SessionDatabase.OpenMode openMode) {
        this.commit = commit;
        this.path = path;
        this.openMode = openMode;
    }

    /**
     * Reads the schema version from {@code schema_meta}. Returns {@link #SCHEMA_VERSION} when
     * the table is empty (fresh DB between table creation and version seeding).
     */
    int schemaVersion() throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement("SELECT version FROM schema_meta LIMIT 1");
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return SCHEMA_VERSION;
            }
            return rs.getInt(1);
        }
    }

    /**
     * Initialises or migrates the schema. Called from the {@link SessionDatabase} constructor;
     * throws {@link SQLException} (caught and wrapped by the caller).
     */
    void initSchema() throws SQLException {
        if (openMode == SessionDatabase.OpenMode.READ_ONLY) {
            initSchemaReadOnly(readSchemaVersionOrNull());
            return;
        }
        try (Statement statement = commit.connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
        }
        Integer existingVersion = readSchemaVersionOrNull();
        if (existingVersion != null && existingVersion > SCHEMA_VERSION) {
            throw new PersistenceException("Unsupported session DB schema version "
                    + existingVersion
                    + " (required "
                    + SCHEMA_VERSION
                    + " or migrate from "
                    + MIN_MIGRATE_FROM
                    + ").");
        }
        if (existingVersion != null && existingVersion < MIN_MIGRATE_FROM) {
            throw new PersistenceException("Unsupported session DB schema version "
                    + existingVersion
                    + " (required "
                    + SCHEMA_VERSION
                    + "). Delete the database file and recreate, or upgrade through v"
                    + MIN_MIGRATE_FROM
                    + " first.");
        }
        if (existingVersion != null && existingVersion == 13) {
            migrateV13ToV14();
            existingVersion = SCHEMA_VERSION;
        }
        try (Statement statement = commit.connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS host_session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        address TEXT NOT NULL UNIQUE,
                        enabled INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            createSessionChildTables(statement);
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS persistence_event (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_type TEXT NOT NULL,
                        host_id INTEGER NOT NULL,
                        profile TEXT,
                        state TEXT,
                        message TEXT,
                        old_ips_json TEXT,
                        new_ips_json TEXT,
                        detail_json TEXT,
                        observed_at TEXT NOT NULL,
                        FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_pe_host_type_time
                        ON persistence_event(host_id, event_type, observed_at)
                    """);
            createIncidentTable(statement);
            createRouteTable(statement);
            createPollResultTable(statement);
            createMetricRollupTable(statement);
            createTelemetryTables(statement);
        }
        if (existingVersion == null) {
            seedSchemaVersion();
        }
        commit.maybeCommit();
    }

    private void initSchemaReadOnly(Integer existingVersion) {
        if (existingVersion == null) {
            throw new PersistenceException("Session database has no schema version: " + path);
        }
        if (existingVersion != SCHEMA_VERSION) {
            throw new PersistenceException("Unsupported session DB schema version "
                    + existingVersion
                    + " (required "
                    + SCHEMA_VERSION
                    + "). Open read/write once to migrate, or recreate the database file.");
        }
    }

    private static void createSessionChildTables(Statement statement) throws SQLException {
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_route_hop (
                    host_id INTEGER NOT NULL,
                    route_kind TEXT NOT NULL,
                    hop INTEGER NOT NULL,
                    ip TEXT,
                    ping_ms REAL,
                    is_timeout INTEGER NOT NULL,
                    PRIMARY KEY (host_id, route_kind, hop),
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_ping_sample (
                    host_id INTEGER NOT NULL,
                    ip TEXT NOT NULL,
                    seq INTEGER NOT NULL,
                    rtt_ms REAL NOT NULL,
                    PRIMARY KEY (host_id, ip, seq),
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_hop_stats (
                    host_id INTEGER NOT NULL,
                    hop INTEGER NOT NULL,
                    probes INTEGER NOT NULL,
                    successes INTEGER NOT NULL,
                    PRIMARY KEY (host_id, hop),
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_hop_rtt_sample (
                    host_id INTEGER NOT NULL,
                    hop INTEGER NOT NULL,
                    seq INTEGER NOT NULL,
                    rtt_ms REAL NOT NULL,
                    PRIMARY KEY (host_id, hop, seq),
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                )
                """);
    }

    private static void createIncidentTable(Statement statement) throws SQLException {
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS incident (
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
                    details_json TEXT NOT NULL DEFAULT '{}',
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_incident_host_state
                    ON incident(host_id, state, started_at DESC)
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_incident_active
                    ON incident(state, severity, started_at DESC)
                """);
    }

    private static void createRouteTable(Statement statement) throws SQLException {
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS route (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    host_id INTEGER NOT NULL,
                    signature TEXT NOT NULL,
                    hops_json TEXT NOT NULL,
                    first_seen TEXT NOT NULL,
                    last_seen TEXT NOT NULL,
                    seen_count INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE,
                    UNIQUE(host_id, signature)
                )
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_route_host_last_seen
                    ON route(host_id, last_seen DESC)
                """);
    }

    private static void createPollResultTable(Statement statement) throws SQLException {
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS poll_result (
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
                    target_sampled INTEGER NOT NULL,
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_poll_host_time
                    ON poll_result(host_id, observed_at DESC)
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_poll_reachable_time
                    ON poll_result(reachable, observed_at DESC)
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_poll_outcome_time
                    ON poll_result(probe_outcome, observed_at DESC)
                """);
    }

    private static void createMetricRollupTable(Statement statement) throws SQLException {
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS metric_rollup (
                    host_id INTEGER NOT NULL,
                    bucket_start TEXT NOT NULL,
                    bucket_size INTEGER NOT NULL,
                    sample_count INTEGER NOT NULL,
                    reachable_samples INTEGER NOT NULL DEFAULT 0,
                    reachable_count INTEGER NOT NULL DEFAULT 0,
                    rtt_samples INTEGER NOT NULL DEFAULT 0,
                    rtt_sum REAL NOT NULL DEFAULT 0,
                    rtt_min REAL,
                    rtt_max REAL,
                    loss_samples INTEGER NOT NULL DEFAULT 0,
                    loss_sum REAL NOT NULL DEFAULT 0,
                    PRIMARY KEY(host_id, bucket_start, bucket_size),
                    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_rollup_host_bucket
                    ON metric_rollup(host_id, bucket_size, bucket_start DESC)
                """);
    }

    private static void createTelemetryTables(Statement statement) throws SQLException {
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS telemetry_sample (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    value REAL NOT NULL,
                    host TEXT NOT NULL,
                    hop INTEGER,
                    labels_json TEXT NOT NULL DEFAULT '{}',
                    observed_at TEXT NOT NULL
                )
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_ts_host_time
                    ON telemetry_sample(host, observed_at)
                """);
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS telemetry_event (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event TEXT NOT NULL,
                    host TEXT NOT NULL,
                    message TEXT,
                    labels_json TEXT NOT NULL DEFAULT '{}',
                    old_ips_json TEXT NOT NULL DEFAULT '[]',
                    new_ips_json TEXT NOT NULL DEFAULT '[]',
                    observed_at TEXT NOT NULL
                )
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_te_host_time
                    ON telemetry_event(host, observed_at)
                """);
    }

    private Integer readSchemaVersionOrNull() throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement("SELECT version FROM schema_meta LIMIT 1");
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return null;
        }
    }

    private void seedSchemaVersion() throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement("INSERT INTO schema_meta(version) VALUES (?)")) {
            ps.setInt(1, SCHEMA_VERSION);
            ps.executeUpdate();
        }
    }

    private void updateSchemaVersion(int version) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement("UPDATE schema_meta SET version = ?")) {
            ps.setInt(1, version);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                try (PreparedStatement insert =
                        commit.connection.prepareStatement("INSERT INTO schema_meta(version) VALUES (?)")) {
                    insert.setInt(1, version);
                    insert.executeUpdate();
                }
            }
        }
    }

    /**
     * In-place v13 → v14: reshape {@code metric_rollup} to additive counters. Best-effort
     * conversion of legacy averages (assumes every sample contributed when an average was present).
     */
    private void migrateV13ToV14() throws SQLException {
        try (Statement statement = commit.connection.createStatement()) {
            boolean hasLegacyAvg = false;
            try (ResultSet rs = statement.executeQuery("PRAGMA table_info(metric_rollup)")) {
                while (rs.next()) {
                    if ("rtt_avg".equalsIgnoreCase(rs.getString("name"))) {
                        hasLegacyAvg = true;
                        break;
                    }
                }
            }
            if (hasLegacyAvg) {
                statement.execute(
                        """
                        CREATE TABLE metric_rollup_v14 (
                            host_id INTEGER NOT NULL,
                            bucket_start TEXT NOT NULL,
                            bucket_size INTEGER NOT NULL,
                            sample_count INTEGER NOT NULL,
                            reachable_samples INTEGER NOT NULL DEFAULT 0,
                            reachable_count INTEGER NOT NULL DEFAULT 0,
                            rtt_samples INTEGER NOT NULL DEFAULT 0,
                            rtt_sum REAL NOT NULL DEFAULT 0,
                            rtt_min REAL,
                            rtt_max REAL,
                            loss_samples INTEGER NOT NULL DEFAULT 0,
                            loss_sum REAL NOT NULL DEFAULT 0,
                            PRIMARY KEY(host_id, bucket_start, bucket_size),
                            FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
                        )
                        """);
                statement.execute(
                        """
                        INSERT INTO metric_rollup_v14(
                            host_id, bucket_start, bucket_size, sample_count,
                            reachable_samples, reachable_count,
                            rtt_samples, rtt_sum, rtt_min, rtt_max,
                            loss_samples, loss_sum)
                        SELECT
                            host_id,
                            bucket_start,
                            bucket_size,
                            samples,
                            CASE WHEN uptime_ratio IS NULL THEN 0 ELSE samples END,
                            CASE WHEN uptime_ratio IS NULL THEN 0
                                 ELSE CAST(ROUND(uptime_ratio * samples) AS INTEGER) END,
                            CASE WHEN rtt_avg IS NULL THEN 0 ELSE samples END,
                            CASE WHEN rtt_avg IS NULL THEN 0 ELSE rtt_avg * samples END,
                            rtt_min,
                            rtt_max,
                            CASE WHEN loss_avg IS NULL THEN 0 ELSE samples END,
                            CASE WHEN loss_avg IS NULL THEN 0 ELSE loss_avg * samples END
                        FROM metric_rollup
                        """);
                statement.execute("DROP TABLE metric_rollup");
                statement.execute("ALTER TABLE metric_rollup_v14 RENAME TO metric_rollup");
                statement.execute(
                        """
                        CREATE INDEX IF NOT EXISTS idx_rollup_host_bucket
                            ON metric_rollup(host_id, bucket_size, bucket_start DESC)
                        """);
            }
        }
        updateSchemaVersion(SCHEMA_VERSION);
        commit.connection.commit();
    }
}

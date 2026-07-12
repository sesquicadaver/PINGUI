package io.pingui.persistence;

import io.pingui.model.Models.HostSessionData;
import io.pingui.monitor.RouteChangeEvent;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SQLite persistence for per-host session metrics (P11-010) and telemetry archive (P16-020).
 *
 * <p>Schema parity with Python {@code session_db.py}: v2 {@code host_session}, v3
 * {@code persistence_event}, v4 {@code telemetry_sample}/{@code telemetry_event}.
 */
public final class SessionDatabase implements AutoCloseable {
    /** Shared with Python {@code SCHEMA_VERSION} (v4 = telemetry tables). */
    public static final int SCHEMA_VERSION = 4;

    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final Path path;
    private final Connection connection;

    public SessionDatabase(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException ex) {
            throw new PersistenceException("Failed to create database directory: " + path, ex);
        }
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            connection.setAutoCommit(false);
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
            initSchema();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to open session database: " + path, ex);
        }
    }

    public SessionDatabase(String path) {
        this(Path.of(path));
    }

    public Path path() {
        return path;
    }

    public synchronized int schemaVersion() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT version FROM schema_meta LIMIT 1");
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return SCHEMA_VERSION;
            }
            return rs.getInt(1);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to read schema version", ex);
        }
    }

    /** Loads persisted metrics for {@code host}, or {@code null} when absent. */
    public synchronized HostSessionData load(String host) {
        Objects.requireNonNull(host, "host");
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT enabled, current_route_json, previous_route_json,
                       last_known_json, ping_history_json, hop_stats_json
                FROM host_session WHERE host = ?
                """)) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                HostSessionData data = new HostSessionData();
                data.setEnabled(rs.getInt(1) != 0);
                data.setCurrentRoute(SessionJsonCodec.routeFromJson(rs.getString(2)));
                data.setPreviousRoute(SessionJsonCodec.routeFromJson(rs.getString(3)));
                data.getLastKnownByHop().putAll(SessionJsonCodec.lastKnownFromJson(rs.getString(4)));
                data.getPingHistory().putAll(SessionJsonCodec.pingHistoryFromJson(rs.getString(5)));
                String hopStatsJson = rs.getString(6);
                if (hopStatsJson == null || hopStatsJson.isBlank()) {
                    hopStatsJson = "{}";
                }
                data.getHopStats().putAll(SessionJsonCodec.hopStatsFromJson(hopStatsJson));
                return data;
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to load host session: " + host, ex);
        }
    }

    /** Upserts route/ping metrics for {@code host}. */
    public synchronized void save(String host, HostSessionData data) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(data, "data");
        String now = ISO_UTC.format(Instant.now());
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO host_session(
                    host, enabled, current_route_json, previous_route_json,
                    last_known_json, ping_history_json, hop_stats_json, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(host) DO UPDATE SET
                    enabled = excluded.enabled,
                    current_route_json = excluded.current_route_json,
                    previous_route_json = excluded.previous_route_json,
                    last_known_json = excluded.last_known_json,
                    ping_history_json = excluded.ping_history_json,
                    hop_stats_json = excluded.hop_stats_json,
                    updated_at = excluded.updated_at
                """)) {
            ps.setString(1, host);
            ps.setInt(2, data.isEnabled() ? 1 : 0);
            ps.setString(3, SessionJsonCodec.routeToJson(data.getCurrentRoute()));
            ps.setString(4, SessionJsonCodec.routeToJson(data.getPreviousRoute()));
            ps.setString(5, SessionJsonCodec.lastKnownToJson(data.getLastKnownByHop()));
            ps.setString(6, SessionJsonCodec.pingHistoryToJson(data.getPingHistory()));
            ps.setString(7, SessionJsonCodec.hopStatsToJson(data.getHopStats()));
            ps.setString(8, now);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to save host session: " + host, ex);
        }
    }

    public synchronized void delete(String host) {
        Objects.requireNonNull(host, "host");
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM host_session WHERE host = ?")) {
            ps.setString(1, host);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to delete host session: " + host, ex);
        }
    }

    public synchronized void rename(String oldHost, String newHost) {
        Objects.requireNonNull(oldHost, "oldHost");
        Objects.requireNonNull(newHost, "newHost");
        if (oldHost.equals(newHost)) {
            return;
        }
        HostSessionData data = load(oldHost);
        if (data == null) {
            return;
        }
        save(newHost, data);
        rewriteEventHosts(oldHost, newHost);
        delete(oldHost);
    }

    private void rewriteEventHosts(String oldHost, String newHost) {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, event_type, payload_json FROM persistence_event WHERE host = ?")) {
            select.setString(1, oldHost);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String eventType = rs.getString(2);
                    String payload = rs.getString(3);
                    String rewritten = rewriteEventPayload(eventType, oldHost, newHost, payload);
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE persistence_event SET host = ?, payload_json = ? WHERE id = ?")) {
                        update.setString(1, newHost);
                        update.setString(2, rewritten);
                        update.setLong(3, id);
                        update.executeUpdate();
                    }
                }
            }
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to rename persistence events: " + oldHost + " -> " + newHost, ex);
        }
    }

    private static String rewriteEventPayload(String eventType, String oldHost, String newHost, String payload) {
        if (!PersistenceEventType.ROUTE_CHANGE.id().equals(eventType)) {
            return payload;
        }
        RouteChangeEvent event = RouteChangeEvent.fromJson(payload);
        if (!oldHost.equals(event.host())) {
            return payload;
        }
        return new RouteChangeEvent(newHost, event.oldIps(), event.newIps(), event.timestamp(), event.profile())
                .toJson();
    }

    /** Returns all hosts with persisted session rows, sorted lexicographically. */
    public synchronized List<String> listHosts() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT host FROM host_session ORDER BY host");
                ResultSet rs = ps.executeQuery()) {
            List<String> hosts = new ArrayList<>();
            while (rs.next()) {
                hosts.add(rs.getString(1));
            }
            return List.copyOf(hosts);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list hosts", ex);
        }
    }

    /**
     * Appends a discrete event row (P11-011+). Table is created by schema v3 migration.
     */
    public synchronized void insertEvent(
            PersistenceEventType eventType, String host, String profile, String payloadJson, Instant observedAt) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Instant when = observedAt != null ? observedAt : Instant.now();
        String profileValue = profile == null || profile.isBlank() ? null : profile;
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO persistence_event(event_type, host, profile, payload_json, observed_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, eventType.id());
            ps.setString(2, host);
            ps.setString(3, profileValue);
            ps.setString(4, payloadJson);
            ps.setString(5, ISO_UTC.format(when));
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to insert persistence event for " + host, ex);
        }
    }

    /** Deletes all rows of {@code eventType}; used by purge policy (P11-014). */
    public synchronized int deleteEventsByType(PersistenceEventType eventType) {
        Objects.requireNonNull(eventType, "eventType");
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM persistence_event WHERE event_type = ?")) {
            ps.setString(1, eventType.id());
            int deleted = ps.executeUpdate();
            connection.commit();
            return deleted;
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to purge events: " + eventType.id(), ex);
        }
    }

    /** Returns number of rows for {@code eventType} (tests / diagnostics). */
    public synchronized int countEvents(PersistenceEventType eventType) {
        Objects.requireNonNull(eventType, "eventType");
        try (PreparedStatement ps =
                connection.prepareStatement("SELECT COUNT(*) FROM persistence_event WHERE event_type = ?")) {
            ps.setString(1, eventType.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to count events: " + eventType.id(), ex);
        }
    }

    /**
     * Lists discrete events for {@code host} since {@code since} (P11-020). Newest first.
     *
     * @param limit max rows (must be &gt;= 1)
     */
    public synchronized List<PersistenceEventRecord> listEvents(
            PersistenceEventType eventType, String host, Instant since, int limit) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(since, "since");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        String sinceIso = ISO_UTC.format(since);
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT id, event_type, host, profile, payload_json, observed_at
                FROM persistence_event
                WHERE event_type = ? AND host = ? AND observed_at >= ?
                ORDER BY observed_at DESC
                LIMIT ?
                """)) {
            ps.setString(1, eventType.id());
            ps.setString(2, host);
            ps.setString(3, sinceIso);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<PersistenceEventRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new PersistenceEventRecord(
                            rs.getLong(1),
                            PersistenceEventType.fromId(rs.getString(2)),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            Instant.parse(rs.getString(6))));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list events for " + host, ex);
        }
    }

    /**
     * Appends one telemetry sample row (P16-020). No FK to {@code host_session}.
     */
    public synchronized void insertTelemetrySample(MetricSample sample) {
        Objects.requireNonNull(sample, "sample");
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO telemetry_sample(name, value, host, hop, payload_json, observed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, sample.name());
            ps.setDouble(2, sample.value());
            ps.setString(3, sample.host());
            if (sample.hop() == null) {
                ps.setObject(4, null);
            } else {
                ps.setInt(4, sample.hop());
            }
            ps.setString(5, sample.toJson());
            ps.setString(6, ISO_UTC.format(sample.timestamp()));
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to insert telemetry sample for " + sample.host(), ex);
        }
    }

    /** Appends one telemetry event row (P16-020). */
    public synchronized void insertTelemetryEvent(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO telemetry_event(event, host, message, payload_json, observed_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, event.event());
            ps.setString(2, event.host());
            ps.setString(3, event.message());
            ps.setString(4, event.toJson());
            ps.setString(5, ISO_UTC.format(event.timestamp()));
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to insert telemetry event for " + event.host(), ex);
        }
    }

    /** Newest-first sample payloads for {@code host} (tests / diagnostics / dump). */
    public synchronized List<MetricSample> listTelemetrySamples(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT payload_json FROM telemetry_sample
                WHERE host = ?
                ORDER BY observed_at DESC, id DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<MetricSample> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(MetricSample.fromJson(rs.getString(1)));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list telemetry samples for " + host, ex);
        }
    }

    /** Newest-first event payloads for {@code host}. */
    public synchronized List<TelemetryEvent> listTelemetryEvents(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT payload_json FROM telemetry_event
                WHERE host = ?
                ORDER BY observed_at DESC, id DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<TelemetryEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(TelemetryEvent.fromJson(rs.getString(1)));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list telemetry events for " + host, ex);
        }
    }

    public synchronized int countTelemetrySamples() {
        return countTable("telemetry_sample");
    }

    public synchronized int countTelemetryEvents() {
        return countTable("telemetry_event");
    }

    /**
     * Deletes telemetry samples with {@code observed_at} strictly before {@code cutoff} (P16-022).
     *
     * @return number of deleted sample rows
     */
    public synchronized int deleteTelemetrySamplesBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return deleteBefore("telemetry_sample", cutoff);
    }

    /**
     * Deletes telemetry events with {@code observed_at} strictly before {@code cutoff} (P16-022).
     *
     * @return number of deleted event rows
     */
    public synchronized int deleteTelemetryEventsBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return deleteBefore("telemetry_event", cutoff);
    }

    private int deleteBefore(String table, Instant cutoff) {
        String iso = ISO_UTC.format(cutoff);
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE observed_at < ?")) {
            ps.setString(1, iso);
            int deleted = ps.executeUpdate();
            connection.commit();
            return deleted;
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to purge " + table + " before " + iso, ex);
        }
    }

    private int countTable(String table) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to count " + table, ex);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to close session database", ex);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS schema_meta (
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS host_session (
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
                    CREATE TABLE IF NOT EXISTS persistence_event (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_type TEXT NOT NULL,
                        host TEXT NOT NULL,
                        profile TEXT,
                        payload_json TEXT NOT NULL,
                        observed_at TEXT NOT NULL,
                        FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                    )
                    """);
            statement.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_pe_host_type_time
                        ON persistence_event(host, event_type, observed_at)
                    """);
            createTelemetryTables(statement);
        }
        int currentVersion = readOrSeedSchemaVersion();
        migrateSchema(currentVersion);
        connection.commit();
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
                    payload_json TEXT NOT NULL,
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
                    payload_json TEXT NOT NULL,
                    observed_at TEXT NOT NULL
                )
                """);
        statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_te_host_time
                    ON telemetry_event(host, observed_at)
                """);
    }

    private int readOrSeedSchemaVersion() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT version FROM schema_meta LIMIT 1");
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO schema_meta(version) VALUES (?)")) {
            ps.setInt(1, SCHEMA_VERSION);
            ps.executeUpdate();
        }
        return SCHEMA_VERSION;
    }

    private void migrateSchema(int currentVersion) throws SQLException {
        if (currentVersion < 2) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE host_session ADD COLUMN hop_stats_json TEXT NOT NULL DEFAULT '{}'");
            } catch (SQLException ex) {
                if (!isDuplicateColumn(ex)) {
                    throw ex;
                }
            }
            setSchemaVersion(2);
            currentVersion = 2;
        }
        if (currentVersion < 3) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        """
                        CREATE TABLE IF NOT EXISTS persistence_event (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            event_type TEXT NOT NULL,
                            host TEXT NOT NULL,
                            profile TEXT,
                            payload_json TEXT NOT NULL,
                            observed_at TEXT NOT NULL,
                            FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                        )
                        """);
                statement.execute(
                        """
                        CREATE INDEX IF NOT EXISTS idx_pe_host_type_time
                            ON persistence_event(host, event_type, observed_at)
                        """);
            }
            setSchemaVersion(3);
            currentVersion = 3;
        }
        if (currentVersion < 4) {
            try (Statement statement = connection.createStatement()) {
                createTelemetryTables(statement);
            }
            setSchemaVersion(4);
        }
    }

    private void setSchemaVersion(int version) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE schema_meta SET version = ?")) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    private static boolean isDuplicateColumn(SQLException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("duplicate column");
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort after failure.
        }
    }
}

package io.pingui.persistence;

import io.pingui.model.Models.HostSessionData;
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
import java.util.Objects;

/**
 * SQLite persistence for per-host session metrics (P11-010).
 *
 * <p>Schema parity with Python {@code session_db.py} (v2 {@code host_session}) plus v3
 * {@code persistence_event} for discrete timeline events.
 */
public final class SessionDatabase implements AutoCloseable {
    /** Python {@code SCHEMA_VERSION}; Java adds {@code persistence_event} at v3. */
    public static final int SCHEMA_VERSION = 3;

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

    public int schemaVersion() {
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
    public HostSessionData load(String host) {
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
    public void save(String host, HostSessionData data) {
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

    public void delete(String host) {
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

    public void rename(String oldHost, String newHost) {
        Objects.requireNonNull(oldHost, "oldHost");
        Objects.requireNonNull(newHost, "newHost");
        HostSessionData data = load(oldHost);
        if (data == null) {
            return;
        }
        delete(oldHost);
        save(newHost, data);
    }

    /**
     * Appends a discrete event row (P11-011+). Table is created by schema v3 migration.
     */
    public void insertEvent(
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
    public int deleteEventsByType(PersistenceEventType eventType) {
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

    @Override
    public void close() {
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
        }
        int currentVersion = readOrSeedSchemaVersion();
        migrateSchema(currentVersion);
        connection.commit();
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

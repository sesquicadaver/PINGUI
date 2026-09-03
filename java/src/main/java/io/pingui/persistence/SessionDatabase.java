package io.pingui.persistence;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HostSessionData;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SQLite persistence for per-host session metrics (P11-010 / P27-003) and telemetry archive (P16 /
 * P27).
 *
 * <p>Schema v7: normalized {@code host_session} + child hop/history/stats tables; typed {@code
 * persistence_event}; telemetry columns SSOT. Legacy schema versions are rejected (delete and
 * recreate the DB file).
 */
public final class SessionDatabase implements AutoCloseable {
    /** Current Java session DB schema (v7 = normalized host_session, P27-003). */
    public static final int SCHEMA_VERSION = 7;

    private static final String ROUTE_CURRENT = "current";
    private static final String ROUTE_PREVIOUS = "previous";
    private static final String ROUTE_LAST_KNOWN = "last_known";

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
        Connection opened;
        try {
            opened = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to open session database: " + path, ex);
        }
        try {
            opened.setAutoCommit(false);
            try (Statement pragma = opened.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
            this.connection = opened;
            initSchema();
        } catch (SQLException ex) {
            closeQuietly(opened);
            throw new PersistenceException("Failed to open session database: " + path, ex);
        } catch (PersistenceException ex) {
            closeQuietly(opened);
            throw ex;
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
        try (PreparedStatement ps = connection.prepareStatement("SELECT enabled FROM host_session WHERE host = ?")) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                HostSessionData data = new HostSessionData();
                data.setEnabled(rs.getInt(1) != 0);
                data.setCurrentRoute(loadRouteHops(host, ROUTE_CURRENT));
                data.setPreviousRoute(loadRouteHops(host, ROUTE_PREVIOUS));
                data.getLastKnownByHop().putAll(loadLastKnown(host));
                data.getPingHistory().putAll(loadPingHistory(host));
                data.getHopStats().putAll(loadHopStats(host));
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
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO host_session(host, enabled, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(host) DO UPDATE SET
                        enabled = excluded.enabled,
                        updated_at = excluded.updated_at
                    """)) {
                ps.setString(1, host);
                ps.setInt(2, data.isEnabled() ? 1 : 0);
                ps.setString(3, now);
                ps.executeUpdate();
            }
            clearHostChildren(host);
            insertRouteHops(host, ROUTE_CURRENT, data.getCurrentRoute());
            insertRouteHops(host, ROUTE_PREVIOUS, data.getPreviousRoute());
            insertLastKnown(host, data.getLastKnownByHop());
            insertPingHistory(host, data.getPingHistory());
            insertHopStats(host, data.getHopStats());
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
        try (PreparedStatement update =
                connection.prepareStatement("UPDATE persistence_event SET host = ? WHERE host = ?")) {
            update.setString(1, newHost);
            update.setString(2, oldHost);
            update.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to rename persistence events: " + oldHost + " -> " + newHost, ex);
        }
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
     * Appends a discrete event row (P11-011+ / P27-002 typed columns).
     */
    public synchronized void insertEvent(
            PersistenceEventType eventType,
            String host,
            String profile,
            String state,
            String message,
            String oldIpsJson,
            String newIpsJson,
            String detailJson,
            Instant observedAt) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(host, "host");
        Instant when = observedAt != null ? observedAt : Instant.now();
        String profileValue = profile == null || profile.isBlank() ? null : profile;
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO persistence_event(
                    event_type, host, profile, state, message,
                    old_ips_json, new_ips_json, detail_json, observed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, eventType.id());
            ps.setString(2, host);
            ps.setString(3, profileValue);
            ps.setString(4, state);
            ps.setString(5, message);
            ps.setString(6, oldIpsJson);
            ps.setString(7, newIpsJson);
            ps.setString(8, detailJson);
            ps.setString(9, ISO_UTC.format(when));
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to insert persistence event for " + host, ex);
        }
    }

    /** Convenience insert for route-change tests and writers. */
    public synchronized void insertRouteChange(
            String host, String profile, List<String> oldIps, List<String> newIps, Instant observedAt) {
        insertEvent(
                PersistenceEventType.ROUTE_CHANGE,
                host,
                profile,
                null,
                null,
                PersistenceJson.stringArray(oldIps),
                PersistenceJson.stringArray(newIps),
                null,
                observedAt);
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
                SELECT id, event_type, host, profile, state, message,
                       old_ips_json, new_ips_json, detail_json, observed_at
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
                            rs.getString(6),
                            rs.getString(7),
                            rs.getString(8),
                            rs.getString(9),
                            Instant.parse(rs.getString(10))));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list events for " + host, ex);
        }
    }

    /**
     * Lists all discrete event types for {@code host} since {@code since} (P29-002). Newest first.
     *
     * @param limit max rows (must be &gt;= 1)
     */
    public synchronized List<PersistenceEventRecord> listHostEvents(String host, Instant since, int limit) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(since, "since");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        String sinceIso = ISO_UTC.format(since);
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT id, event_type, host, profile, state, message,
                       old_ips_json, new_ips_json, detail_json, observed_at
                FROM persistence_event
                WHERE host = ? AND observed_at >= ?
                ORDER BY observed_at DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setString(2, sinceIso);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<PersistenceEventRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new PersistenceEventRecord(
                            rs.getLong(1),
                            PersistenceEventType.fromId(rs.getString(2)),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getString(7),
                            rs.getString(8),
                            rs.getString(9),
                            Instant.parse(rs.getString(10))));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list host events for " + host, ex);
        }
    }

    /**
     * Appends one telemetry sample row (P16-020 / P27-001). Columns are SSOT; dump rebuilds JSON.
     */
    public synchronized void insertTelemetrySample(MetricSample sample) {
        Objects.requireNonNull(sample, "sample");
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO telemetry_sample(name, value, host, hop, labels_json, observed_at)
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
            ps.setString(5, sample.labelsJson());
            ps.setString(6, ISO_UTC.format(sample.timestamp()));
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to insert telemetry sample for " + sample.host(), ex);
        }
    }

    /** Appends one telemetry event row (P16-020 / P27-001). */
    public synchronized void insertTelemetryEvent(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO telemetry_event(
                    event, host, message, labels_json, old_ips_json, new_ips_json, observed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, event.event());
            ps.setString(2, event.host());
            ps.setString(3, event.message());
            ps.setString(4, event.labelsJson());
            ps.setString(5, event.oldIpsJson());
            ps.setString(6, event.newIpsJson());
            ps.setString(7, ISO_UTC.format(event.timestamp()));
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to insert telemetry event for " + event.host(), ex);
        }
    }

    /** Newest-first samples for {@code host} (tests / diagnostics / dump). */
    public synchronized List<MetricSample> listTelemetrySamples(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT name, value, host, hop, labels_json, observed_at FROM telemetry_sample
                WHERE host = ?
                ORDER BY observed_at DESC, id DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<MetricSample> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(readTelemetrySample(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list telemetry samples for " + host, ex);
        }
    }

    /** Newest-first events for {@code host}. */
    public synchronized List<TelemetryEvent> listTelemetryEvents(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT event, host, message, labels_json, old_ips_json, new_ips_json, observed_at
                FROM telemetry_event
                WHERE host = ?
                ORDER BY observed_at DESC, id DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<TelemetryEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(readTelemetryEvent(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list telemetry events for " + host, ex);
        }
    }

    /** Oldest-first samples for full archive dump (P16-023). */
    public synchronized List<MetricSample> listAllTelemetrySamples() {
        try (PreparedStatement ps = connection.prepareStatement(
                        """
                SELECT name, value, host, hop, labels_json, observed_at FROM telemetry_sample
                ORDER BY observed_at ASC, id ASC
                """);
                ResultSet rs = ps.executeQuery()) {
            List<MetricSample> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(readTelemetrySample(rs));
            }
            return List.copyOf(rows);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list all telemetry samples", ex);
        }
    }

    /** Oldest-first events for full archive dump (P16-023). */
    public synchronized List<TelemetryEvent> listAllTelemetryEvents() {
        try (PreparedStatement ps = connection.prepareStatement(
                        """
                SELECT event, host, message, labels_json, old_ips_json, new_ips_json, observed_at
                FROM telemetry_event
                ORDER BY observed_at ASC, id ASC
                """);
                ResultSet rs = ps.executeQuery()) {
            List<TelemetryEvent> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(readTelemetryEvent(rs));
            }
            return List.copyOf(rows);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list all telemetry events", ex);
        }
    }

    private static MetricSample readTelemetrySample(ResultSet rs) throws SQLException {
        Integer hop = rs.getObject(4) == null ? null : rs.getInt(4);
        return MetricSample.fromColumns(
                rs.getString(1),
                rs.getDouble(2),
                rs.getString(3),
                hop,
                rs.getString(5),
                Instant.parse(rs.getString(6)));
    }

    private static TelemetryEvent readTelemetryEvent(ResultSet rs) throws SQLException {
        return TelemetryEvent.fromColumns(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                Instant.parse(rs.getString(7)));
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
                        updated_at TEXT NOT NULL
                    )
                    """);
            createSessionChildTables(statement);
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS persistence_event (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_type TEXT NOT NULL,
                        host TEXT NOT NULL,
                        profile TEXT,
                        state TEXT,
                        message TEXT,
                        old_ips_json TEXT,
                        new_ips_json TEXT,
                        detail_json TEXT,
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
        if (currentVersion != SCHEMA_VERSION) {
            throw new PersistenceException("Unsupported session DB schema version "
                    + currentVersion
                    + " (required "
                    + SCHEMA_VERSION
                    + "). Delete the database file and recreate.");
        }
        connection.commit();
    }

    private static void createSessionChildTables(Statement statement) throws SQLException {
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_route_hop (
                    host TEXT NOT NULL,
                    route_kind TEXT NOT NULL,
                    hop INTEGER NOT NULL,
                    ip TEXT,
                    ping_ms REAL,
                    is_timeout INTEGER NOT NULL,
                    PRIMARY KEY (host, route_kind, hop),
                    FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_ping_sample (
                    host TEXT NOT NULL,
                    ip TEXT NOT NULL,
                    seq INTEGER NOT NULL,
                    rtt_ms REAL NOT NULL,
                    PRIMARY KEY (host, ip, seq),
                    FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_hop_stats (
                    host TEXT NOT NULL,
                    hop INTEGER NOT NULL,
                    probes INTEGER NOT NULL,
                    successes INTEGER NOT NULL,
                    PRIMARY KEY (host, hop),
                    FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                )
                """);
        statement.execute(
                """
                CREATE TABLE IF NOT EXISTS session_hop_rtt_sample (
                    host TEXT NOT NULL,
                    hop INTEGER NOT NULL,
                    seq INTEGER NOT NULL,
                    rtt_ms REAL NOT NULL,
                    PRIMARY KEY (host, hop, seq),
                    FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                )
                """);
    }

    private void clearHostChildren(String host) throws SQLException {
        try (PreparedStatement route = connection.prepareStatement("DELETE FROM session_route_hop WHERE host = ?");
                PreparedStatement ping = connection.prepareStatement("DELETE FROM session_ping_sample WHERE host = ?");
                PreparedStatement rtt =
                        connection.prepareStatement("DELETE FROM session_hop_rtt_sample WHERE host = ?");
                PreparedStatement stats = connection.prepareStatement("DELETE FROM session_hop_stats WHERE host = ?")) {
            route.setString(1, host);
            route.executeUpdate();
            ping.setString(1, host);
            ping.executeUpdate();
            rtt.setString(1, host);
            rtt.executeUpdate();
            stats.setString(1, host);
            stats.executeUpdate();
        }
    }

    private List<HopNode> loadRouteHops(String host, String routeKind) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT hop, ip, ping_ms, is_timeout FROM session_route_hop
                WHERE host = ? AND route_kind = ?
                ORDER BY hop ASC
                """)) {
            ps.setString(1, host);
            ps.setString(2, routeKind);
            try (ResultSet rs = ps.executeQuery()) {
                List<HopNode> hops = new ArrayList<>();
                while (rs.next()) {
                    hops.add(readHopNode(rs));
                }
                return List.copyOf(hops);
            }
        }
    }

    private Map<Integer, HopNode> loadLastKnown(String host) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT hop, ip, ping_ms, is_timeout FROM session_route_hop
                WHERE host = ? AND route_kind = ?
                ORDER BY hop ASC
                """)) {
            ps.setString(1, host);
            ps.setString(2, ROUTE_LAST_KNOWN);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, HopNode> result = new LinkedHashMap<>();
                while (rs.next()) {
                    HopNode node = readHopNode(rs);
                    result.put(node.hop(), node);
                }
                return result;
            }
        }
    }

    private Map<String, List<Double>> loadPingHistory(String host) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT ip, seq, rtt_ms FROM session_ping_sample
                WHERE host = ?
                ORDER BY ip ASC, seq ASC
                """)) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, List<Double>> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.computeIfAbsent(rs.getString(1), key -> new ArrayList<>())
                            .add(rs.getDouble(3));
                }
                Map<String, List<Double>> copy = new LinkedHashMap<>();
                for (Map.Entry<String, List<Double>> entry : result.entrySet()) {
                    copy.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
                return copy;
            }
        }
    }

    private Map<Integer, HopProbeStats> loadHopStats(String host) throws SQLException {
        Map<Integer, List<Double>> samplesByHop = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT hop, seq, rtt_ms FROM session_hop_rtt_sample
                WHERE host = ?
                ORDER BY hop ASC, seq ASC
                """)) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    samplesByHop
                            .computeIfAbsent(rs.getInt(1), key -> new ArrayList<>())
                            .add(rs.getDouble(3));
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT hop, probes, successes FROM session_hop_stats
                WHERE host = ?
                ORDER BY hop ASC
                """)) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, HopProbeStats> result = new LinkedHashMap<>();
                while (rs.next()) {
                    int hop = rs.getInt(1);
                    List<Double> samples = samplesByHop.getOrDefault(hop, List.of());
                    result.put(hop, HopProbeStats.fromSerialized(rs.getInt(2), rs.getInt(3), samples));
                }
                return result;
            }
        }
    }

    private void insertRouteHops(String host, String routeKind, List<HopNode> hops) throws SQLException {
        if (hops == null || hops.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO session_route_hop(host, route_kind, hop, ip, ping_ms, is_timeout)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (HopNode node : hops) {
                bindHop(ps, host, routeKind, node);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertLastKnown(String host, Map<Integer, HopNode> lastKnown) throws SQLException {
        if (lastKnown == null || lastKnown.isEmpty()) {
            return;
        }
        List<Map.Entry<Integer, HopNode>> entries = new ArrayList<>(lastKnown.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO session_route_hop(host, route_kind, hop, ip, ping_ms, is_timeout)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (Map.Entry<Integer, HopNode> entry : entries) {
                bindHop(ps, host, ROUTE_LAST_KNOWN, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertPingHistory(String host, Map<String, List<Double>> history) throws SQLException {
        if (history == null || history.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO session_ping_sample(host, ip, seq, rtt_ms)
                VALUES (?, ?, ?, ?)
                """)) {
            for (Map.Entry<String, List<Double>> entry : history.entrySet()) {
                List<Double> samples = entry.getValue();
                if (samples == null) {
                    continue;
                }
                for (int i = 0; i < samples.size(); i++) {
                    ps.setString(1, host);
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, i);
                    ps.setDouble(4, samples.get(i));
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void insertHopStats(String host, Map<Integer, HopProbeStats> stats) throws SQLException {
        if (stats == null || stats.isEmpty()) {
            return;
        }
        try (PreparedStatement statsPs = connection.prepareStatement(
                        """
                        INSERT INTO session_hop_stats(host, hop, probes, successes)
                        VALUES (?, ?, ?, ?)
                        """);
                PreparedStatement rttPs = connection.prepareStatement(
                        """
                        INSERT INTO session_hop_rtt_sample(host, hop, seq, rtt_ms)
                        VALUES (?, ?, ?, ?)
                        """)) {
            for (Map.Entry<Integer, HopProbeStats> entry : stats.entrySet()) {
                HopProbeStats item = entry.getValue();
                if (item == null) {
                    continue;
                }
                int hop = entry.getKey();
                statsPs.setString(1, host);
                statsPs.setInt(2, hop);
                statsPs.setInt(3, item.getProbes());
                statsPs.setInt(4, item.getSuccesses());
                statsPs.addBatch();
                List<Double> samples = item.getRttSamples();
                for (int i = 0; i < samples.size(); i++) {
                    rttPs.setString(1, host);
                    rttPs.setInt(2, hop);
                    rttPs.setInt(3, i);
                    rttPs.setDouble(4, samples.get(i));
                    rttPs.addBatch();
                }
            }
            statsPs.executeBatch();
            rttPs.executeBatch();
        }
    }

    private static void bindHop(PreparedStatement ps, String host, String routeKind, HopNode node) throws SQLException {
        ps.setString(1, host);
        ps.setString(2, routeKind);
        ps.setInt(3, node.hop());
        ps.setString(4, node.ip());
        if (node.pingMs() == null) {
            ps.setObject(5, null);
        } else {
            ps.setDouble(5, node.pingMs());
        }
        ps.setInt(6, node.timeout() ? 1 : 0);
    }

    private static HopNode readHopNode(ResultSet rs) throws SQLException {
        int hop = rs.getInt(1);
        boolean timeout = rs.getInt(4) != 0;
        if (timeout) {
            return Models.timeout(hop);
        }
        Double pingMs = rs.getObject(3) == null ? null : rs.getDouble(3);
        return new HopNode(hop, rs.getString(2), pingMs, false);
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

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Best effort after failure.
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Best-effort so Windows can delete @TempDir files after failed open.
        }
    }
}

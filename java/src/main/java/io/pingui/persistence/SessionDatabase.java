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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * SQLite persistence for per-host session metrics (P11-010 / P27-003 / P30-001 / P30-002) and
 * telemetry archive (P16 / P27).
 *
 * <p>Schema v9: stable {@code host_session.id}; {@code incident} table for quality FIRING/RESOLVED;
 * child tables and {@code persistence_event} use {@code host_id}. Public API remains address-keyed.
 * Legacy schema versions are rejected (delete and recreate the DB file).
 */
public final class SessionDatabase implements AutoCloseable {
    /** Current Java session DB schema (v9 = incident table, P30-002). */
    public static final int SCHEMA_VERSION = 9;

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
                pragma.execute("PRAGMA busy_timeout = 5000");
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

    /**
     * Resolves the stable integer id for {@code address}, or empty when the host row is absent
     * (tests / diagnostics).
     */
    public synchronized OptionalLong hostId(String address) {
        Objects.requireNonNull(address, "address");
        try {
            return findHostId(address);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to resolve host id: " + address, ex);
        }
    }

    /** Loads persisted metrics for {@code host} address, or {@code null} when absent. */
    public synchronized HostSessionData load(String host) {
        Objects.requireNonNull(host, "host");
        try {
            OptionalLong id = findHostId(host);
            if (id.isEmpty()) {
                return null;
            }
            long hostId = id.getAsLong();
            try (PreparedStatement ps = connection.prepareStatement("SELECT enabled FROM host_session WHERE id = ?")) {
                ps.setLong(1, hostId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    HostSessionData data = new HostSessionData();
                    data.setEnabled(rs.getInt(1) != 0);
                    data.setCurrentRoute(loadRouteHops(hostId, ROUTE_CURRENT));
                    data.setPreviousRoute(loadRouteHops(hostId, ROUTE_PREVIOUS));
                    data.getLastKnownByHop().putAll(loadLastKnown(hostId));
                    data.getPingHistory().putAll(loadPingHistory(hostId));
                    data.getHopStats().putAll(loadHopStats(hostId));
                    return data;
                }
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to load host session: " + host, ex);
        }
    }

    /** Upserts route/ping metrics for {@code host} address. */
    public synchronized void save(String host, HostSessionData data) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(data, "data");
        String now = ISO_UTC.format(Instant.now());
        try {
            long hostId = upsertHost(host, data.isEnabled(), now);
            clearHostChildren(hostId);
            insertRouteHops(hostId, ROUTE_CURRENT, data.getCurrentRoute());
            insertRouteHops(hostId, ROUTE_PREVIOUS, data.getPreviousRoute());
            insertLastKnown(hostId, data.getLastKnownByHop());
            insertPingHistory(hostId, data.getPingHistory());
            insertHopStats(hostId, data.getHopStats());
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to save host session: " + host, ex);
        }
    }

    public synchronized void delete(String host) {
        Objects.requireNonNull(host, "host");
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM host_session WHERE address = ?")) {
            ps.setString(1, host);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to delete host session: " + host, ex);
        }
    }

    /**
     * Renames the host address in place. Child rows and {@code persistence_event} keep the same
     * {@code host_id} (no cascade rewrite).
     */
    public synchronized void rename(String oldHost, String newHost) {
        Objects.requireNonNull(oldHost, "oldHost");
        Objects.requireNonNull(newHost, "newHost");
        if (oldHost.equals(newHost)) {
            return;
        }
        String now = ISO_UTC.format(Instant.now());
        try {
            OptionalLong id = findHostId(oldHost);
            if (id.isEmpty()) {
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    UPDATE host_session
                    SET address = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, newHost);
                ps.setString(2, now);
                ps.setLong(3, id.getAsLong());
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to rename host session: " + oldHost + " -> " + newHost, ex);
        }
    }

    /** Returns all host addresses with persisted session rows, sorted lexicographically. */
    public synchronized List<String> listHosts() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT address FROM host_session ORDER BY address");
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
     * Appends a discrete event row (P11-011+ / P27-002 typed columns). Requires an existing host
     * session row.
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
        try {
            long hostId = requireHostId(host);
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO persistence_event(
                        event_type, host_id, profile, state, message,
                        old_ips_json, new_ips_json, detail_json, observed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, eventType.id());
                ps.setLong(2, hostId);
                ps.setString(3, profileValue);
                ps.setString(4, state);
                ps.setString(5, message);
                ps.setString(6, oldIpsJson);
                ps.setString(7, newIpsJson);
                ps.setString(8, detailJson);
                ps.setString(9, ISO_UTC.format(when));
                ps.executeUpdate();
            }
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
     * Lists discrete events for {@code host} address since {@code since} (P11-020). Newest first.
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
                SELECT pe.id, pe.event_type, hs.address, pe.profile, pe.state, pe.message,
                       pe.old_ips_json, pe.new_ips_json, pe.detail_json, pe.observed_at
                FROM persistence_event pe
                JOIN host_session hs ON hs.id = pe.host_id
                WHERE pe.event_type = ? AND hs.address = ? AND pe.observed_at >= ?
                ORDER BY pe.observed_at DESC
                LIMIT ?
                """)) {
            ps.setString(1, eventType.id());
            ps.setString(2, host);
            ps.setString(3, sinceIso);
            ps.setInt(4, limit);
            return readEventRows(ps);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list events for " + host, ex);
        }
    }

    /**
     * Lists all discrete event types for {@code host} address since {@code since} (P29-002). Newest
     * first.
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
                SELECT pe.id, pe.event_type, hs.address, pe.profile, pe.state, pe.message,
                       pe.old_ips_json, pe.new_ips_json, pe.detail_json, pe.observed_at
                FROM persistence_event pe
                JOIN host_session hs ON hs.id = pe.host_id
                WHERE hs.address = ? AND pe.observed_at >= ?
                ORDER BY pe.observed_at DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setString(2, sinceIso);
            ps.setInt(3, limit);
            return readEventRows(ps);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list host events for " + host, ex);
        }
    }

    /**
     * Opens or refreshes a FIRING incident for {@code host}/{@code kind} (P30-002). Same open kind
     * increments {@code occurrences}.
     *
     * @return incident id
     */
    public synchronized long openOrRefreshIncident(
            String host, String kind, String severity, Instant startedAt, Double peakValue, String detailsJson) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Instant when = startedAt != null ? startedAt : Instant.now();
        String details = detailsJson == null || detailsJson.isBlank() ? "{}" : detailsJson;
        try {
            long hostId = requireHostId(host);
            OptionalLong openId = findOpenIncidentId(hostId, kind);
            if (openId.isPresent()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        """
                        UPDATE incident
                        SET occurrences = occurrences + 1,
                            peak_value = CASE
                                WHEN ? IS NOT NULL AND (peak_value IS NULL OR ? > peak_value) THEN ?
                                ELSE peak_value
                            END,
                            details_json = ?
                        WHERE id = ?
                        """)) {
                    if (peakValue == null) {
                        ps.setObject(1, null);
                        ps.setObject(2, null);
                        ps.setObject(3, null);
                    } else {
                        ps.setDouble(1, peakValue);
                        ps.setDouble(2, peakValue);
                        ps.setDouble(3, peakValue);
                    }
                    ps.setString(4, details);
                    ps.setLong(5, openId.getAsLong());
                    ps.executeUpdate();
                }
                connection.commit();
                return openId.getAsLong();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO incident(
                        host_id, kind, severity, state, started_at, ended_at, acknowledged_at,
                        occurrences, peak_value, details_json)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, 1, ?, ?)
                    """)) {
                ps.setLong(1, hostId);
                ps.setString(2, kind);
                ps.setString(3, severity);
                ps.setString(4, IncidentRecord.STATE_FIRING);
                ps.setString(5, ISO_UTC.format(when));
                if (peakValue == null) {
                    ps.setObject(6, null);
                } else {
                    ps.setDouble(6, peakValue);
                }
                ps.setString(7, details);
                ps.executeUpdate();
            }
            long id;
            try (PreparedStatement ps = connection.prepareStatement("SELECT last_insert_rowid()");
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                id = rs.getLong(1);
            }
            connection.commit();
            return id;
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to open incident for " + host + "/" + kind, ex);
        }
    }

    /**
     * Resolves the open FIRING incident for {@code host}/{@code kind}, if any.
     *
     * @return true when a row was updated
     */
    public synchronized boolean resolveIncident(String host, String kind, Instant endedAt) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(kind, "kind");
        Instant when = endedAt != null ? endedAt : Instant.now();
        try {
            OptionalLong hostId = findHostId(host);
            if (hostId.isEmpty()) {
                return false;
            }
            OptionalLong openId = findOpenIncidentId(hostId.getAsLong(), kind);
            if (openId.isEmpty()) {
                return false;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    UPDATE incident
                    SET state = ?, ended_at = ?
                    WHERE id = ?
                    """)) {
                ps.setString(1, IncidentRecord.STATE_RESOLVED);
                ps.setString(2, ISO_UTC.format(when));
                ps.setLong(3, openId.getAsLong());
                int updated = ps.executeUpdate();
                connection.commit();
                return updated > 0;
            }
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to resolve incident for " + host + "/" + kind, ex);
        }
    }

    /**
     * Sets {@code acknowledged_at} on all open FIRING incidents for {@code host}.
     *
     * @return number of rows updated
     */
    public synchronized int acknowledgeOpenIncidents(String host, Instant when) {
        Objects.requireNonNull(host, "host");
        Instant at = when != null ? when : Instant.now();
        try {
            OptionalLong hostId = findHostId(host);
            if (hostId.isEmpty()) {
                return 0;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    UPDATE incident
                    SET acknowledged_at = ?
                    WHERE host_id = ? AND state = ? AND ended_at IS NULL
                    """)) {
                ps.setString(1, ISO_UTC.format(at));
                ps.setLong(2, hostId.getAsLong());
                ps.setString(3, IncidentRecord.STATE_FIRING);
                int updated = ps.executeUpdate();
                connection.commit();
                return updated;
            }
        } catch (SQLException ex) {
            rollbackQuietly();
            throw new PersistenceException("Failed to acknowledge incidents for " + host, ex);
        }
    }

    /** Active (FIRING, no end) incidents, newest first. */
    public synchronized List<IncidentRecord> listActiveIncidents(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT i.id, i.host_id, hs.address, i.kind, i.severity, i.state,
                       i.started_at, i.ended_at, i.acknowledged_at,
                       i.occurrences, i.peak_value, i.details_json
                FROM incident i
                JOIN host_session hs ON hs.id = i.host_id
                WHERE i.state = ? AND i.ended_at IS NULL
                ORDER BY i.started_at DESC
                LIMIT ?
                """)) {
            ps.setString(1, IncidentRecord.STATE_FIRING);
            ps.setInt(2, limit);
            return readIncidentRows(ps);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list active incidents", ex);
        }
    }

    /** Incidents for {@code host} address, newest first. */
    public synchronized List<IncidentRecord> listIncidents(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT i.id, i.host_id, hs.address, i.kind, i.severity, i.state,
                       i.started_at, i.ended_at, i.acknowledged_at,
                       i.occurrences, i.peak_value, i.details_json
                FROM incident i
                JOIN host_session hs ON hs.id = i.host_id
                WHERE hs.address = ?
                ORDER BY i.started_at DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, limit);
            return readIncidentRows(ps);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list incidents for " + host, ex);
        }
    }

    /**
     * Mean resolved duration in seconds for {@code kind} (MTTR helper). Uses columns only — no
     * {@code details_json} parsing.
     */
    public synchronized OptionalDouble averageResolvedDurationSeconds(String kind) {
        Objects.requireNonNull(kind, "kind");
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT started_at, ended_at FROM incident
                WHERE kind = ? AND state = ? AND ended_at IS NOT NULL
                """)) {
            ps.setString(1, kind);
            ps.setString(2, IncidentRecord.STATE_RESOLVED);
            try (ResultSet rs = ps.executeQuery()) {
                double sum = 0;
                int count = 0;
                while (rs.next()) {
                    Instant start = Instant.parse(rs.getString(1));
                    Instant end = Instant.parse(rs.getString(2));
                    sum += Duration.between(start, end).toMillis() / 1000.0;
                    count++;
                }
                return count == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / count);
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to compute average duration for " + kind, ex);
        }
    }

    /**
     * Appends one telemetry sample row (P16-020 / P27-001). Columns are SSOT; dump rebuilds JSON.
     * Host remains the address string (no FK in v8).
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
        }
        Integer existingVersion = readSchemaVersionOrNull();
        if (existingVersion != null && existingVersion != SCHEMA_VERSION) {
            throw new PersistenceException("Unsupported session DB schema version "
                    + existingVersion
                    + " (required "
                    + SCHEMA_VERSION
                    + "). Delete the database file and recreate.");
        }
        try (Statement statement = connection.createStatement()) {
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
            createTelemetryTables(statement);
        }
        if (existingVersion == null) {
            seedSchemaVersion();
        }
        connection.commit();
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

    private OptionalLong findHostId(String address) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM host_session WHERE address = ?")) {
            ps.setString(1, address);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(rs.getLong(1));
            }
        }
    }

    private long requireHostId(String address) throws SQLException {
        OptionalLong id = findHostId(address);
        if (id.isEmpty()) {
            throw new PersistenceException("Unknown host address for persistence event: " + address);
        }
        return id.getAsLong();
    }

    private long upsertHost(String address, boolean enabled, String now) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO host_session(address, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(address) DO UPDATE SET
                    enabled = excluded.enabled,
                    updated_at = excluded.updated_at
                """)) {
            ps.setString(1, address);
            ps.setInt(2, enabled ? 1 : 0);
            ps.setString(3, now);
            ps.setString(4, now);
            ps.executeUpdate();
        }
        return findHostId(address)
                .orElseThrow(() -> new PersistenceException("Failed to resolve host id after upsert: " + address));
    }

    private void clearHostChildren(long hostId) throws SQLException {
        try (PreparedStatement route = connection.prepareStatement("DELETE FROM session_route_hop WHERE host_id = ?");
                PreparedStatement ping =
                        connection.prepareStatement("DELETE FROM session_ping_sample WHERE host_id = ?");
                PreparedStatement rtt =
                        connection.prepareStatement("DELETE FROM session_hop_rtt_sample WHERE host_id = ?");
                PreparedStatement stats =
                        connection.prepareStatement("DELETE FROM session_hop_stats WHERE host_id = ?")) {
            route.setLong(1, hostId);
            route.executeUpdate();
            ping.setLong(1, hostId);
            ping.executeUpdate();
            rtt.setLong(1, hostId);
            rtt.executeUpdate();
            stats.setLong(1, hostId);
            stats.executeUpdate();
        }
    }

    private List<HopNode> loadRouteHops(long hostId, String routeKind) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT hop, ip, ping_ms, is_timeout FROM session_route_hop
                WHERE host_id = ? AND route_kind = ?
                ORDER BY hop ASC
                """)) {
            ps.setLong(1, hostId);
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

    private Map<Integer, HopNode> loadLastKnown(long hostId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT hop, ip, ping_ms, is_timeout FROM session_route_hop
                WHERE host_id = ? AND route_kind = ?
                ORDER BY hop ASC
                """)) {
            ps.setLong(1, hostId);
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

    private Map<String, List<Double>> loadPingHistory(long hostId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT ip, seq, rtt_ms FROM session_ping_sample
                WHERE host_id = ?
                ORDER BY ip ASC, seq ASC
                """)) {
            ps.setLong(1, hostId);
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

    private Map<Integer, HopProbeStats> loadHopStats(long hostId) throws SQLException {
        Map<Integer, List<Double>> samplesByHop = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT hop, seq, rtt_ms FROM session_hop_rtt_sample
                WHERE host_id = ?
                ORDER BY hop ASC, seq ASC
                """)) {
            ps.setLong(1, hostId);
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
                WHERE host_id = ?
                ORDER BY hop ASC
                """)) {
            ps.setLong(1, hostId);
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

    private void insertRouteHops(long hostId, String routeKind, List<HopNode> hops) throws SQLException {
        if (hops == null || hops.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO session_route_hop(host_id, route_kind, hop, ip, ping_ms, is_timeout)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (HopNode node : hops) {
                bindHop(ps, hostId, routeKind, node);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertLastKnown(long hostId, Map<Integer, HopNode> lastKnown) throws SQLException {
        if (lastKnown == null || lastKnown.isEmpty()) {
            return;
        }
        List<Map.Entry<Integer, HopNode>> entries = new ArrayList<>(lastKnown.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO session_route_hop(host_id, route_kind, hop, ip, ping_ms, is_timeout)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (Map.Entry<Integer, HopNode> entry : entries) {
                bindHop(ps, hostId, ROUTE_LAST_KNOWN, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertPingHistory(long hostId, Map<String, List<Double>> history) throws SQLException {
        if (history == null || history.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO session_ping_sample(host_id, ip, seq, rtt_ms)
                VALUES (?, ?, ?, ?)
                """)) {
            for (Map.Entry<String, List<Double>> entry : history.entrySet()) {
                List<Double> samples = entry.getValue();
                if (samples == null) {
                    continue;
                }
                for (int i = 0; i < samples.size(); i++) {
                    ps.setLong(1, hostId);
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, i);
                    ps.setDouble(4, samples.get(i));
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void insertHopStats(long hostId, Map<Integer, HopProbeStats> stats) throws SQLException {
        if (stats == null || stats.isEmpty()) {
            return;
        }
        try (PreparedStatement statsPs = connection.prepareStatement(
                        """
                        INSERT INTO session_hop_stats(host_id, hop, probes, successes)
                        VALUES (?, ?, ?, ?)
                        """);
                PreparedStatement rttPs = connection.prepareStatement(
                        """
                        INSERT INTO session_hop_rtt_sample(host_id, hop, seq, rtt_ms)
                        VALUES (?, ?, ?, ?)
                        """)) {
            for (Map.Entry<Integer, HopProbeStats> entry : stats.entrySet()) {
                HopProbeStats item = entry.getValue();
                if (item == null) {
                    continue;
                }
                int hop = entry.getKey();
                statsPs.setLong(1, hostId);
                statsPs.setInt(2, hop);
                statsPs.setInt(3, item.getProbes());
                statsPs.setInt(4, item.getSuccesses());
                statsPs.addBatch();
                List<Double> samples = item.getRttSamples();
                for (int i = 0; i < samples.size(); i++) {
                    rttPs.setLong(1, hostId);
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

    private static void bindHop(PreparedStatement ps, long hostId, String routeKind, HopNode node) throws SQLException {
        ps.setLong(1, hostId);
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

    private static List<PersistenceEventRecord> readEventRows(PreparedStatement ps) throws SQLException {
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

    private OptionalLong findOpenIncidentId(long hostId, String kind) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT id FROM incident
                WHERE host_id = ? AND kind = ? AND state = ? AND ended_at IS NULL
                ORDER BY started_at DESC
                LIMIT 1
                """)) {
            ps.setLong(1, hostId);
            ps.setString(2, kind);
            ps.setString(3, IncidentRecord.STATE_FIRING);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(rs.getLong(1));
            }
        }
    }

    private static List<IncidentRecord> readIncidentRows(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<IncidentRecord> rows = new ArrayList<>();
            while (rs.next()) {
                String ended = rs.getString(8);
                String acked = rs.getString(9);
                Object peak = rs.getObject(11);
                rows.add(new IncidentRecord(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        Instant.parse(rs.getString(7)),
                        ended == null ? null : Instant.parse(ended),
                        acked == null ? null : Instant.parse(acked),
                        rs.getInt(10),
                        peak == null ? null : rs.getDouble(11),
                        rs.getString(12)));
            }
            return List.copyOf(rows);
        }
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
        try (PreparedStatement ps = connection.prepareStatement("SELECT version FROM schema_meta LIMIT 1");
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return null;
        }
    }

    private void seedSchemaVersion() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO schema_meta(version) VALUES (?)")) {
            ps.setInt(1, SCHEMA_VERSION);
            ps.executeUpdate();
        }
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

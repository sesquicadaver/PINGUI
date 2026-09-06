package io.pingui.persistence;

import io.pingui.model.Models.HostSessionData;
import io.pingui.probe.ProbeOutcome;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * SQLite persistence for per-host session metrics (P11-010 / P27 / P30 / P32) and telemetry
 * archive (P16).
 *
 * <p>Schema v14: additive {@code metric_rollup} counters ({@code *_samples}/{@code *_sum});
 * averages on read. Public API remains address-keyed. Opens migrate {@code 12 → 13 → 14}
 * in-place (P33-007); versions older than v12 are still rejected.
 *
 * <p>This class is the public facade: it owns the {@link Connection}, manages the transaction
 * boundary via {@link #inTransaction}, and delegates all SQL work to package-private repositories.
 */
public final class SessionDatabase implements AutoCloseable {

    /** How the SQLite file is opened (P30-006). */
    public enum OpenMode {
        /** Normal monitor / GUI / retention writes. */
        READ_WRITE,
        /** Long export or integrity_check — no DDL or mutations. */
        READ_ONLY
    }

    /** Current Java session DB schema (v14 = accurate metric_rollup + atomic retention, P32-004). */
    public static final int SCHEMA_VERSION = SchemaManager.SCHEMA_VERSION;

    /** Minimum version that can be migrated forward (v12 → v13 → v14, P33-007). */
    public static final int MIN_MIGRATE_FROM = SchemaManager.MIN_MIGRATE_FROM;

    private final Path path;
    private final OpenMode openMode;
    private final DbCommit dbCommit;
    private final SchemaManager schemaManager;
    private final SessionStateRepository stateRepo;
    private final HistoryRepository historyRepo;

    /** Opens {@code path} for read/write (creates schema when missing). */
    public SessionDatabase(Path path) {
        this(path, OpenMode.READ_WRITE);
    }

    /** Opens {@code path} for read/write (creates schema when missing). */
    public SessionDatabase(String path) {
        this(Path.of(path));
    }

    /** Read-only open for export / integrity_check while daemon holds the write lock (P30-006). */
    public static SessionDatabase readOnly(Path path) {
        return new SessionDatabase(path, OpenMode.READ_ONLY);
    }

    /** Opens {@code path} with the given {@code openMode}. */
    public SessionDatabase(Path path, OpenMode openMode) {
        this.path = Objects.requireNonNull(path, "path");
        this.openMode = Objects.requireNonNull(openMode, "openMode");
        if (openMode == OpenMode.READ_WRITE) {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (java.io.IOException ex) {
                throw new PersistenceException("Failed to create database directory: " + path, ex);
            }
        } else if (!Files.isRegularFile(path)) {
            throw new PersistenceException("Session database file not found (read-only): " + path);
        }
        Connection opened;
        try {
            opened = DriverManager.getConnection(jdbcUrl(path, openMode));
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to open session database: " + path, ex);
        }
        try {
            opened.setAutoCommit(false);
            try (Statement pragma = opened.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
                pragma.execute("PRAGMA busy_timeout = 5000");
            }
            this.dbCommit = new DbCommit(opened);
            this.schemaManager = new SchemaManager(dbCommit, path, openMode);
            this.stateRepo = new SessionStateRepository(dbCommit);
            this.historyRepo = new HistoryRepository(dbCommit, stateRepo);
            schemaManager.initSchema();
        } catch (SQLException ex) {
            closeQuietly(opened);
            throw new PersistenceException("Failed to open session database: " + path, ex);
        } catch (PersistenceException ex) {
            closeQuietly(opened);
            throw ex;
        }
    }

    private static String jdbcUrl(Path path, OpenMode mode) {
        String absolute = path.toAbsolutePath().toString();
        if (mode == OpenMode.READ_ONLY) {
            return "jdbc:sqlite:file:" + absolute + "?mode=ro";
        }
        return "jdbc:sqlite:" + absolute;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the database file path. */
    public Path path() {
        return path;
    }

    /** Returns the open mode. */
    public OpenMode openMode() {
        return openMode;
    }

    // -------------------------------------------------------------------------
    // Connection-level operations
    // -------------------------------------------------------------------------

    /**
     * Runs {@code PRAGMA integrity_check} (P30-006). Safe on read-only connections.
     *
     * @return {@code ok=true} when SQLite reports a single {@code ok} row
     */
    public synchronized IntegrityCheckResult integrityCheck() {
        try (Statement statement = dbCommit.connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
            List<String> messages = new ArrayList<>();
            while (rs.next()) {
                messages.add(rs.getString(1));
            }
            boolean ok = messages.size() == 1 && "ok".equalsIgnoreCase(messages.get(0));
            return new IntegrityCheckResult(ok, messages);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to run integrity_check on " + path, ex);
        }
    }

    /**
     * Runs {@code work} with deferred commits — one commit on success, full rollback on failure
     * (P32-004 atomic retention).
     */
    public synchronized <T> T inTransaction(TransactionWork<T> work) {
        Objects.requireNonNull(work, "work");
        if (openMode == OpenMode.READ_ONLY) {
            throw new PersistenceException("Cannot begin a write transaction on a read-only session database");
        }
        if (dbCommit.deferCommit) {
            throw new PersistenceException("Nested SessionDatabase transactions are not supported");
        }
        dbCommit.deferCommit = true;
        try {
            T result = work.execute();
            dbCommit.connection.commit();
            return result;
        } catch (PersistenceException ex) {
            dbCommit.rollbackQuietly();
            throw ex;
        } catch (RuntimeException ex) {
            dbCommit.rollbackQuietly();
            throw ex;
        } catch (Exception ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Session transaction failed", ex);
        } finally {
            dbCommit.deferCommit = false;
        }
    }

    /** Functional interface for {@link #inTransaction}. */
    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute() throws Exception;
    }

    @Override
    public synchronized void close() {
        try {
            dbCommit.connection.close();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to close session database", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    /** Returns the schema version stored in {@code schema_meta}. */
    public synchronized int schemaVersion() {
        try {
            return schemaManager.schemaVersion();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to read schema version", ex);
        }
    }

    // -------------------------------------------------------------------------
    // host_session delegates
    // -------------------------------------------------------------------------

    /**
     * Resolves the stable integer id for {@code address}, or empty when the host row is absent
     * (tests / diagnostics).
     */
    public synchronized OptionalLong hostId(String address) {
        Objects.requireNonNull(address, "address");
        return stateRepo.hostId(address);
    }

    /** Loads persisted metrics for {@code host} address, or {@code null} when absent. */
    public synchronized HostSessionData load(String host) {
        Objects.requireNonNull(host, "host");
        try {
            return stateRepo.load(host);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to load host session: " + host, ex);
        }
    }

    /**
     * Ensures a {@code host_session} row exists without loading hop/ping payloads (P32-005). Uses
     * {@code INSERT … ON CONFLICT DO NOTHING}.
     */
    public synchronized void ensureHostExists(String host) {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        try {
            stateRepo.ensureHostExists(host);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to ensure host row: " + host, ex);
        }
    }

    /** Upserts route/ping metrics for {@code host} address. */
    public synchronized void save(String host, HostSessionData data) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(data, "data");
        try {
            stateRepo.save(host, data);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to save host session: " + host, ex);
        }
    }

    /** Deletes the {@code host_session} row (cascades to child tables). */
    public synchronized void delete(String host) {
        Objects.requireNonNull(host, "host");
        try {
            stateRepo.delete(host);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
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
        try {
            stateRepo.rename(oldHost, newHost);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to rename host session: " + oldHost + " -> " + newHost, ex);
        }
    }

    /** Returns all host addresses with persisted session rows, sorted lexicographically. */
    public synchronized List<String> listHosts() {
        try {
            return stateRepo.listHosts();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list hosts", ex);
        }
    }

    // -------------------------------------------------------------------------
    // persistence_event delegates
    // -------------------------------------------------------------------------

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
        try {
            historyRepo.insertEvent(
                    eventType, host, profile, state, message, oldIpsJson, newIpsJson, detailJson, observedAt);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
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
        try {
            return historyRepo.deleteEventsByType(eventType);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to purge events: " + eventType.id(), ex);
        }
    }

    /** Returns number of rows for {@code eventType} (tests / diagnostics). */
    public synchronized int countEvents(PersistenceEventType eventType) {
        Objects.requireNonNull(eventType, "eventType");
        try {
            return historyRepo.countEvents(eventType);
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
        try {
            return historyRepo.listEvents(eventType, host, since, limit);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list events for " + host, ex);
        }
    }

    /**
     * Lists all discrete event types for {@code host} address since {@code since} (P29-002).
     * Newest first.
     *
     * @param limit max rows (must be &gt;= 1)
     */
    public synchronized List<PersistenceEventRecord> listHostEvents(String host, Instant since, int limit) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(since, "since");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listHostEvents(host, since, limit);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list host events for " + host, ex);
        }
    }

    // -------------------------------------------------------------------------
    // incident delegates
    // -------------------------------------------------------------------------

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
        try {
            return historyRepo.openOrRefreshIncident(host, kind, severity, startedAt, peakValue, detailsJson);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
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
        try {
            return historyRepo.resolveIncident(host, kind, endedAt);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
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
        try {
            return historyRepo.acknowledgeOpenIncidents(host, when);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to acknowledge incidents for " + host, ex);
        }
    }

    /** Active (FIRING, no end) incidents, newest first. */
    public synchronized List<IncidentRecord> listActiveIncidents(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listActiveIncidents(limit);
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
        try {
            return historyRepo.listIncidents(host, limit);
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
        try {
            return historyRepo.averageResolvedDurationSeconds(kind);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to compute average duration for " + kind, ex);
        }
    }

    // -------------------------------------------------------------------------
    // poll_result delegates
    // -------------------------------------------------------------------------

    /**
     * Inserts one finished-poll aggregate (P30-003). {@code route_id} reserved for P30-004
     * (nullable).
     *
     * @return row id
     */
    public synchronized long insertPollResult(
            String host,
            Instant observedAt,
            String probeMode,
            Boolean reachable,
            Double terminalRttMs,
            Double jitterMs,
            Double lossPercent,
            Double durationMs,
            Long routeId,
            String errorCode,
            ProbeOutcome probeOutcome,
            boolean targetSampled) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(probeMode, "probeMode");
        try {
            return historyRepo.insertPollResult(
                    host,
                    observedAt,
                    probeMode,
                    reachable,
                    terminalRttMs,
                    jitterMs,
                    lossPercent,
                    durationMs,
                    routeId,
                    errorCode,
                    probeOutcome,
                    targetSampled);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to insert poll_result for " + host, ex);
        }
    }

    /** Newest-first poll aggregates for {@code host} address. */
    public synchronized List<PollResultRecord> listPollResults(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listPollResults(host, limit);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list poll_result for " + host, ex);
        }
    }

    /** Count of poll_result rows (tests / diagnostics). */
    public synchronized int countPollResults() {
        try {
            return historyRepo.countPollResults();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to count poll_result", ex);
        }
    }

    /** All poll_result rows with {@code observed_at} strictly before {@code cutoff} (oldest first). */
    public synchronized List<PollResultRecord> listPollResultsBefore(Instant cutoff) {
        return listPollResultsBefore(cutoff, Integer.MAX_VALUE);
    }

    /** Oldest poll_result rows before {@code cutoff}, capped at {@code limit} (P33-007). */
    public synchronized List<PollResultRecord> listPollResultsBefore(Instant cutoff, int limit) {
        Objects.requireNonNull(cutoff, "cutoff");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listPollResultsBefore(cutoff, limit);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list poll_result before " + DbCommit.ISO_UTC.format(cutoff), ex);
        }
    }

    /** Deletes poll_result rows with {@code observed_at} strictly before {@code cutoff}. */
    public synchronized int deletePollResultsBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        try {
            return historyRepo.deletePollResultsBefore(cutoff);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException(
                    "Failed to delete poll_result before " + DbCommit.ISO_UTC.format(cutoff), ex);
        }
    }

    /** Deletes {@code poll_result} rows by id (chunked retention, P33-007). */
    public synchronized int deletePollResultsByIds(List<Long> ids) {
        try {
            return historyRepo.deletePollResultsByIds(ids);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to delete poll_result by ids", ex);
        }
    }

    // -------------------------------------------------------------------------
    // route delegates
    // -------------------------------------------------------------------------

    /**
     * Inserts or refreshes a deduplicated route for {@code host} (P30-004). Same signature bumps
     * {@code seen_count} and {@code last_seen}.
     *
     * @return route row id
     */
    public synchronized long upsertRoute(String host, String signature, String hopsJson, Instant seenAt) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(hopsJson, "hopsJson");
        if (signature.isBlank()) {
            throw new IllegalArgumentException("signature must be non-blank");
        }
        try {
            return historyRepo.upsertRoute(host, signature, hopsJson, seenAt);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to upsert route for " + host, ex);
        }
    }

    /** Routes for {@code host}, newest {@code last_seen} first. */
    public synchronized List<RouteRecord> listRoutes(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listRoutes(host, limit);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list routes for " + host, ex);
        }
    }

    /** Count of route rows (tests / diagnostics). */
    public synchronized int countRoutes() {
        try {
            return historyRepo.countRoutes();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to count routes", ex);
        }
    }

    // -------------------------------------------------------------------------
    // metric_rollup delegates
    // -------------------------------------------------------------------------

    /**
     * Upserts a metric rollup bucket with additive counters (P32-004). Averages are derived on
     * read.
     */
    public synchronized void upsertMetricRollup(
            long hostId,
            Instant bucketStart,
            int bucketSizeSeconds,
            int sampleCount,
            int reachableSamples,
            int reachableCount,
            int rttSamples,
            double rttSum,
            Double rttMin,
            Double rttMax,
            int lossSamples,
            double lossSum) {
        Objects.requireNonNull(bucketStart, "bucketStart");
        if (bucketSizeSeconds < 1) {
            throw new IllegalArgumentException("bucketSizeSeconds must be >= 1");
        }
        if (sampleCount < 1) {
            throw new IllegalArgumentException("sampleCount must be >= 1");
        }
        if (reachableSamples < 0
                || reachableCount < 0
                || rttSamples < 0
                || lossSamples < 0
                || reachableCount > reachableSamples) {
            throw new IllegalArgumentException("invalid rollup counters");
        }
        try {
            historyRepo.upsertMetricRollup(
                    hostId,
                    bucketStart,
                    bucketSizeSeconds,
                    sampleCount,
                    reachableSamples,
                    reachableCount,
                    rttSamples,
                    rttSum,
                    rttMin,
                    rttMax,
                    lossSamples,
                    lossSum);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to upsert metric_rollup for host_id=" + hostId, ex);
        }
    }

    /** Rollups for {@code host} address and bucket size, newest bucket first. */
    public synchronized List<MetricRollupRecord> listMetricRollups(String host, int bucketSizeSeconds, int limit) {
        Objects.requireNonNull(host, "host");
        if (bucketSizeSeconds < 1) {
            throw new IllegalArgumentException("bucketSizeSeconds must be >= 1");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listMetricRollups(host, bucketSizeSeconds, limit);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list metric_rollup for " + host, ex);
        }
    }

    /** Rollups of {@code bucketSizeSeconds} with {@code bucket_start} strictly before {@code cutoff}. */
    public synchronized List<MetricRollupRecord> listMetricRollupsBefore(int bucketSizeSeconds, Instant cutoff) {
        return listMetricRollupsBefore(bucketSizeSeconds, cutoff, Integer.MAX_VALUE);
    }

    /** Oldest metric_rollup rows before {@code cutoff}, capped at {@code limit} (P33-007). */
    public synchronized List<MetricRollupRecord> listMetricRollupsBefore(
            int bucketSizeSeconds, Instant cutoff, int limit) {
        Objects.requireNonNull(cutoff, "cutoff");
        if (bucketSizeSeconds < 1) {
            throw new IllegalArgumentException("bucketSizeSeconds must be >= 1");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listMetricRollupsBefore(bucketSizeSeconds, cutoff, limit);
        } catch (SQLException ex) {
            throw new PersistenceException(
                    "Failed to list metric_rollup before " + DbCommit.ISO_UTC.format(cutoff), ex);
        }
    }

    /** Deletes metric_rollup rows for {@code bucketSizeSeconds} with start strictly before cutoff. */
    public synchronized int deleteMetricRollupsBefore(int bucketSizeSeconds, Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        if (bucketSizeSeconds < 1) {
            throw new IllegalArgumentException("bucketSizeSeconds must be >= 1");
        }
        try {
            return historyRepo.deleteMetricRollupsBefore(bucketSizeSeconds, cutoff);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException(
                    "Failed to delete metric_rollup before " + DbCommit.ISO_UTC.format(cutoff), ex);
        }
    }

    /** Deletes specific metric_rollup primary keys (chunked retention, P33-007). */
    public synchronized int deleteMetricRollups(List<MetricRollupRecord> rows) {
        try {
            return historyRepo.deleteMetricRollups(rows);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to delete metric_rollup rows", ex);
        }
    }

    /** Count of metric_rollup rows (tests / diagnostics). */
    public synchronized int countMetricRollups() {
        try {
            return historyRepo.countMetricRollups();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to count metric_rollup", ex);
        }
    }

    // -------------------------------------------------------------------------
    // telemetry delegates
    // -------------------------------------------------------------------------

    /**
     * Appends one telemetry sample row (P16-020 / P27-001). Columns are SSOT; dump rebuilds JSON.
     * Host remains the address string (no FK in v8).
     */
    public synchronized void insertTelemetrySample(MetricSample sample) {
        Objects.requireNonNull(sample, "sample");
        try {
            historyRepo.insertTelemetrySample(sample);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to insert telemetry sample for " + sample.host(), ex);
        }
    }

    /** Appends one telemetry event row (P16-020 / P27-001). */
    public synchronized void insertTelemetryEvent(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            historyRepo.insertTelemetryEvent(event);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException("Failed to insert telemetry event for " + event.host(), ex);
        }
    }

    /** Newest-first samples for {@code host} (tests / diagnostics / dump). */
    public synchronized List<MetricSample> listTelemetrySamples(String host, int limit) {
        Objects.requireNonNull(host, "host");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        try {
            return historyRepo.listTelemetrySamples(host, limit);
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
        try {
            return historyRepo.listTelemetryEvents(host, limit);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list telemetry events for " + host, ex);
        }
    }

    /** Oldest-first samples for full archive dump (P16-023). */
    public synchronized List<MetricSample> listAllTelemetrySamples() {
        try {
            return historyRepo.listAllTelemetrySamples();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list all telemetry samples", ex);
        }
    }

    /** Oldest-first events for full archive dump (P16-023). */
    public synchronized List<TelemetryEvent> listAllTelemetryEvents() {
        try {
            return historyRepo.listAllTelemetryEvents();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to list all telemetry events", ex);
        }
    }

    /** Count of telemetry_sample rows (tests / diagnostics). */
    public synchronized int countTelemetrySamples() {
        try {
            return historyRepo.countTelemetrySamples();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to count telemetry_sample", ex);
        }
    }

    /** Count of telemetry_event rows (tests / diagnostics). */
    public synchronized int countTelemetryEvents() {
        try {
            return historyRepo.countTelemetryEvents();
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to count telemetry_event", ex);
        }
    }

    /**
     * Deletes telemetry samples with {@code observed_at} strictly before {@code cutoff}
     * (P16-022).
     *
     * @return number of deleted sample rows
     */
    public synchronized int deleteTelemetrySamplesBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        try {
            return historyRepo.deleteTelemetrySamplesBefore(cutoff);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException(
                    "Failed to purge telemetry_sample before " + DbCommit.ISO_UTC.format(cutoff), ex);
        }
    }

    /**
     * Deletes telemetry events with {@code observed_at} strictly before {@code cutoff} (P16-022).
     *
     * @return number of deleted event rows
     */
    public synchronized int deleteTelemetryEventsBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        try {
            return historyRepo.deleteTelemetryEventsBefore(cutoff);
        } catch (SQLException ex) {
            dbCommit.rollbackQuietly();
            throw new PersistenceException(
                    "Failed to purge telemetry_event before " + DbCommit.ISO_UTC.format(cutoff), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Private static utilities
    // -------------------------------------------------------------------------

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Best-effort so Windows can delete @TempDir files after failed open.
        }
    }
}

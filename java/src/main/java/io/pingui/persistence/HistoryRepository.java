package io.pingui.persistence;

import io.pingui.probe.ProbeOutcome;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryEvent;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Read/write for history tables: {@code persistence_event}, {@code incident},
 * {@code poll_result}, {@code route}, {@code metric_rollup}, {@code telemetry_sample},
 * {@code telemetry_event}.
 *
 * <p>Host address → id resolution is delegated to {@link SessionStateRepository}. Package-private
 * — all external access goes through {@link SessionDatabase}.
 */
final class HistoryRepository {

    private final DbCommit commit;
    private final SessionStateRepository stateRepo;

    HistoryRepository(DbCommit commit, SessionStateRepository stateRepo) {
        this.commit = commit;
        this.stateRepo = stateRepo;
    }

    // -------------------------------------------------------------------------
    // persistence_event
    // -------------------------------------------------------------------------

    /**
     * Appends a discrete event row (P11-011+ / P27-002 typed columns). Requires an existing host
     * session row.
     */
    void insertEvent(
            PersistenceEventType eventType,
            String host,
            String profile,
            String state,
            String message,
            String oldIpsJson,
            String newIpsJson,
            String detailJson,
            Instant observedAt)
            throws SQLException {
        Instant when = observedAt != null ? observedAt : Instant.now();
        String profileValue = profile == null || profile.isBlank() ? null : profile;
        long hostId = stateRepo.requireHostId(host);
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
            ps.setString(9, DbCommit.ISO_UTC.format(when));
            ps.executeUpdate();
        }
        commit.maybeCommit();
    }

    /** Deletes all rows of {@code eventType}; used by purge policy (P11-014). */
    int deleteEventsByType(PersistenceEventType eventType) throws SQLException {
        try (PreparedStatement ps =
                commit.connection.prepareStatement("DELETE FROM persistence_event WHERE event_type = ?")) {
            ps.setString(1, eventType.id());
            int deleted = ps.executeUpdate();
            commit.maybeCommit();
            return deleted;
        }
    }

    /** Returns number of rows for {@code eventType} (tests / diagnostics). */
    int countEvents(PersistenceEventType eventType) throws SQLException {
        try (PreparedStatement ps =
                commit.connection.prepareStatement("SELECT COUNT(*) FROM persistence_event WHERE event_type = ?")) {
            ps.setString(1, eventType.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Lists discrete events for {@code host} address since {@code since} (P11-020). Newest first.
     */
    List<PersistenceEventRecord> listEvents(PersistenceEventType eventType, String host, Instant since, int limit)
            throws SQLException {
        String sinceIso = DbCommit.ISO_UTC.format(since);
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    /**
     * Lists all discrete event types for {@code host} address since {@code since} (P29-002).
     * Newest first.
     */
    List<PersistenceEventRecord> listHostEvents(String host, Instant since, int limit) throws SQLException {
        String sinceIso = DbCommit.ISO_UTC.format(since);
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    // -------------------------------------------------------------------------
    // incident
    // -------------------------------------------------------------------------

    /**
     * Opens or refreshes a FIRING incident for {@code host}/{@code kind} (P30-002). Same open kind
     * increments {@code occurrences}.
     *
     * @return incident id
     */
    long openOrRefreshIncident(
            String host, String kind, String severity, Instant startedAt, Double peakValue, String detailsJson)
            throws SQLException {
        Instant when = startedAt != null ? startedAt : Instant.now();
        String details = detailsJson == null || detailsJson.isBlank() ? "{}" : detailsJson;
        long hostId = stateRepo.requireHostId(host);
        OptionalLong openId = findOpenIncidentId(hostId, kind);
        if (openId.isPresent()) {
            try (PreparedStatement ps = commit.connection.prepareStatement(
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
            commit.maybeCommit();
            return openId.getAsLong();
        }
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
            ps.setString(5, DbCommit.ISO_UTC.format(when));
            if (peakValue == null) {
                ps.setObject(6, null);
            } else {
                ps.setDouble(6, peakValue);
            }
            ps.setString(7, details);
            ps.executeUpdate();
        }
        long id;
        try (PreparedStatement ps = commit.connection.prepareStatement("SELECT last_insert_rowid()");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            id = rs.getLong(1);
        }
        commit.maybeCommit();
        return id;
    }

    /**
     * Resolves the open FIRING incident for {@code host}/{@code kind}, if any.
     *
     * @return true when a row was updated
     */
    boolean resolveIncident(String host, String kind, Instant endedAt) throws SQLException {
        Instant when = endedAt != null ? endedAt : Instant.now();
        OptionalLong hostId = stateRepo.findHostId(host);
        if (hostId.isEmpty()) {
            return false;
        }
        OptionalLong openId = findOpenIncidentId(hostId.getAsLong(), kind);
        if (openId.isEmpty()) {
            return false;
        }
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                UPDATE incident
                SET state = ?, ended_at = ?
                WHERE id = ?
                """)) {
            ps.setString(1, IncidentRecord.STATE_RESOLVED);
            ps.setString(2, DbCommit.ISO_UTC.format(when));
            ps.setLong(3, openId.getAsLong());
            int updated = ps.executeUpdate();
            commit.maybeCommit();
            return updated > 0;
        }
    }

    /**
     * Sets {@code acknowledged_at} on all open FIRING incidents for {@code host}.
     *
     * @return number of rows updated
     */
    int acknowledgeOpenIncidents(String host, Instant when) throws SQLException {
        Instant at = when != null ? when : Instant.now();
        OptionalLong hostId = stateRepo.findHostId(host);
        if (hostId.isEmpty()) {
            return 0;
        }
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                UPDATE incident
                SET acknowledged_at = ?
                WHERE host_id = ? AND state = ? AND ended_at IS NULL
                """)) {
            ps.setString(1, DbCommit.ISO_UTC.format(at));
            ps.setLong(2, hostId.getAsLong());
            ps.setString(3, IncidentRecord.STATE_FIRING);
            int updated = ps.executeUpdate();
            commit.maybeCommit();
            return updated;
        }
    }

    /** Active (FIRING, no end) incidents, newest first. */
    List<IncidentRecord> listActiveIncidents(int limit) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    /** Incidents for {@code host} address, newest first. */
    List<IncidentRecord> listIncidents(String host, int limit) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    /**
     * Mean resolved duration in seconds for {@code kind} (MTTR helper). Uses columns only — no
     * {@code details_json} parsing.
     */
    OptionalDouble averageResolvedDurationSeconds(String kind) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    // -------------------------------------------------------------------------
    // poll_result
    // -------------------------------------------------------------------------

    /**
     * Inserts one finished-poll aggregate (P30-003). {@code route_id} reserved for P30-004
     * (nullable).
     *
     * @return row id
     */
    long insertPollResult(
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
            boolean targetSampled)
            throws SQLException {
        ProbeOutcome outcome = probeOutcome != null ? probeOutcome : ProbeOutcome.NETWORK_ERROR;
        Instant when = observedAt != null ? observedAt : Instant.now();
        long hostId = stateRepo.requireHostId(host);
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                INSERT INTO poll_result(
                    host_id, observed_at, probe_mode, reachable, terminal_rtt_ms,
                    jitter_ms, loss_percent, duration_ms, route_id, error_code,
                    probe_outcome, target_sampled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, hostId);
            ps.setString(2, DbCommit.ISO_UTC.format(when));
            ps.setString(3, probeMode);
            if (reachable == null) {
                ps.setObject(4, null);
            } else {
                ps.setInt(4, reachable ? 1 : 0);
            }
            DbCommit.setNullableDouble(ps, 5, terminalRttMs);
            DbCommit.setNullableDouble(ps, 6, jitterMs);
            DbCommit.setNullableDouble(ps, 7, lossPercent);
            DbCommit.setNullableDouble(ps, 8, durationMs);
            if (routeId == null) {
                ps.setObject(9, null);
            } else {
                ps.setLong(9, routeId);
            }
            ps.setString(10, errorCode);
            ps.setString(11, outcome.wire());
            ps.setInt(12, targetSampled ? 1 : 0);
            ps.executeUpdate();
        }
        long id;
        try (PreparedStatement ps = commit.connection.prepareStatement("SELECT last_insert_rowid()");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            id = rs.getLong(1);
        }
        commit.maybeCommit();
        return id;
    }

    /** Newest-first poll aggregates for {@code host} address. */
    List<PollResultRecord> listPollResults(String host, int limit) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                SELECT p.id, p.host_id, hs.address, p.observed_at, p.probe_mode, p.reachable,
                       p.terminal_rtt_ms, p.jitter_ms, p.loss_percent, p.duration_ms,
                       p.route_id, p.error_code, p.probe_outcome, p.target_sampled
                FROM poll_result p
                JOIN host_session hs ON hs.id = p.host_id
                WHERE hs.address = ?
                ORDER BY p.observed_at DESC, p.id DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, limit);
            return readPollResultRows(ps);
        }
    }

    /** Count of poll_result rows (tests / diagnostics). */
    int countPollResults() throws SQLException {
        return countTable("poll_result");
    }

    /** All poll_result rows with {@code observed_at} strictly before {@code cutoff} (oldest first). */
    List<PollResultRecord> listPollResultsBefore(Instant cutoff) throws SQLException {
        String cutoffIso = DbCommit.ISO_UTC.format(cutoff);
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                SELECT p.id, p.host_id, hs.address, p.observed_at, p.probe_mode, p.reachable,
                       p.terminal_rtt_ms, p.jitter_ms, p.loss_percent, p.duration_ms,
                       p.route_id, p.error_code, p.probe_outcome, p.target_sampled
                FROM poll_result p
                JOIN host_session hs ON hs.id = p.host_id
                WHERE p.observed_at < ?
                ORDER BY p.observed_at ASC, p.id ASC
                """)) {
            ps.setString(1, cutoffIso);
            return readPollResultRows(ps);
        }
    }

    /** Deletes poll_result rows with {@code observed_at} strictly before {@code cutoff}. */
    int deletePollResultsBefore(Instant cutoff) throws SQLException {
        return deleteBefore("poll_result", cutoff);
    }

    // -------------------------------------------------------------------------
    // route
    // -------------------------------------------------------------------------

    /**
     * Inserts or refreshes a deduplicated route for {@code host} (P30-004). Same signature bumps
     * {@code seen_count} and {@code last_seen}.
     *
     * @return route row id
     */
    long upsertRoute(String host, String signature, String hopsJson, Instant seenAt) throws SQLException {
        Instant when = seenAt != null ? seenAt : Instant.now();
        String whenIso = DbCommit.ISO_UTC.format(when);
        long hostId = stateRepo.requireHostId(host);
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                INSERT INTO route(host_id, signature, hops_json, first_seen, last_seen, seen_count)
                VALUES (?, ?, ?, ?, ?, 1)
                ON CONFLICT(host_id, signature) DO UPDATE SET
                    last_seen = excluded.last_seen,
                    seen_count = seen_count + 1,
                    hops_json = excluded.hops_json
                """)) {
            ps.setLong(1, hostId);
            ps.setString(2, signature);
            ps.setString(3, hopsJson);
            ps.setString(4, whenIso);
            ps.setString(5, whenIso);
            ps.executeUpdate();
        }
        long id;
        try (PreparedStatement ps =
                commit.connection.prepareStatement("SELECT id FROM route WHERE host_id = ? AND signature = ?")) {
            ps.setLong(1, hostId);
            ps.setString(2, signature);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new PersistenceException("Failed to resolve route id after upsert for " + host);
                }
                id = rs.getLong(1);
            }
        }
        commit.maybeCommit();
        return id;
    }

    /** Routes for {@code host}, newest {@code last_seen} first. */
    List<RouteRecord> listRoutes(String host, int limit) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                SELECT r.id, r.host_id, hs.address, r.signature, r.hops_json,
                       r.first_seen, r.last_seen, r.seen_count
                FROM route r
                JOIN host_session hs ON hs.id = r.host_id
                WHERE hs.address = ?
                ORDER BY r.last_seen DESC, r.id DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, limit);
            return readRouteRows(ps);
        }
    }

    /** Count of route rows (tests / diagnostics). */
    int countRoutes() throws SQLException {
        return countTable("route");
    }

    // -------------------------------------------------------------------------
    // metric_rollup
    // -------------------------------------------------------------------------

    /**
     * Upserts a metric rollup bucket with additive counters (P32-004). Averages are derived on
     * read.
     */
    void upsertMetricRollup(
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
            double lossSum)
            throws SQLException {
        String startIso = DbCommit.ISO_UTC.format(bucketStart);
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                INSERT INTO metric_rollup(
                    host_id, bucket_start, bucket_size, sample_count,
                    reachable_samples, reachable_count,
                    rtt_samples, rtt_sum, rtt_min, rtt_max,
                    loss_samples, loss_sum)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(host_id, bucket_start, bucket_size) DO UPDATE SET
                    sample_count = metric_rollup.sample_count + excluded.sample_count,
                    reachable_samples = metric_rollup.reachable_samples + excluded.reachable_samples,
                    reachable_count = metric_rollup.reachable_count + excluded.reachable_count,
                    rtt_samples = metric_rollup.rtt_samples + excluded.rtt_samples,
                    rtt_sum = metric_rollup.rtt_sum + excluded.rtt_sum,
                    rtt_min = CASE
                        WHEN metric_rollup.rtt_min IS NULL THEN excluded.rtt_min
                        WHEN excluded.rtt_min IS NULL THEN metric_rollup.rtt_min
                        ELSE MIN(metric_rollup.rtt_min, excluded.rtt_min)
                    END,
                    rtt_max = CASE
                        WHEN metric_rollup.rtt_max IS NULL THEN excluded.rtt_max
                        WHEN excluded.rtt_max IS NULL THEN metric_rollup.rtt_max
                        ELSE MAX(metric_rollup.rtt_max, excluded.rtt_max)
                    END,
                    loss_samples = metric_rollup.loss_samples + excluded.loss_samples,
                    loss_sum = metric_rollup.loss_sum + excluded.loss_sum
                """)) {
            ps.setLong(1, hostId);
            ps.setString(2, startIso);
            ps.setInt(3, bucketSizeSeconds);
            ps.setInt(4, sampleCount);
            ps.setInt(5, reachableSamples);
            ps.setInt(6, reachableCount);
            ps.setInt(7, rttSamples);
            ps.setDouble(8, rttSum);
            DbCommit.setNullableDouble(ps, 9, rttMin);
            DbCommit.setNullableDouble(ps, 10, rttMax);
            ps.setInt(11, lossSamples);
            ps.setDouble(12, lossSum);
            ps.executeUpdate();
            commit.maybeCommit();
        }
    }

    /** Rollups for {@code host} address and bucket size, newest bucket first. */
    List<MetricRollupRecord> listMetricRollups(String host, int bucketSizeSeconds, int limit) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                SELECT m.host_id, hs.address, m.bucket_start, m.bucket_size, m.sample_count,
                       m.reachable_samples, m.reachable_count,
                       m.rtt_samples, m.rtt_sum, m.rtt_min, m.rtt_max,
                       m.loss_samples, m.loss_sum
                FROM metric_rollup m
                JOIN host_session hs ON hs.id = m.host_id
                WHERE hs.address = ? AND m.bucket_size = ?
                ORDER BY m.bucket_start DESC
                LIMIT ?
                """)) {
            ps.setString(1, host);
            ps.setInt(2, bucketSizeSeconds);
            ps.setInt(3, limit);
            return readMetricRollupRows(ps);
        }
    }

    /** Rollups of {@code bucketSizeSeconds} with {@code bucket_start} strictly before {@code cutoff}. */
    List<MetricRollupRecord> listMetricRollupsBefore(int bucketSizeSeconds, Instant cutoff) throws SQLException {
        String cutoffIso = DbCommit.ISO_UTC.format(cutoff);
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                SELECT m.host_id, hs.address, m.bucket_start, m.bucket_size, m.sample_count,
                       m.reachable_samples, m.reachable_count,
                       m.rtt_samples, m.rtt_sum, m.rtt_min, m.rtt_max,
                       m.loss_samples, m.loss_sum
                FROM metric_rollup m
                JOIN host_session hs ON hs.id = m.host_id
                WHERE m.bucket_size = ? AND m.bucket_start < ?
                ORDER BY m.bucket_start ASC
                """)) {
            ps.setInt(1, bucketSizeSeconds);
            ps.setString(2, cutoffIso);
            return readMetricRollupRows(ps);
        }
    }

    /** Deletes metric_rollup rows for {@code bucketSizeSeconds} with start strictly before cutoff. */
    int deleteMetricRollupsBefore(int bucketSizeSeconds, Instant cutoff) throws SQLException {
        String cutoffIso = DbCommit.ISO_UTC.format(cutoff);
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                DELETE FROM metric_rollup
                WHERE bucket_size = ? AND bucket_start < ?
                """)) {
            ps.setInt(1, bucketSizeSeconds);
            ps.setString(2, cutoffIso);
            int deleted = ps.executeUpdate();
            commit.maybeCommit();
            return deleted;
        }
    }

    /** Count of metric_rollup rows (tests / diagnostics). */
    int countMetricRollups() throws SQLException {
        return countTable("metric_rollup");
    }

    // -------------------------------------------------------------------------
    // telemetry_sample / telemetry_event
    // -------------------------------------------------------------------------

    /**
     * Appends one telemetry sample row (P16-020 / P27-001). Columns are SSOT; dump rebuilds JSON.
     * Host remains the address string (no FK in v8).
     */
    void insertTelemetrySample(MetricSample sample) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
            ps.setString(6, DbCommit.ISO_UTC.format(sample.timestamp()));
            ps.executeUpdate();
            commit.maybeCommit();
        }
    }

    /** Appends one telemetry event row (P16-020 / P27-001). */
    void insertTelemetryEvent(TelemetryEvent event) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
            ps.setString(7, DbCommit.ISO_UTC.format(event.timestamp()));
            ps.executeUpdate();
            commit.maybeCommit();
        }
    }

    /** Newest-first samples for {@code host} (tests / diagnostics / dump). */
    List<MetricSample> listTelemetrySamples(String host, int limit) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    /** Newest-first events for {@code host}. */
    List<TelemetryEvent> listTelemetryEvents(String host, int limit) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    /** Oldest-first samples for full archive dump (P16-023). */
    List<MetricSample> listAllTelemetrySamples() throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    /** Oldest-first events for full archive dump (P16-023). */
    List<TelemetryEvent> listAllTelemetryEvents() throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        }
    }

    /** Count of telemetry_sample rows (tests / diagnostics). */
    int countTelemetrySamples() throws SQLException {
        return countTable("telemetry_sample");
    }

    /** Count of telemetry_event rows (tests / diagnostics). */
    int countTelemetryEvents() throws SQLException {
        return countTable("telemetry_event");
    }

    /**
     * Deletes telemetry samples with {@code observed_at} strictly before {@code cutoff}
     * (P16-022).
     *
     * @return number of deleted sample rows
     */
    int deleteTelemetrySamplesBefore(Instant cutoff) throws SQLException {
        return deleteBefore("telemetry_sample", cutoff);
    }

    /**
     * Deletes telemetry events with {@code observed_at} strictly before {@code cutoff} (P16-022).
     *
     * @return number of deleted event rows
     */
    int deleteTelemetryEventsBefore(Instant cutoff) throws SQLException {
        return deleteBefore("telemetry_event", cutoff);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private OptionalLong findOpenIncidentId(long hostId, String kind) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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

    private int countTable(String table) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int deleteBefore(String table, Instant cutoff) throws SQLException {
        String iso = DbCommit.ISO_UTC.format(cutoff);
        try (PreparedStatement ps =
                commit.connection.prepareStatement("DELETE FROM " + table + " WHERE observed_at < ?")) {
            ps.setString(1, iso);
            int deleted = ps.executeUpdate();
            commit.maybeCommit();
            return deleted;
        }
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

    private static List<PollResultRecord> readPollResultRows(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<PollResultRecord> rows = new ArrayList<>();
            while (rs.next()) {
                Object reachableObj = rs.getObject(6);
                Boolean reachable = reachableObj == null ? null : rs.getInt(6) != 0;
                Object routeObj = rs.getObject(11);
                rows.add(new PollResultRecord(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getString(3),
                        Instant.parse(rs.getString(4)),
                        rs.getString(5),
                        reachable,
                        DbCommit.nullableDouble(rs, 7),
                        DbCommit.nullableDouble(rs, 8),
                        DbCommit.nullableDouble(rs, 9),
                        DbCommit.nullableDouble(rs, 10),
                        routeObj == null ? null : rs.getLong(11),
                        rs.getString(12),
                        ProbeOutcome.fromWire(rs.getString(13)),
                        rs.getInt(14) != 0));
            }
            return List.copyOf(rows);
        }
    }

    private static List<RouteRecord> readRouteRows(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<RouteRecord> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new RouteRecord(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        Instant.parse(rs.getString(6)),
                        Instant.parse(rs.getString(7)),
                        rs.getInt(8)));
            }
            return List.copyOf(rows);
        }
    }

    private static List<MetricRollupRecord> readMetricRollupRows(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<MetricRollupRecord> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new MetricRollupRecord(
                        rs.getLong(1),
                        rs.getString(2),
                        Instant.parse(rs.getString(3)),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getInt(6),
                        rs.getInt(7),
                        rs.getInt(8),
                        rs.getDouble(9),
                        DbCommit.nullableDouble(rs, 10),
                        DbCommit.nullableDouble(rs, 11),
                        rs.getInt(12),
                        rs.getDouble(13)));
            }
            return List.copyOf(rows);
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
}

package io.pingui.persistence;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HostSessionData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * CRUD for {@code host_session} and its child tables ({@code session_route_hop},
 * {@code session_ping_sample}, {@code session_hop_stats}, {@code session_hop_rtt_sample}).
 *
 * <p>Package-private — all external access goes through {@link SessionDatabase}.
 */
final class SessionStateRepository {

    private static final String ROUTE_CURRENT = "current";
    private static final String ROUTE_PREVIOUS = "previous";
    private static final String ROUTE_LAST_KNOWN = "last_known";

    private final DbCommit commit;

    SessionStateRepository(DbCommit commit) {
        this.commit = commit;
    }

    // -------------------------------------------------------------------------
    // Host lookup helpers (also used by HistoryRepository)
    // -------------------------------------------------------------------------

    /**
     * Resolves the stable integer id for {@code address}, or {@link OptionalLong#empty()} when
     * absent.
     */
    OptionalLong findHostId(String address) throws SQLException {
        try (PreparedStatement ps =
                commit.connection.prepareStatement("SELECT id FROM host_session WHERE address = ?")) {
            ps.setString(1, address);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(rs.getLong(1));
            }
        }
    }

    /**
     * Resolves host id or throws {@link PersistenceException} when the row is absent (used by
     * history writes that require an existing host).
     */
    long requireHostId(String address) throws SQLException {
        OptionalLong id = findHostId(address);
        if (id.isEmpty()) {
            throw new PersistenceException("Unknown host address for persistence event: " + address);
        }
        return id.getAsLong();
    }

    // -------------------------------------------------------------------------
    // Public-facing operations (delegated from SessionDatabase)
    // -------------------------------------------------------------------------

    /**
     * Resolves the stable integer id for {@code address}, or empty when absent (tests /
     * diagnostics). Wraps {@link SQLException} in {@link PersistenceException}.
     */
    OptionalLong hostId(String address) {
        try {
            return findHostId(address);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to resolve host id: " + address, ex);
        }
    }

    /** Loads persisted metrics for {@code host} address, or {@code null} when absent. */
    HostSessionData load(String host) throws SQLException {
        OptionalLong id = findHostId(host);
        if (id.isEmpty()) {
            return null;
        }
        long hostId = id.getAsLong();
        try (PreparedStatement ps =
                commit.connection.prepareStatement("SELECT enabled FROM host_session WHERE id = ?")) {
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
    }

    /**
     * Ensures a {@code host_session} row exists without loading hop/ping payloads (P32-005). Uses
     * {@code INSERT … ON CONFLICT DO NOTHING}.
     */
    void ensureHostExists(String host) throws SQLException {
        String now = DbCommit.ISO_UTC.format(Instant.now());
        try (PreparedStatement ps = commit.connection.prepareStatement(
                """
                INSERT INTO host_session(address, enabled, created_at, updated_at)
                VALUES (?, 1, ?, ?)
                ON CONFLICT(address) DO NOTHING
                """)) {
            ps.setString(1, host);
            ps.setString(2, now);
            ps.setString(3, now);
            ps.executeUpdate();
            commit.maybeCommit();
        }
    }

    /** Upserts route/ping metrics for {@code host} address. */
    void save(String host, HostSessionData data) throws SQLException {
        String now = DbCommit.ISO_UTC.format(Instant.now());
        long hostId = upsertHost(host, data.isEnabled(), now);
        clearHostChildren(hostId);
        insertRouteHops(hostId, ROUTE_CURRENT, data.getCurrentRoute());
        insertRouteHops(hostId, ROUTE_PREVIOUS, data.getPreviousRoute());
        insertLastKnown(hostId, data.getLastKnownByHop());
        insertPingHistory(hostId, data.getPingHistory());
        insertHopStats(hostId, data.getHopStats());
        commit.maybeCommit();
    }

    /** Deletes the {@code host_session} row (cascades to child tables). */
    void delete(String host) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement("DELETE FROM host_session WHERE address = ?")) {
            ps.setString(1, host);
            ps.executeUpdate();
            commit.maybeCommit();
        }
    }

    /**
     * Renames the host address in place. Child rows and {@code persistence_event} keep the same
     * {@code host_id} (no cascade rewrite).
     */
    void rename(String oldHost, String newHost) throws SQLException {
        if (oldHost.equals(newHost)) {
            return;
        }
        String now = DbCommit.ISO_UTC.format(Instant.now());
        OptionalLong id = findHostId(oldHost);
        if (id.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        commit.maybeCommit();
    }

    /** Returns all host addresses with persisted session rows, sorted lexicographically. */
    List<String> listHosts() throws SQLException {
        try (PreparedStatement ps =
                        commit.connection.prepareStatement("SELECT address FROM host_session ORDER BY address");
                ResultSet rs = ps.executeQuery()) {
            List<String> hosts = new ArrayList<>();
            while (rs.next()) {
                hosts.add(rs.getString(1));
            }
            return List.copyOf(hosts);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private long upsertHost(String address, boolean enabled, String now) throws SQLException {
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement route =
                        commit.connection.prepareStatement("DELETE FROM session_route_hop WHERE host_id = ?");
                PreparedStatement ping =
                        commit.connection.prepareStatement("DELETE FROM session_ping_sample WHERE host_id = ?");
                PreparedStatement rtt =
                        commit.connection.prepareStatement("DELETE FROM session_hop_rtt_sample WHERE host_id = ?");
                PreparedStatement stats =
                        commit.connection.prepareStatement("DELETE FROM session_hop_stats WHERE host_id = ?")) {
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement ps = commit.connection.prepareStatement(
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
        try (PreparedStatement statsPs = commit.connection.prepareStatement(
                        """
                        INSERT INTO session_hop_stats(host_id, hop, probes, successes)
                        VALUES (?, ?, ?, ?)
                        """);
                PreparedStatement rttPs = commit.connection.prepareStatement(
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
}

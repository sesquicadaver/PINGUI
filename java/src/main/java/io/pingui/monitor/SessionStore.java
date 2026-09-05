package io.pingui.monitor;

import io.pingui.config.ConfigError;
import io.pingui.config.HostEntry;
import io.pingui.config.HostsConfig;
import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.HostSessionData;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.SessionDatabase;
import io.pingui.persistence.timeseries.PingSample;
import io.pingui.persistence.timeseries.RouteEvent;
import io.pingui.persistence.timeseries.TimeSeriesBackend;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** In-memory session storage for route and ping metrics; optional SQLite persistence (P11-011 / P32-005). */
public final class SessionStore implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SessionStore.class);
    public static final int MAX_PING_SAMPLES = 50;

    private final Map<String, HostSessionData> data = new LinkedHashMap<>();
    private SessionDatabase database;
    private TimeSeriesBackend timeseries;
    /** Nested depth for coalescing SQLite saves within one poll (P32-005). */
    private int deferPersistDepth;

    private final java.util.LinkedHashSet<String> deferredPersistHosts = new java.util.LinkedHashSet<>();
    private int flushPersistCountForTests;

    public SessionStore(List<String> hosts) {
        this(hosts, null);
    }

    public SessionStore(List<String> hosts, SessionDatabase database) {
        this.database = database;
        for (String host : hosts) {
            data.put(host, loadOrCreate(host));
        }
    }

    public static SessionStore fromEntries(List<HostEntry> entries) {
        return fromEntries(entries, null);
    }

    public static SessionStore fromEntries(List<HostEntry> entries, SessionDatabase database) {
        return fromEntries(entries, database, HostProbeMode.TRACE);
    }

    public static SessionStore fromEntries(
            List<HostEntry> entries, SessionDatabase database, HostProbeMode profileDefault) {
        SessionStore store = new SessionStore(List.of(), database);
        store.loadHostEntries(entries, profileDefault);
        return store;
    }

    public boolean hasPersistence() {
        return database != null;
    }

    public SessionDatabase database() {
        return database;
    }

    /** Optional Influx/Timescale writer (P15-020); null disables push. */
    public void setTimeSeriesBackend(TimeSeriesBackend timeseries) {
        this.timeseries = timeseries;
    }

    public TimeSeriesBackend timeSeriesBackend() {
        return timeseries;
    }

    public List<String> hosts() {
        return List.copyOf(data.keySet());
    }

    public boolean containsHost(String host) {
        return data.containsKey(host);
    }

    public boolean canAddHost() {
        return data.size() < HostsConfig.MAX_HOSTS;
    }

    public String addHost(String host, boolean enabled) {
        return addHost(host, enabled, false, PingExpertEntry.empty());
    }

    public String addHost(String host, boolean enabled, PingExpertEntry pingExpert) {
        return addHost(host, enabled, false, pingExpert);
    }

    public String addHost(String host, boolean enabled, boolean pingOnly, PingExpertEntry pingExpert) {
        return addHost(host, enabled, pingOnly ? HostProbeMode.PING_ONLY : HostProbeMode.TRACE, pingExpert);
    }

    public String addHost(String host, boolean enabled, HostProbeMode probeMode, PingExpertEntry pingExpert) {
        String normalized = HostsConfig.validateSessionHost(host, hosts());
        if (data.containsKey(normalized)) {
            throw new ConfigError("Host already in list: " + normalized);
        }
        HostSessionData session = new HostSessionData();
        session.setEnabled(enabled);
        session.setProbeMode(probeMode);
        session.setPingExpert(pingExpert);
        data.put(normalized, session);
        persist(normalized);
        return normalized;
    }

    public void removeHost(String host) {
        if (!data.containsKey(host)) {
            throw new ConfigError("Unknown host: " + host);
        }
        data.remove(host);
        if (database != null) {
            database.delete(host);
        }
    }

    public void setEnabled(String host, boolean enabled) {
        get(host).setEnabled(enabled);
        persist(host);
    }

    public PingExpertEntry getPingExpert(String host) {
        return get(host).getPingExpert();
    }

    public void setPingExpert(String host, PingExpertEntry expert) {
        get(host).setPingExpert(expert);
    }

    public List<String> getTags(String host) {
        return get(host).getTags();
    }

    /** Replaces host tags (normalized via {@link io.pingui.config.HostTags}). */
    public void setTags(String host, List<String> tags) {
        get(host).setTags(tags);
    }

    public boolean isPingOnly(String host) {
        return get(host).isPingOnly();
    }

    public HostProbeMode getProbeMode(String host) {
        return get(host).getProbeMode();
    }

    public OptionalDouble getIntervalOverride(String host) {
        Double override = get(host).getIntervalSecondsOverride();
        return override != null ? OptionalDouble.of(override) : OptionalDouble.empty();
    }

    public void setProbeMode(String host, HostProbeMode probeMode) {
        HostSessionData session = get(host);
        session.setProbeMode(probeMode);
        session.setProbeModeOverride(null);
        session.setCurrentRoute(List.of());
        session.setPreviousRoute(List.of());
        session.clearTargetIdentity();
        session.getLastKnownByHop().clear();
        // Mode switch changes terminal hop identity (trace N ↔ ping_only 1); drop probe series.
        session.getHopStats().clear();
        session.getPingHistory().clear();
        persist(host);
    }

    public void setPingOnly(String host, boolean pingOnly) {
        setProbeMode(host, pingOnly ? HostProbeMode.PING_ONLY : HostProbeMode.TRACE);
    }

    public void loadHostEntries(List<HostEntry> entries) {
        loadHostEntries(entries, HostProbeMode.TRACE);
    }

    public void loadHostEntries(List<HostEntry> entries, HostProbeMode profileDefault) {
        data.clear();
        for (HostEntry entry : entries) {
            HostSessionData session = database != null ? loadOrCreate(entry.address()) : new HostSessionData();
            session.setEnabled(entry.enabled());
            session.applyProbeFromEntry(entry, profileDefault);
            session.setPingExpert(entry.pingExpert());
            data.put(entry.address(), session);
            persist(entry.address());
        }
    }

    public List<HostEntry> toHostEntries() {
        List<HostEntry> out = new ArrayList<>();
        for (Map.Entry<String, HostSessionData> entry : data.entrySet()) {
            HostSessionData session = entry.getValue();
            boolean pingOnly = session.isPingOnly() && session.getProbeModeOverride() == null;
            out.add(new HostEntry(
                    entry.getKey(),
                    session.isEnabled(),
                    pingOnly,
                    session.getPingExpert(),
                    session.getProbeModeOverride(),
                    session.getIntervalSecondsOverride(),
                    session.getTags()));
        }
        return List.copyOf(out);
    }

    public String renameHost(String oldHost, String newHost) {
        List<String> others = new ArrayList<>(hosts());
        others.remove(oldHost);
        String normalized = HostsConfig.validateSessionHost(newHost, others);
        HostSessionData session = data.remove(oldHost);
        if (session == null) {
            throw new ConfigError("Unknown host: " + oldHost);
        }
        data.put(normalized, session);
        if (database != null) {
            database.rename(oldHost, normalized);
        } else {
            persist(normalized);
        }
        return normalized;
    }

    public HostSessionData get(String host) {
        HostSessionData session = data.get(host);
        if (session == null) {
            throw new ConfigError("Unknown host: " + host);
        }
        return session;
    }

    public List<HopNode> inactiveRoute(String host) {
        HostSessionData session = get(host);
        return RouteHistory.routeWithLastKnownIps(session.getPreviousRoute(), session.getLastKnownByHop());
    }

    /**
     * Updates displayed route nodes. When {@code confirmedRouteChange} is non-null it is the
     * authoritative topology flag (P33-002); otherwise IP-list auto-detect is used (TRACE / tests).
     */
    public void updateRoute(String host, RouteSnapshot snapshot) {
        updateRoute(host, snapshot, null);
    }

    public void updateRoute(String host, RouteSnapshot snapshot, Boolean confirmedRouteChange) {
        HostSessionData session = get(host);
        rememberTargetIdentity(session, snapshot, null);
        List<String> oldIps = routeIps(session.getCurrentRoute());
        List<String> newIps = snapshot.routeIps();
        boolean routeChanged = confirmedRouteChange != null
                ? confirmedRouteChange
                : !session.getCurrentRoute().isEmpty() && !oldIps.equals(newIps);
        if (routeChanged) {
            session.setPreviousRoute(
                    RouteHistory.routeWithLastKnownIps(session.getCurrentRoute(), session.getLastKnownByHop()));
        }
        RouteHistory.recordLastKnown(session.getLastKnownByHop(), snapshot.nodes());
        session.setCurrentRoute(snapshot.nodes());
        persist(host);
        writeRouteEvent(host, newIps, routeChanged, snapshot);
    }

    /**
     * Applies one poll snapshot with a single SQLite {@code save} (P32-005 / P33-002). Prefer this over
     * separate {@link #updateRoute} + {@link #appendPingSamples} on the hot path.
     *
     * <p>{@code confirmedRouteChange} must come from the probe outcome (MTR discovery / timeout are not
     * topology changes). Pass {@code null} only for TRACE-style auto-detect callers.
     */
    public void applyPollSnapshot(String host, RouteSnapshot snapshot, PollSampleScope sampleScope) {
        applyPollSnapshot(host, snapshot, sampleScope, null);
    }

    public void applyPollSnapshot(
            String host, RouteSnapshot snapshot, PollSampleScope sampleScope, Boolean confirmedRouteChange) {
        PollSampleScope safe = sampleScope != null ? sampleScope : PollSampleScope.FULL;
        withDeferredPersist(() -> {
            updateRoute(host, snapshot, confirmedRouteChange);
            rememberTargetIdentity(get(host), snapshot, safe);
            appendPingSamples(host, snapshot, safe);
        });
    }

    public void appendPingSamples(String host, RouteSnapshot snapshot) {
        appendPingSamples(host, snapshot, PollSampleScope.FULL);
    }

    /** Records hop stats / RTT history for fresh samples only (P32-001). */
    public void appendPingSamples(String host, RouteSnapshot snapshot, PollSampleScope scope) {
        PollSampleScope safe = scope != null ? scope : PollSampleScope.FULL;
        recordHopProbes(host, snapshot, safe);
        Map<String, List<Double>> history = get(host).getPingHistory();
        boolean changed = false;
        List<PingSample> newSamples = new ArrayList<>();
        for (HopNode node : snapshot.nodes()) {
            if (!safe.allHopsFresh() && (safe.freshHop() == null || node.hop() != safe.freshHop())) {
                continue;
            }
            if (!node.isReachable() || node.pingMs() == null) {
                continue;
            }
            List<Double> samples = mutablePingSamples(history, node.ip());
            samples.add(node.pingMs());
            if (samples.size() > MAX_PING_SAMPLES) {
                samples.subList(0, samples.size() - MAX_PING_SAMPLES).clear();
            }
            changed = true;
            newSamples.add(new PingSample(host, node.hop(), node.ip(), node.pingMs(), snapshot.timestamp()));
        }
        if (changed) {
            persist(host);
            writePingSamples(newSamples);
        }
    }

    private static List<Double> mutablePingSamples(Map<String, List<Double>> history, String ip) {
        List<Double> existing = history.get(ip);
        if (existing == null) {
            ArrayList<Double> created = new ArrayList<>();
            history.put(ip, created);
            return created;
        }
        if (existing instanceof ArrayList) {
            return existing;
        }
        ArrayList<Double> copy = new ArrayList<>(existing);
        history.put(ip, copy);
        return copy;
    }

    public Double avgPing(String host, String ip) {
        List<Double> samples = get(host).getPingHistory().get(ip);
        if (samples == null || samples.isEmpty()) {
            return null;
        }
        return samples.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    public HopStatsSummary hopStatsSummary(String host, int hop) {
        HopProbeStats stats = get(host).getHopStats().get(hop);
        return stats != null ? HopStats.summarize(stats) : null;
    }

    /**
     * Metrics for the real target hop (P33-002), not merely the last node of a partial MTR route.
     * {@code null} when disabled, empty, or the target has not been identified yet.
     */
    public HostTargetStats targetStats(String host) {
        HostSessionData session = get(host);
        if (!session.isEnabled()) {
            return null;
        }
        List<HopNode> route = session.getCurrentRoute();
        if (route.isEmpty()) {
            return null;
        }
        HopNode terminal = resolveTargetHop(session, route);
        if (terminal == null) {
            return null;
        }
        HopProbeStats stats = session.getHopStats().get(terminal.hop());
        return HopStats.targetStats(terminal, stats);
    }

    /** Resolves the hop that represents the monitored target (P33-002). */
    static HopNode resolveTargetHop(HostSessionData session, List<HopNode> route) {
        Integer targetHop = session.getLastTargetHop();
        if (targetHop != null && targetHop >= 1 && targetHop <= route.size()) {
            return route.get(targetHop - 1);
        }
        String targetIp = session.getLastTargetIp();
        if (targetIp != null && !targetIp.isBlank()) {
            for (HopNode node : route) {
                if (node.isReachable() && targetIp.equals(node.ip())) {
                    return node;
                }
            }
            // Known target but not present yet (MTR discovery) — do not use an intermediate router.
            return null;
        }
        return route.get(route.size() - 1);
    }

    /** Records target IP / hop identity from a poll for endpoint projection (P33-002). */
    static void rememberTargetIdentity(HostSessionData session, RouteSnapshot snapshot, PollSampleScope scope) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.targetIp() != null && !snapshot.targetIp().isBlank()) {
            session.setLastTargetIp(snapshot.targetIp());
        }
        if (scope != null && scope.targetSampled() && scope.freshHop() != null) {
            session.setLastTargetHop(scope.freshHop());
            return;
        }
        String tip = session.getLastTargetIp();
        if (tip == null || tip.isBlank()) {
            return;
        }
        for (HopNode node : snapshot.nodes()) {
            if (node.isReachable() && tip.equals(node.ip())) {
                session.setLastTargetHop(node.hop());
                return;
            }
        }
    }

    @Override
    public void close() {
        if (timeseries != null) {
            try {
                timeseries.close();
            } catch (RuntimeException ex) {
                LOG.warn("Time-series backend close failed: {}", ex.getMessage());
            }
            timeseries = null;
        }
        if (database == null) {
            return;
        }
        for (String host : hosts()) {
            database.save(host, get(host));
        }
        database.close();
        database = null;
    }

    private void writePingSamples(List<PingSample> samples) {
        TimeSeriesBackend backend = timeseries;
        if (backend == null || samples.isEmpty()) {
            return;
        }
        try {
            backend.writePingSamples(samples);
        } catch (RuntimeException ex) {
            LOG.warn("Time-series ping write failed: {}", ex.getMessage());
        }
    }

    private void writeRouteEvent(String host, List<String> routeIps, boolean routeChanged, RouteSnapshot snapshot) {
        TimeSeriesBackend backend = timeseries;
        if (backend == null) {
            return;
        }
        try {
            backend.writeRouteEvent(new RouteEvent(host, routeIps, routeChanged, snapshot.timestamp()));
        } catch (RuntimeException ex) {
            LOG.warn("Time-series route write failed: {}", ex.getMessage());
        }
    }

    private void recordHopProbes(String host, RouteSnapshot snapshot, PollSampleScope scope) {
        if (snapshot.nodes().isEmpty()) {
            return;
        }
        PollSampleScope safe = scope != null ? scope : PollSampleScope.FULL;
        HostSessionData session = get(host);
        boolean changed = false;
        for (HopNode node : snapshot.nodes()) {
            if (!safe.allHopsFresh() && (safe.freshHop() == null || node.hop() != safe.freshHop())) {
                continue;
            }
            HopProbeStats stats = session.getHopStats().computeIfAbsent(node.hop(), ignored -> new HopProbeStats());
            HopStats.recordProbe(stats, node);
            changed = true;
        }
        if (changed) {
            persist(host);
        }
    }

    private HostSessionData loadOrCreate(String host) {
        if (database == null) {
            return new HostSessionData();
        }
        HostSessionData loaded = database.load(host);
        return loaded != null ? loaded : new HostSessionData();
    }

    private void persist(String host) {
        if (deferPersistDepth > 0) {
            deferredPersistHosts.add(host);
            return;
        }
        flushPersist(host);
    }

    private void flushPersist(String host) {
        flushPersistCountForTests++;
        if (database != null) {
            database.save(host, get(host));
        }
    }

    /** Test hook: number of SQLite {@code save} flushes since construction (P32-005). */
    int flushPersistCountForTests() {
        return flushPersistCountForTests;
    }

    /**
     * Coalesces nested {@link #persist(String)} calls into one SQLite write per dirty host (P32-005).
     */
    void withDeferredPersist(Runnable action) {
        Objects.requireNonNull(action, "action");
        deferPersistDepth++;
        try {
            action.run();
        } finally {
            deferPersistDepth--;
            if (deferPersistDepth == 0 && !deferredPersistHosts.isEmpty()) {
                java.util.ArrayList<String> hosts = new java.util.ArrayList<>(deferredPersistHosts);
                deferredPersistHosts.clear();
                for (String host : hosts) {
                    flushPersist(host);
                }
            }
        }
    }

    private static List<String> routeIps(List<HopNode> route) {
        return route.stream().filter(HopNode::isReachable).map(HopNode::ip).toList();
    }
}

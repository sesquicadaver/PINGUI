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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory session storage for route and ping metrics; optional SQLite persistence (P11-011). */
public final class SessionStore implements AutoCloseable {
    public static final int MAX_PING_SAMPLES = 50;

    private final Map<String, HostSessionData> data = new LinkedHashMap<>();
    private SessionDatabase database;

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
        SessionStore store = new SessionStore(List.of(), database);
        store.loadHostEntries(entries);
        return store;
    }

    public boolean hasPersistence() {
        return database != null;
    }

    public SessionDatabase database() {
        return database;
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
        String normalized = HostsConfig.validateSessionHost(host, hosts());
        if (data.containsKey(normalized)) {
            throw new ConfigError("Host already in list: " + normalized);
        }
        HostSessionData session = new HostSessionData();
        session.setEnabled(enabled);
        session.setPingOnly(pingOnly);
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

    public boolean isPingOnly(String host) {
        return get(host).isPingOnly();
    }

    public void setPingOnly(String host, boolean pingOnly) {
        HostSessionData session = get(host);
        session.setPingOnly(pingOnly);
        session.setCurrentRoute(List.of());
        session.setPreviousRoute(List.of());
        session.getLastKnownByHop().clear();
        persist(host);
    }

    public void loadHostEntries(List<HostEntry> entries) {
        data.clear();
        for (HostEntry entry : entries) {
            HostSessionData session = database != null ? loadOrCreate(entry.address()) : new HostSessionData();
            session.setEnabled(entry.enabled());
            session.setPingOnly(entry.pingOnly());
            session.setPingExpert(entry.pingExpert());
            data.put(entry.address(), session);
            persist(entry.address());
        }
    }

    public List<HostEntry> toHostEntries() {
        List<HostEntry> out = new ArrayList<>();
        for (Map.Entry<String, HostSessionData> entry : data.entrySet()) {
            HostSessionData session = entry.getValue();
            out.add(new HostEntry(entry.getKey(), session.isEnabled(), session.isPingOnly(), session.getPingExpert()));
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

    public void updateRoute(String host, RouteSnapshot snapshot) {
        HostSessionData session = get(host);
        List<String> oldIps = routeIps(session.getCurrentRoute());
        List<String> newIps = snapshot.routeIps();
        if (!session.getCurrentRoute().isEmpty() && !oldIps.equals(newIps)) {
            session.setPreviousRoute(
                    RouteHistory.routeWithLastKnownIps(session.getCurrentRoute(), session.getLastKnownByHop()));
        }
        RouteHistory.recordLastKnown(session.getLastKnownByHop(), snapshot.nodes());
        session.setCurrentRoute(snapshot.nodes());
        persist(host);
    }

    public void appendPingSamples(String host, RouteSnapshot snapshot) {
        recordHopProbes(host, snapshot);
        Map<String, List<Double>> history = get(host).getPingHistory();
        boolean changed = false;
        for (HopNode node : snapshot.nodes()) {
            if (!node.isReachable() || node.pingMs() == null) {
                continue;
            }
            List<Double> samples = mutablePingSamples(history, node.ip());
            samples.add(node.pingMs());
            if (samples.size() > MAX_PING_SAMPLES) {
                samples.subList(0, samples.size() - MAX_PING_SAMPLES).clear();
            }
            changed = true;
        }
        if (changed) {
            persist(host);
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

    /** Metrics for the last hop in the current route; {@code null} when route or probes are missing. */
    public HostTargetStats targetStats(String host) {
        HostSessionData session = get(host);
        if (!session.isEnabled()) {
            return null;
        }
        List<HopNode> route = session.getCurrentRoute();
        if (route.isEmpty()) {
            return null;
        }
        HopNode terminal = route.get(route.size() - 1);
        HopProbeStats stats = session.getHopStats().get(terminal.hop());
        return HopStats.targetStats(terminal, stats);
    }

    @Override
    public void close() {
        if (database == null) {
            return;
        }
        for (String host : hosts()) {
            database.save(host, get(host));
        }
        database.close();
        database = null;
    }

    private void recordHopProbes(String host, RouteSnapshot snapshot) {
        if (snapshot.nodes().isEmpty()) {
            return;
        }
        HostSessionData session = get(host);
        for (HopNode node : snapshot.nodes()) {
            HopProbeStats stats = session.getHopStats().computeIfAbsent(node.hop(), ignored -> new HopProbeStats());
            HopStats.recordProbe(stats, node);
        }
        persist(host);
    }

    private HostSessionData loadOrCreate(String host) {
        if (database == null) {
            return new HostSessionData();
        }
        HostSessionData loaded = database.load(host);
        return loaded != null ? loaded : new HostSessionData();
    }

    private void persist(String host) {
        if (database != null) {
            database.save(host, get(host));
        }
    }

    private static List<String> routeIps(List<HopNode> route) {
        return route.stream().filter(HopNode::isReachable).map(HopNode::ip).toList();
    }
}

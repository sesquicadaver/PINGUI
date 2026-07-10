package io.pingui.monitor;

import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.PersistencePolicyHolder;
import io.pingui.probe.MtrHopProbers;
import io.pingui.probe.MtrProbe;
import io.pingui.probe.ProbeMode;
import io.pingui.probe.RouteProbe;
import io.pingui.probe.RouteProbeFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Background polling of enabled hosts (cross-platform, no Qt). */
public final class MonitorService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorService.class);
    private static final int MIN_PROBE_POOL_THREADS = 4;

    public interface Listener {
        void onDataReceived(String host, RouteSnapshot snapshot);

        void onRouteChanged(String host, List<String> oldIps, List<String> newIps);

        void onProbeError(String host, String message);
    }

    /** Supplies per-host expert ping settings for enrichment after trace. */
    @FunctionalInterface
    public interface PingExpertResolver {
        PingExpertEntry resolve(String host);
    }

    /** Supplies per-host monitoring strategy (trace / mtr / ping_only). */
    @FunctionalInterface
    public interface HostProbeModeResolver {
        HostProbeMode resolve(String host);
    }

    /** Supplies optional per-host poll interval override in seconds (P13-020). */
    @FunctionalInterface
    public interface HostPollIntervalResolver {
        OptionalDouble resolve(String host);
    }

    /** @deprecated use {@link HostProbeModeResolver} */
    @Deprecated
    @FunctionalInterface
    public interface PingOnlyResolver {
        boolean resolve(String host);
    }

    private final RoutePoller poller;
    private final ExpertPingEnricher expertEnricher = new ExpertPingEnricher();
    private final DefaultTargetPingEnricher defaultTargetPingEnricher = new DefaultTargetPingEnricher();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService probePool;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object lock = new Object();
    private final List<String> hosts = new ArrayList<>();
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final Map<String, Boolean> pingOnly = new HashMap<>();
    private final Map<String, HostProbeMode> probeModes = new HashMap<>();
    private final Map<String, List<String>> lastRoutes = new HashMap<>();
    private final Map<String, Instant> lastPollAt = new HashMap<>();
    private final Map<String, AtomicBoolean> pollsInFlight = new ConcurrentHashMap<>();
    private final double profileIntervalSeconds;
    private final int maxHops;
    private final double timeoutSeconds;
    private Listener listener;
    private volatile AlertDispatcher alertDispatcher = AlertDispatcher.noop();
    private volatile String alertProfileName = "default";
    private volatile PingExpertResolver expertResolver;
    private volatile HostProbeModeResolver probeModeResolver;
    private volatile HostPollIntervalResolver intervalResolver;
    private volatile PingOnlyResolver pingOnlyResolver;
    private HostProbeMode profileProbeMode = HostProbeMode.TRACE;
    private volatile PersistenceEventWriter persistenceEvents;
    private final PersistencePolicyHolder persistencePolicy = new PersistencePolicyHolder();
    private final BurstSchedulePolicy burstPolicy = new BurstSchedulePolicy();
    private final TraceConcurrencyLimiter traceLimiter;

    public MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds) {
        this(intervalSeconds, maxHops, timeoutSeconds, ProbeMode.AUTO);
    }

    public MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds, ProbeMode probeMode) {
        this(
                intervalSeconds,
                maxHops,
                timeoutSeconds,
                RouteProbeFactory.create(probeMode),
                TraceConcurrencyLimiter.DEFAULT_MAX);
    }

    public MonitorService(
            double intervalSeconds, int maxHops, double timeoutSeconds, ProbeMode probeMode, int maxConcurrentTraces) {
        this(intervalSeconds, maxHops, timeoutSeconds, RouteProbeFactory.create(probeMode), maxConcurrentTraces);
    }

    MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds, RouteProbe probe) {
        this(intervalSeconds, maxHops, timeoutSeconds, probe, TraceConcurrencyLimiter.DEFAULT_MAX);
    }

    MonitorService(
            double intervalSeconds, int maxHops, double timeoutSeconds, RouteProbe probe, int maxConcurrentTraces) {
        this(
                intervalSeconds,
                maxHops,
                timeoutSeconds,
                probe,
                new MtrProbe(MtrHopProbers.platformDefault()),
                maxConcurrentTraces);
    }

    MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds, RouteProbe probe, MtrProbe mtrProbe) {
        this(intervalSeconds, maxHops, timeoutSeconds, probe, mtrProbe, TraceConcurrencyLimiter.DEFAULT_MAX);
    }

    MonitorService(
            double intervalSeconds,
            int maxHops,
            double timeoutSeconds,
            RouteProbe probe,
            MtrProbe mtrProbe,
            int maxConcurrentTraces) {
        this.profileIntervalSeconds = intervalSeconds;
        this.maxHops = maxHops;
        this.timeoutSeconds = timeoutSeconds;
        this.poller = new RoutePoller(probe, mtrProbe);
        this.traceLimiter = new TraceConcurrencyLimiter(maxConcurrentTraces);
        int poolSize = Math.max(MIN_PROBE_POOL_THREADS, maxConcurrentTraces + 2);
        this.probePool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread thread = new Thread(r, "pingui-probe");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "pingui-monitor");
            thread.setDaemon(true);
            return thread;
        });
        long tickMs = Math.max(1L, Math.round(HostPollSchedule.TICK_SECONDS * 1000.0));
        scheduler.scheduleWithFixedDelay(this::cycle, 0, tickMs, TimeUnit.MILLISECONDS);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAlertDispatcher(AlertDispatcher alertDispatcher) {
        this.alertDispatcher = alertDispatcher != null ? alertDispatcher : AlertDispatcher.noop();
    }

    public void setAlertProfileName(String alertProfileName) {
        if (alertProfileName == null || alertProfileName.isBlank()) {
            this.alertProfileName = "default";
        } else {
            this.alertProfileName = alertProfileName;
        }
    }

    public void setExpertResolver(PingExpertResolver expertResolver) {
        this.expertResolver = expertResolver;
    }

    public void setHostProbeModeResolver(HostProbeModeResolver probeModeResolver) {
        this.probeModeResolver = probeModeResolver;
    }

    public void setHostPollIntervalResolver(HostPollIntervalResolver intervalResolver) {
        this.intervalResolver = intervalResolver;
    }

    public void setProfileProbeMode(HostProbeMode profileProbeMode) {
        this.profileProbeMode = profileProbeMode != null ? profileProbeMode : HostProbeMode.TRACE;
    }

    @Deprecated
    public void setPingOnlyResolver(PingOnlyResolver pingOnlyResolver) {
        this.pingOnlyResolver = pingOnlyResolver;
    }

    public void setPersistenceEventWriter(PersistenceEventWriter persistenceEvents) {
        this.persistenceEvents = persistenceEvents;
    }

    public PersistencePolicyHolder persistencePolicy() {
        return persistencePolicy;
    }

    /** Sets policy effective from the next completed poll cycle (SPIKE P11-002). */
    public void setPendingPersistencePolicy(PersistencePolicy policy) {
        persistencePolicy.setPending(policy);
    }

    public List<String> hosts() {
        synchronized (lock) {
            return List.copyOf(hosts);
        }
    }

    public List<String> enabledHosts() {
        synchronized (lock) {
            return hosts.stream()
                    .filter(h -> Boolean.TRUE.equals(enabled.get(h)))
                    .toList();
        }
    }

    public boolean canAddHost() {
        synchronized (lock) {
            return hosts.size() < io.pingui.config.HostsConfig.MAX_HOSTS;
        }
    }

    public void addHost(String host, boolean hostEnabled) {
        addHost(host, hostEnabled, false);
    }

    public void addHost(String host, boolean hostEnabled, boolean hostPingOnly) {
        addHost(host, hostEnabled, hostPingOnly ? HostProbeMode.PING_ONLY : HostProbeMode.TRACE);
    }

    public void addHost(String host, boolean hostEnabled, HostProbeMode probeMode) {
        synchronized (lock) {
            if (hosts.contains(host)) {
                throw new io.pingui.config.ConfigError("Host already in list: " + host);
            }
            hosts.add(host);
            enabled.put(host, hostEnabled);
            boolean legacyPingOnly = probeMode == HostProbeMode.PING_ONLY;
            pingOnly.put(host, legacyPingOnly);
            probeModes.put(host, probeMode);
            lastRoutes.put(host, List.of());
            lastPollAt.remove(host);
        }
    }

    public void removeHost(String host) {
        synchronized (lock) {
            if (!hosts.remove(host)) {
                throw new io.pingui.config.ConfigError("Unknown host: " + host);
            }
            enabled.remove(host);
            pingOnly.remove(host);
            probeModes.remove(host);
            lastRoutes.remove(host);
            lastPollAt.remove(host);
        }
        burstPolicy.clearHost(host);
        pollsInFlight.remove(host);
    }

    public void renameHost(String oldHost, String newHost) {
        synchronized (lock) {
            int index = hosts.indexOf(oldHost);
            if (index < 0) {
                throw new io.pingui.config.ConfigError("Unknown host: " + oldHost);
            }
            hosts.set(index, newHost);
            Boolean wasEnabled = enabled.remove(oldHost);
            if (wasEnabled != null) {
                enabled.put(newHost, wasEnabled);
            }
            Boolean wasPingOnly = pingOnly.remove(oldHost);
            if (wasPingOnly != null) {
                pingOnly.put(newHost, wasPingOnly);
            }
            HostProbeMode wasMode = probeModes.remove(oldHost);
            if (wasMode != null) {
                probeModes.put(newHost, wasMode);
            }
            lastRoutes.put(newHost, lastRoutes.remove(oldHost));
            Instant wasLastPoll = lastPollAt.remove(oldHost);
            if (wasLastPoll != null) {
                lastPollAt.put(newHost, wasLastPoll);
            }
            AtomicBoolean inFlight = pollsInFlight.remove(oldHost);
            if (inFlight != null) {
                pollsInFlight.put(newHost, inFlight);
            }
        }
        burstPolicy.renameHost(oldHost, newHost);
    }

    public void setHostEnabled(String host, boolean hostEnabled) {
        synchronized (lock) {
            if (!hosts.contains(host)) {
                throw new io.pingui.config.ConfigError("Unknown host: " + host);
            }
            enabled.put(host, hostEnabled);
        }
    }

    public void setHostPingOnly(String host, boolean hostPingOnly) {
        setHostProbeMode(host, hostPingOnly ? HostProbeMode.PING_ONLY : HostProbeMode.TRACE);
    }

    public void setHostProbeMode(String host, HostProbeMode probeMode) {
        synchronized (lock) {
            if (!hosts.contains(host)) {
                throw new io.pingui.config.ConfigError("Unknown host: " + host);
            }
            HostProbeMode mode = probeMode != null ? probeMode : HostProbeMode.TRACE;
            probeModes.put(host, mode);
            pingOnly.put(host, mode == HostProbeMode.PING_ONLY);
            lastRoutes.put(host, List.of());
            lastPollAt.remove(host);
        }
        burstPolicy.clearHost(host);
        poller.resetMtrHost(host);
    }

    private void cycle() {
        if (!running.get()) {
            return;
        }
        List<String> active;
        synchronized (lock) {
            active = hosts.stream()
                    .filter(h -> Boolean.TRUE.equals(enabled.get(h)))
                    .toList();
        }
        if (active.isEmpty()) {
            persistencePolicy.applyPendingAfterCycle();
            return;
        }
        Instant now = Instant.now();
        for (String host : active) {
            if (!running.get()) {
                break;
            }
            HostProbeMode mode = resolveProbeMode(host);
            double intervalSeconds = resolveIntervalSeconds(host, mode);
            Instant lastPoll;
            synchronized (lock) {
                lastPoll = lastPollAt.get(host);
            }
            if (!HostPollSchedule.isDue(lastPoll, now, intervalSeconds)) {
                continue;
            }
            dispatchDueHost(host, mode);
        }
        persistencePolicy.applyPendingAfterCycle();
    }

    private void dispatchDueHost(String host, HostProbeMode mode) {
        if (!TraceConcurrencyLimiter.limitsConcurrency(mode)) {
            probePool.execute(() -> pollHost(host));
            return;
        }
        if (!traceLimiter.tryAcquire()) {
            return;
        }
        probePool.execute(() -> {
            try {
                pollHost(host);
            } finally {
                traceLimiter.release();
            }
        });
    }

    private void pollHost(String host) {
        if (!running.get()) {
            return;
        }
        AtomicBoolean inFlight = pollsInFlight.computeIfAbsent(host, ignored -> new AtomicBoolean(false));
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            pollHostOnce(host);
        } finally {
            inFlight.set(false);
        }
    }

    private void pollHostOnce(String host) {
        if (!running.get()) {
            return;
        }
        List<String> previousIps;
        HostProbeMode probeMode;
        synchronized (lock) {
            if (!hosts.contains(host)) {
                return;
            }
            previousIps = List.copyOf(lastRoutes.getOrDefault(host, List.of()));
            probeMode = resolveProbeMode(host);
            lastPollAt.put(host, Instant.now());
        }
        HostPollOutcome outcome =
                switch (probeMode) {
                    case PING_ONLY -> poller.pollHostPingOnly(host, previousIps, timeoutSeconds, resolveExpert(host));
                    case MTR -> poller.pollHostMtr(host, previousIps, maxHops, timeoutSeconds);
                    case TRACE -> poller.pollHostRoute(host, previousIps, maxHops, timeoutSeconds);
                };
        Listener current = listener;
        if (current == null || !isKnownHost(host)) {
            return;
        }
        if (outcome.error() != null) {
            PersistenceEventWriter events = persistenceEvents;
            if (events != null) {
                try {
                    events.writeProbeError(host, outcome.error());
                } catch (RuntimeException ex) {
                    LOG.warn("Persistence probe_error failed for {}: {}", host, ex.getMessage());
                }
            }
            current.onProbeError(host, outcome.error());
            return;
        }
        synchronized (lock) {
            if (hosts.contains(host)) {
                lastRoutes.put(host, outcome.currentIps());
            }
        }
        if (outcome.snapshot() != null && isKnownHost(host)) {
            RouteSnapshot snapshot = outcome.snapshot();
            if (probeMode != HostProbeMode.PING_ONLY) {
                PingExpertEntry expert = resolveExpert(host);
                if (expert.isConfigured()) {
                    snapshot = expertEnricher.enrich(snapshot, expert, timeoutSeconds);
                } else {
                    snapshot = defaultTargetPingEnricher.enrich(snapshot, timeoutSeconds);
                }
            }
            current.onDataReceived(host, snapshot);
        }
        if (outcome.routeChanged() && BurstSchedulePolicy.shouldArmBurst(outcome.oldIps(), outcome.newIps())) {
            burstPolicy.onRouteChange(host, Instant.now());
        }
        if (outcome.routeChanged()) {
            current.onRouteChanged(host, outcome.oldIps(), outcome.newIps());
            dispatchRouteChangeAlert(host, outcome.oldIps(), outcome.newIps());
        } else if (isFirstBaseline(previousIps, outcome.currentIps())) {
            persistBaselineRouteChange(host, outcome.currentIps());
            current.onRouteChanged(host, List.of(), outcome.currentIps());
        }
    }

    private static boolean isFirstBaseline(List<String> previousIps, List<String> currentIps) {
        return previousIps.isEmpty() && currentIps != null && !currentIps.isEmpty();
    }

    private void persistBaselineRouteChange(String host, List<String> currentIps) {
        RouteChangeEvent event =
                RouteChangeEvent.fromRouteChange(host, List.of(), currentIps, alertProfileName, Instant.now());
        PersistenceEventWriter events = persistenceEvents;
        if (events == null || events.hasRouteChangeEvents(host)) {
            return;
        }
        try {
            events.writeRouteChange(event);
        } catch (RuntimeException ex) {
            LOG.warn("Persistence baseline route_change failed for {}: {}", host, ex.getMessage());
        }
    }

    private void dispatchRouteChangeAlert(String host, List<String> oldIps, List<String> newIps) {
        RouteChangeEvent event =
                RouteChangeEvent.fromRouteChange(host, oldIps, newIps, alertProfileName, Instant.now());
        PersistenceEventWriter events = persistenceEvents;
        if (events != null) {
            try {
                events.writeRouteChange(event);
            } catch (RuntimeException ex) {
                LOG.warn("Persistence route_change failed for {}: {}", host, ex.getMessage());
            }
        }
        AlertDispatcher dispatcher = alertDispatcher;
        if (dispatcher == null) {
            return;
        }
        try {
            dispatcher.dispatch(event);
        } catch (RuntimeException ex) {
            LOG.warn("Alert dispatch failed for {}: {}", host, ex.getMessage());
        }
    }

    private boolean isKnownHost(String host) {
        synchronized (lock) {
            return hosts.contains(host);
        }
    }

    private PingExpertEntry resolveExpert(String host) {
        PingExpertResolver resolver = expertResolver;
        if (resolver == null) {
            return PingExpertEntry.empty();
        }
        PingExpertEntry expert = resolver.resolve(host);
        return expert != null ? expert : PingExpertEntry.empty();
    }

    private HostProbeMode resolveProbeMode(String host) {
        HostProbeModeResolver resolver = probeModeResolver;
        if (resolver != null) {
            HostProbeMode resolved = resolver.resolve(host);
            if (resolved != null) {
                return resolved;
            }
        }
        PingOnlyResolver legacyResolver = pingOnlyResolver;
        if (legacyResolver != null && legacyResolver.resolve(host)) {
            return HostProbeMode.PING_ONLY;
        }
        return probeModes.getOrDefault(host, profileProbeMode);
    }

    private double resolveIntervalSeconds(String host, HostProbeMode mode) {
        OptionalDouble override = OptionalDouble.empty();
        HostPollIntervalResolver resolver = intervalResolver;
        if (resolver != null) {
            OptionalDouble resolved = resolver.resolve(host);
            if (resolved != null) {
                override = resolved;
            }
        }
        return burstPolicy.effectiveInterval(
                host, HostPollSchedule.effectiveInterval(mode, profileIntervalSeconds, override), Instant.now());
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdownNow();
        probePool.shutdownNow();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            probePool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

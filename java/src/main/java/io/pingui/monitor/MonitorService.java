package io.pingui.monitor;

import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.config.LatencyHighRuleConfig;
import io.pingui.config.PingExpertEntry;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.PersistencePolicyHolder;
import io.pingui.probe.MtrHopProbers;
import io.pingui.probe.MtrProbe;
import io.pingui.probe.ProbeMode;
import io.pingui.probe.RouteProbe;
import io.pingui.probe.RouteProbeFactory;
import io.pingui.telemetry.MetricNames;
import io.pingui.telemetry.MetricSample;
import io.pingui.telemetry.TelemetryBus;
import io.pingui.telemetry.TelemetryEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
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

    private final RoutePoller poller;
    private final ExpertPingEnricher expertEnricher = new ExpertPingEnricher();
    private final DefaultTargetPingEnricher defaultTargetPingEnricher = new DefaultTargetPingEnricher();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService probePool;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final HostRegistry registry = new HostRegistry();
    private final double profileIntervalSeconds;
    private final int maxHops;
    private final double timeoutSeconds;
    private Listener listener;
    private volatile AlertDispatcher alertDispatcher = AlertDispatcher.noop();
    private volatile String alertProfileName = "default";
    private final AlertRuleEngine alertRuleEngine = new AlertRuleEngine();
    private volatile EndpointDownRuleConfig endpointDownRule = EndpointDownRuleConfig.disabled();
    private volatile LatencyHighRuleConfig latencyHighRule = LatencyHighRuleConfig.disabled();
    private volatile boolean notifyResolved;
    private volatile PingExpertResolver expertResolver;
    private volatile HostProbeModeResolver probeModeResolver;
    private volatile HostPollIntervalResolver intervalResolver;
    private HostProbeMode profileProbeMode = HostProbeMode.TRACE;
    private volatile PersistenceEventWriter persistenceEvents;
    private final PersistencePolicyHolder persistencePolicy = new PersistencePolicyHolder();
    private final BurstSchedulePolicy burstPolicy = new BurstSchedulePolicy();
    private final TraceConcurrencyLimiter traceLimiter;
    private volatile TelemetryBus telemetryBus;

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

    /**
     * In-memory {@code endpoint_down} rule (P21-002). Default disabled; YAML/GUI wiring is P21-003.
     */
    public void setEndpointDownRule(EndpointDownRuleConfig endpointDownRule) {
        this.endpointDownRule = endpointDownRule != null ? endpointDownRule : EndpointDownRuleConfig.disabled();
        alertRuleEngine.clearAll();
    }

    /**
     * In-memory {@code latency_high} rule (P23). Default disabled. Clears latency baselines for all hosts.
     */
    public void setLatencyHighRule(LatencyHighRuleConfig latencyHighRule) {
        this.latencyHighRule = latencyHighRule != null ? latencyHighRule : LatencyHighRuleConfig.disabled();
        alertRuleEngine.clearAll();
    }

    /** When true, emit quality RESOLVED after clear_after successes (ADR). */
    public void setNotifyResolved(boolean notifyResolved) {
        this.notifyResolved = notifyResolved;
    }

    /** Session quality problem summary for host-row badge (P22-002 / P23). */
    public Optional<HostProblemSummary> hostProblemSummary(String host) {
        return alertRuleEngine.problemSummary(host, Instant.now());
    }

    /**
     * Acknowledges the host problem (badge off until next FIRING). Counters preserved.
     *
     * @return {@code true} when engine had state for the host
     */
    public boolean ackHostProblem(String host) {
        return alertRuleEngine.ack(host);
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

    public void setPersistenceEventWriter(PersistenceEventWriter persistenceEvents) {
        this.persistenceEvents = persistenceEvents;
    }

    /** Optional telemetry bus (P16-013); null disables offers. Must not block poll. */
    public void setTelemetryBus(TelemetryBus telemetryBus) {
        this.telemetryBus = telemetryBus;
    }

    public PersistencePolicyHolder persistencePolicy() {
        return persistencePolicy;
    }

    /** Sets policy effective from the next completed poll cycle (SPIKE P11-002). */
    public void setPendingPersistencePolicy(PersistencePolicy policy) {
        persistencePolicy.setPending(policy);
    }

    public List<String> hosts() {
        return registry.hosts();
    }

    public List<String> enabledHosts() {
        return registry.enabledHosts();
    }

    public boolean canAddHost() {
        return registry.canAdd();
    }

    public void addHost(String host, boolean hostEnabled) {
        addHost(host, hostEnabled, false);
    }

    public void addHost(String host, boolean hostEnabled, boolean hostPingOnly) {
        addHost(host, hostEnabled, hostPingOnly ? HostProbeMode.PING_ONLY : HostProbeMode.TRACE);
    }

    public void addHost(String host, boolean hostEnabled, HostProbeMode probeMode) {
        registry.add(host, hostEnabled, probeMode);
    }

    public void removeHost(String host) {
        registry.remove(host);
        burstPolicy.clearHost(host);
        alertRuleEngine.clearHost(host);
    }

    public void renameHost(String oldHost, String newHost) {
        registry.rename(oldHost, newHost);
        burstPolicy.renameHost(oldHost, newHost);
    }

    public void setHostEnabled(String host, boolean hostEnabled) {
        registry.setEnabled(host, hostEnabled);
    }

    public void setHostProbeMode(String host, HostProbeMode probeMode) {
        registry.setProbeMode(host, probeMode);
        burstPolicy.clearHost(host);
        poller.resetMtrHost(host);
    }

    private void cycle() {
        if (!running.get()) {
            return;
        }
        List<String> active = registry.enabledHosts();
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
            Instant lastPoll = registry.lastPollAt(host);
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
        AtomicBoolean inFlight = registry.inFlightFlag(host);
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
        HostRegistry.PollStart start = registry.beginPoll(host, profileProbeMode, Instant.now());
        if (start == null) {
            return;
        }
        List<String> previousIps = start.previousIps();
        HostProbeMode mappedAtStart = start.mappedMode();
        HostProbeMode probeMode = resolveProbeMode(host);
        long startedNanos = System.nanoTime();
        HostPollOutcome outcome =
                switch (probeMode) {
                    case PING_ONLY -> poller.pollHostPingOnly(host, previousIps, timeoutSeconds, resolveExpert(host));
                    case MTR -> poller.pollHostMtr(host, previousIps, maxHops, timeoutSeconds);
                    case TRACE -> poller.pollHostRoute(host, previousIps, maxHops, timeoutSeconds);
                };
        double durationMs = (System.nanoTime() - startedNanos) / 1_000_000.0;
        Listener current = listener;
        if (current == null || !registry.contains(host)) {
            return;
        }
        // Discard if resolver or local map changed mid-flight (half-updated probe-mode toggle).
        // Compare each to its start snapshot — not to each other — so HostProbeModeResolver
        // (SessionStore) may still report the old mode while the local map already flipped.
        HostProbeMode resolved = resolveProbeMode(host);
        if (resolved != probeMode || !registry.mappedModeUnchanged(host, mappedAtStart, profileProbeMode)) {
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
            offerTelemetryFailure(host, outcome.error(), probeMode, durationMs);
            current.onProbeError(host, outcome.error());
            return;
        }
        registry.putLastRoute(host, outcome.currentIps());
        if (outcome.snapshot() != null && registry.contains(host)) {
            RouteSnapshot snapshot = outcome.snapshot();
            if (probeMode != HostProbeMode.PING_ONLY) {
                PingExpertEntry expert = resolveExpert(host);
                if (expert.isConfigured()) {
                    snapshot = expertEnricher.enrich(snapshot, expert, timeoutSeconds);
                } else {
                    snapshot = defaultTargetPingEnricher.enrich(snapshot, timeoutSeconds);
                }
            }
            offerTelemetrySuccess(host, probeMode, snapshot, durationMs);
            current.onDataReceived(host, snapshot);
            evaluateEndpointDown(host, snapshot);
            evaluateLatencyHigh(host, snapshot);
        }
        if (outcome.routeChanged() && BurstSchedulePolicy.shouldArmBurst(outcome.oldIps(), outcome.newIps())) {
            burstPolicy.onRouteChange(host, Instant.now());
        }
        if (outcome.routeChanged()) {
            offerTelemetryRouteChange(host, outcome.oldIps(), outcome.newIps(), probeMode);
            current.onRouteChanged(host, outcome.oldIps(), outcome.newIps());
            dispatchRouteChangeAlert(host, outcome.oldIps(), outcome.newIps());
        } else if (isFirstBaseline(previousIps, outcome.currentIps())) {
            persistBaselineRouteChange(host, outcome.currentIps());
            offerTelemetryRouteChange(host, List.of(), outcome.currentIps(), probeMode);
            current.onRouteChanged(host, List.of(), outcome.currentIps());
        }
    }

    /** Target reachable when a hop IP matches {@code targetIp}, else any reachable hop. */
    static boolean isTargetReachable(RouteSnapshot snapshot) {
        String targetIp = snapshot.targetIp();
        if (targetIp != null && !targetIp.isBlank()) {
            for (var node : snapshot.nodes()) {
                if (node.isReachable() && targetIp.equals(node.ip())) {
                    return true;
                }
            }
            return false;
        }
        for (var node : snapshot.nodes()) {
            if (node.isReachable()) {
                return true;
            }
        }
        return false;
    }

    private void offerTelemetrySuccess(
            String host, HostProbeMode probeMode, RouteSnapshot snapshot, double durationMs) {
        TelemetryBus bus = telemetryBus;
        if (bus == null) {
            return;
        }
        Instant ts = Instant.now();
        Map<String, String> labels = telemetryLabels(probeMode);
        try {
            bus.offerSample(new MetricSample(
                    MetricNames.TARGET_REACHABLE, isTargetReachable(snapshot) ? 1.0 : 0.0, host, null, labels, ts));
            bus.offerSample(new MetricSample(MetricNames.TRACE_DURATION_MS, durationMs, host, null, labels, ts));
            for (HopNode node : snapshot.nodes()) {
                double lossPct = node.isReachable() && node.pingMs() != null ? 0.0 : 100.0;
                bus.offerSample(new MetricSample(MetricNames.HOP_LOSS_PCT, lossPct, host, node.hop(), labels, ts));
                if (node.pingMs() != null && node.isReachable()) {
                    bus.offerSample(MetricSample.rttMs(host, node.hop(), node.pingMs(), labels, ts));
                }
            }
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry sample offer failed for {}: {}", host, ex.getMessage());
        }
    }

    private void offerTelemetryFailure(String host, String message, HostProbeMode probeMode, double durationMs) {
        TelemetryBus bus = telemetryBus;
        if (bus == null) {
            return;
        }
        Instant ts = Instant.now();
        Map<String, String> labels = telemetryLabels(probeMode);
        try {
            // No TARGET_REACHABLE sample: probe_error sets unreachable without clearHostRtt (P15 parity).
            bus.offerSample(new MetricSample(MetricNames.TRACE_DURATION_MS, durationMs, host, null, labels, ts));
            bus.offerEvent(TelemetryEvent.probeError(host, message, labels, ts));
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry failure offer failed for {}: {}", host, ex.getMessage());
        }
    }

    private void offerTelemetryRouteChange(
            String host, List<String> oldIps, List<String> newIps, HostProbeMode probeMode) {
        TelemetryBus bus = telemetryBus;
        if (bus == null) {
            return;
        }
        try {
            bus.offerEvent(TelemetryEvent.routeChange(host, oldIps, newIps, telemetryLabels(probeMode), Instant.now()));
        } catch (RuntimeException ex) {
            LOG.warn("Telemetry route_change offer failed for {}: {}", host, ex.getMessage());
        }
    }

    private Map<String, String> telemetryLabels(HostProbeMode probeMode) {
        return MetricNames.javaLabels(alertProfileName, probeMode.yamlValue());
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

    private void evaluateEndpointDown(String host, RouteSnapshot snapshot) {
        EndpointDownRuleConfig rule = endpointDownRule;
        if (rule == null || !rule.enabled() || snapshot == null) {
            return;
        }
        boolean down = !isTargetReachable(snapshot);
        Instant now = Instant.now();
        try {
            alertRuleEngine
                    .observeEndpointDown(host, down, now, alertProfileName, rule)
                    .ifPresent(this::onQualityAlertEdge);
        } catch (RuntimeException ex) {
            LOG.warn("endpoint_down rule failed for {}: {}", host, ex.getMessage());
        }
    }

    private void evaluateLatencyHigh(String host, RouteSnapshot snapshot) {
        LatencyHighRuleConfig rule = latencyHighRule;
        if (rule == null || !rule.enabled() || snapshot == null) {
            return;
        }
        if (!isTargetReachable(snapshot)) {
            return;
        }
        OptionalDouble rtt = terminalRttMs(snapshot);
        if (rtt.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        try {
            alertRuleEngine
                    .observeLatencyHigh(host, rtt.getAsDouble(), now, alertProfileName, rule)
                    .ifPresent(this::onQualityAlertEdge);
        } catch (RuntimeException ex) {
            LOG.warn("latency_high rule failed for {}: {}", host, ex.getMessage());
        }
    }

    /** Terminal / target-hop RTT when reachable; empty when missing. */
    static OptionalDouble terminalRttMs(RouteSnapshot snapshot) {
        if (snapshot == null || snapshot.nodes().isEmpty()) {
            return OptionalDouble.empty();
        }
        String targetIp = snapshot.targetIp();
        if (targetIp != null && !targetIp.isBlank()) {
            for (HopNode node : snapshot.nodes()) {
                if (node.isReachable() && targetIp.equals(node.ip()) && node.pingMs() != null) {
                    return OptionalDouble.of(node.pingMs());
                }
            }
            return OptionalDouble.empty();
        }
        for (int i = snapshot.nodes().size() - 1; i >= 0; i--) {
            HopNode node = snapshot.nodes().get(i);
            if (node.isReachable() && node.pingMs() != null) {
                return OptionalDouble.of(node.pingMs());
            }
        }
        return OptionalDouble.empty();
    }

    private void onQualityAlertEdge(QualityAlertEvent event) {
        persistQualityAlert(event);
        if (QualityAlertEvent.STATE_RESOLVED.equals(event.state()) && !notifyResolved) {
            return;
        }
        dispatchQualityAlert(event);
    }

    private void persistQualityAlert(QualityAlertEvent event) {
        PersistenceEventWriter events = persistenceEvents;
        if (events == null || event == null) {
            return;
        }
        try {
            events.writeQualityAlert(event);
        } catch (RuntimeException ex) {
            LOG.warn("Persistence endpoint_down failed for {}: {}", event.host(), ex.getMessage());
        }
    }

    private void dispatchQualityAlert(QualityAlertEvent event) {
        AlertDispatcher dispatcher = alertDispatcher;
        if (dispatcher == null || event == null) {
            return;
        }
        try {
            dispatcher.dispatchQuality(event);
        } catch (RuntimeException ex) {
            LOG.warn("Quality alert dispatch failed for {}: {}", event.host(), ex.getMessage());
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
        return registry.mappedMode(host, profileProbeMode);
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
        // Bus lifecycle is owned by TelemetryAttachment / caller — drop the pointer only.
        telemetryBus = null;
    }
}

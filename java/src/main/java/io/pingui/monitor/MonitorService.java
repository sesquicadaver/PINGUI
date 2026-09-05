package io.pingui.monitor;

import io.pingui.config.AlertSilenceConfig;
import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.config.LatencyHighRuleConfig;
import io.pingui.config.PingExpertEntry;
import io.pingui.dns.BoundedForwardDnsLookup;
import io.pingui.dns.DnsControlTracker;
import io.pingui.dns.ForwardDnsLookup;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.persistence.PersistencePolicy;
import io.pingui.persistence.PersistencePolicyHolder;
import io.pingui.probe.MtrHopProbers;
import io.pingui.probe.MtrProbe;
import io.pingui.probe.ProbeMode;
import io.pingui.probe.RouteProbe;
import io.pingui.probe.RouteProbeFactory;
import io.pingui.telemetry.TelemetryBus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Background polling of enabled hosts (cross-platform, no Qt). */
public final class MonitorService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MonitorService.class);
    private static final int MIN_PROBE_POOL_THREADS = 4;

    public interface Listener {
        void onDataReceived(String host, RouteSnapshot snapshot);

        /**
         * Same as {@link #onDataReceived(String, RouteSnapshot)} with MTR freshness scope (P32-001).
         * Default delegates to the two-arg form.
         */
        default void onDataReceived(String host, RouteSnapshot snapshot, PollSampleScope sampleScope) {
            onDataReceived(host, snapshot);
        }

        /**
         * Authoritative poll projection (P33-002): freshness scope plus confirmed route-change flag from
         * the probe outcome (SessionStore must not re-derive topology from partial MTR snapshots).
         */
        default void onDataReceived(
                String host, RouteSnapshot snapshot, PollSampleScope sampleScope, boolean routeChanged) {
            onDataReceived(host, snapshot, sampleScope);
        }

        void onRouteChanged(String host, List<String> oldIps, List<String> newIps);

        void onProbeError(String host, String message);

        /**
         * Invoked after a completed poll when neither {@link #onDataReceived} nor {@link
         * #onProbeError} runs (e.g. empty success snapshot), so UI can refresh liveness counters.
         */
        default void onPollFinished(String host) {}
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
    private final AlertRuleEngine alertRuleEngine = new AlertRuleEngine();
    private final PollResultEffects pollEffects = new PollResultEffects(alertRuleEngine);
    private volatile PingExpertResolver expertResolver;
    private volatile HostProbeModeResolver probeModeResolver;
    private volatile HostPollIntervalResolver intervalResolver;
    private HostProbeMode profileProbeMode = HostProbeMode.TRACE;
    private volatile PersistenceEventWriter persistenceEvents;
    private final PersistencePolicyHolder persistencePolicy = new PersistencePolicyHolder();
    private final BurstSchedulePolicy burstPolicy = new BurstSchedulePolicy();
    private final TraceConcurrencyLimiter traceLimiter;
    private final BoundedForwardDnsLookup ownedForwardDns = BoundedForwardDnsLookup.systemDefault();
    private final ExecutorService dnsControlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pingui-dns-control-" + DNS_CONTROL_SEQ.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private volatile DnsControlTracker dnsControl = new DnsControlTracker(ownedForwardDns);
    private static final AtomicInteger DNS_CONTROL_SEQ = new AtomicInteger();

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
        pollEffects.setAlertDispatcher(alertDispatcher);
    }

    /**
     * In-memory {@code endpoint_down} rule (P21-002). Default disabled; YAML/GUI wiring is P21-003.
     */
    public void setEndpointDownRule(EndpointDownRuleConfig endpointDownRule) {
        pollEffects.setEndpointDownRule(endpointDownRule);
        alertRuleEngine.clearAll();
    }

    /**
     * In-memory {@code latency_high} rule (P23). Default disabled. Clears latency baselines for all hosts.
     */
    public void setLatencyHighRule(LatencyHighRuleConfig latencyHighRule) {
        pollEffects.setLatencyHighRule(latencyHighRule);
        alertRuleEngine.clearAll();
    }

    /** When true, emit quality RESOLVED after clear_after successes (ADR). */
    public void setNotifyResolved(boolean notifyResolved) {
        pollEffects.setNotifyResolved(notifyResolved);
    }

    /** Alert silence / maintenance windows (P29-003). Does not stop probing. */
    public void setAlertSilence(AlertSilenceConfig silence) {
        pollEffects.setAlertSilence(silence);
    }

    /** Supplies per-host tags for silence matching (P29-003). */
    public void setHostTagsResolver(Function<String, List<String>> hostTagsResolver) {
        pollEffects.setHostTagsResolver(hostTagsResolver);
    }

    /**
     * Supplies measured terminal-hop loss/jitter from an RTT series (P32-003). Absent → {@code null}
     * metrics in {@code poll_result}.
     */
    public void setMeasuredHopStatsResolver(
            Function<String, io.pingui.model.Models.HopStatsSummary> measuredHopStatsResolver) {
        pollEffects.setMeasuredHopStatsResolver(measuredHopStatsResolver);
    }

    /** Session quality problem summary for host-row badge (P22-002 / P23). */
    public Optional<HostProblemSummary> hostProblemSummary(String host) {
        return alertRuleEngine.problemSummary(host, Instant.now());
    }

    /**
     * Correlates hosts currently in {@code FIRING} using their session routes (P29-001).
     *
     * @param store session routes + host set; must not be null
     * @return empty when fewer than two firing hosts have usable routes
     */
    public Optional<ProblemCorrelation> correlateActiveProblems(SessionStore store) {
        return correlateActiveProblems(store, Instant.now());
    }

    /** Same as {@link #correlateActiveProblems(SessionStore)} with an explicit clock. */
    public Optional<ProblemCorrelation> correlateActiveProblems(SessionStore store, Instant now) {
        if (store == null) {
            throw new IllegalArgumentException("store required");
        }
        Instant at = now != null ? now : Instant.now();
        ArrayList<ProblemHostObservation> observations = new ArrayList<>();
        for (String host : store.hosts()) {
            Optional<HostProblemSummary> summary = alertRuleEngine.problemSummary(host, at);
            if (summary.isEmpty()
                    || !HostProblemSummary.STATE_FIRING.equals(summary.get().lastState())) {
                continue;
            }
            Instant started = summary.get().lastStartedAt();
            if (started == null) {
                started = at;
            }
            observations.add(new ProblemHostObservation(host, store.get(host).getCurrentRoute(), started));
        }
        return ProblemCorrelator.correlate(observations, store.hosts().size());
    }

    /**
     * Acknowledges the host problem (badge off until next FIRING). Counters preserved. Persists
     * {@code problem_ack} when a session DB writer is attached (P29-002).
     *
     * @return {@code true} when engine had state for the host
     */
    public boolean ackHostProblem(String host) {
        boolean acked = alertRuleEngine.ack(host);
        if (acked) {
            PersistenceEventWriter writer = persistenceEvents;
            if (writer != null) {
                try {
                    writer.writeProblemAck(host, Instant.now());
                } catch (RuntimeException ex) {
                    LOG.warn("problem_ack persistence failed for {}: {}", host, ex.getMessage());
                }
            }
        }
        return acked;
    }

    public void setAlertProfileName(String alertProfileName) {
        pollEffects.setAlertProfileName(alertProfileName);
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
        pollEffects.setPersistenceEventWriter(persistenceEvents);
    }

    /** Test hook: replace forward-DNS lookup used by hostname DNS control (P29-004 / P32-005). */
    void setForwardDnsLookupForTests(ForwardDnsLookup lookup) {
        dnsControl = lookup == null ? new DnsControlTracker(ownedForwardDns) : new DnsControlTracker(lookup);
    }

    /** Optional telemetry bus (P16-013); null disables offers. Must not block poll. */
    public void setTelemetryBus(TelemetryBus telemetryBus) {
        pollEffects.setTelemetryBus(telemetryBus);
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

    /** True while the poll scheduler has not been closed. */
    public boolean isRunning() {
        return running.get();
    }

    /** Latest poll timestamp across all hosts, if any. */
    public Optional<Instant> latestPollAt() {
        Instant best = null;
        for (String host : registry.hosts()) {
            Instant at = registry.lastPollAt(host);
            if (at != null && (best == null || at.isAfter(best))) {
                best = at;
            }
        }
        return Optional.ofNullable(best);
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
        poller.resetMtrHost(host);
    }

    public void renameHost(String oldHost, String newHost) {
        registry.rename(oldHost, newHost);
        burstPolicy.renameHost(oldHost, newHost);
        poller.renameMtrHost(oldHost, newHost);
    }

    public void setHostEnabled(String host, boolean hostEnabled) {
        registry.setEnabled(host, hostEnabled);
    }

    public void setHostProbeMode(String host, HostProbeMode probeMode) {
        registry.setProbeMode(host, probeMode);
        burstPolicy.clearHost(host);
        poller.resetMtrHost(host);
        // New probe mode must not inherit the previous EWMA baseline (P33-005).
        alertRuleEngine.clearLatencyHost(host);
    }

    /** Liveness counters for the current probe mode (reset on {@link #setHostProbeMode}). */
    public HostPollCounters pollCounters(String host) {
        return registry.pollCounters(host);
    }

    /** Time of the last started poll for {@code host}, if any (P31-003). */
    public Optional<Instant> lastPollAt(String host) {
        return Optional.ofNullable(registry.lastPollAt(host));
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

    /**
     * Queues one poll for {@code host}. Reserves {@code inFlight} <em>before</em> {@code
     * probePool.execute} so PING_ONLY / TRACE cannot accumulate unbounded duplicate runnables while a
     * slow poll is still queued or running (P28-002).
     */
    private void dispatchDueHost(String host, HostProbeMode mode) {
        AtomicBoolean inFlight = registry.inFlightFlag(host);
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        boolean holdTracePermit = TraceConcurrencyLimiter.limitsConcurrency(mode);
        if (holdTracePermit && !traceLimiter.tryAcquire()) {
            inFlight.set(false);
            return;
        }
        try {
            probePool.execute(() -> {
                try {
                    if (running.get()) {
                        pollHostOnce(host);
                    }
                } finally {
                    if (holdTracePermit) {
                        traceLimiter.release();
                    }
                    inFlight.set(false);
                }
            });
        } catch (RejectedExecutionException ex) {
            if (holdTracePermit) {
                traceLimiter.release();
            }
            inFlight.set(false);
            LOG.warn("Probe pool rejected poll for {}: {}", host, ex.getMessage());
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
        try {
            List<String> previousIps = start.previousIps();
            HostProbeMode mappedAtStart = start.mappedMode();
            HostProbeMode probeMode = resolveProbeMode(host);
            long startedNanos = System.nanoTime();
            HostPollOutcome outcome =
                    switch (probeMode) {
                        case PING_ONLY -> poller.pollHostPingOnly(
                                host, previousIps, timeoutSeconds, resolveExpert(host));
                        case TCP_CONNECT -> poller.pollHostTcpConnect(host, previousIps, timeoutSeconds);
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
            boolean probeFailed = outcome.error() != null;
            registry.recordPoll(host, probeFailed);
            if (probeFailed) {
                PersistenceEventWriter events = persistenceEvents;
                if (events != null) {
                    try {
                        events.writeProbeError(host, outcome.error());
                    } catch (RuntimeException ex) {
                        LOG.warn("Persistence probe_error failed for {}: {}", host, ex.getMessage());
                    }
                }
                pollEffects.offerTelemetryFailure(host, outcome.error(), probeMode, durationMs);
                // Monitor/DNS/internal failure is not a sampled downtime (P33-004).
                pollEffects.recordPollResult(
                        host, probeMode, null, durationMs, outcome.error(), outcome.probeOutcome(), false);
                current.onProbeError(host, outcome.error());
                return;
            }
            PollSampleScope sampleScope = outcome.sampleScope();
            // MTR: only advance the announced route after the target was sampled (P32-001).
            if (probeMode != HostProbeMode.MTR || sampleScope.targetSampled()) {
                registry.putLastRoute(host, outcome.currentIps());
            }
            boolean deliveredSnapshot = false;
            if (outcome.snapshot() != null && registry.contains(host)) {
                RouteSnapshot snapshot = outcome.snapshot();
                if (!probeMode.isTargetOnly()) {
                    PingExpertEntry expert = resolveExpert(host);
                    if (expert.isConfigured()) {
                        snapshot = expertEnricher.enrich(snapshot, expert, timeoutSeconds);
                    } else if (sampleScope.targetSampled()) {
                        // Avoid treating the last discovered router as the target during MTR discovery.
                        snapshot = defaultTargetPingEnricher.enrich(snapshot, timeoutSeconds);
                    }
                }
                pollEffects.offerTelemetrySuccess(host, probeMode, snapshot, durationMs, sampleScope);
                current.onDataReceived(host, snapshot, sampleScope, outcome.routeChanged());
                deliveredSnapshot = true;
                if (sampleScope.targetSampled()) {
                    pollEffects.recordPollResult(
                            host, probeMode, snapshot, durationMs, null, outcome.probeOutcome(), true);
                    pollEffects.evaluateEndpointDown(host, snapshot);
                    // Reset EWMA before latency_high so the first RTT on a new path is warm-up (P33-005).
                    if (outcome.routeChanged() && !outcome.oldIps().isEmpty()) {
                        pollEffects.resetLatencyBaseline(host);
                    }
                    pollEffects.evaluateLatencyHigh(host, snapshot);
                }
            }
            if (outcome.routeChanged() && BurstSchedulePolicy.shouldArmBurst(outcome.oldIps(), outcome.newIps())) {
                burstPolicy.onRouteChange(host, Instant.now());
            }
            if (outcome.routeChanged()) {
                pollEffects.offerTelemetryRouteChange(host, outcome.oldIps(), outcome.newIps(), probeMode);
                current.onRouteChanged(host, outcome.oldIps(), outcome.newIps());
                pollEffects.dispatchRouteChangeAlert(host, outcome.oldIps(), outcome.newIps());
            } else if (sampleScope.targetSampled()
                    && PollResultEffects.isFirstBaseline(previousIps, outcome.currentIps())) {
                pollEffects.persistBaselineRouteChange(host, outcome.currentIps());
                pollEffects.offerTelemetryRouteChange(host, List.of(), outcome.currentIps(), probeMode);
                current.onRouteChanged(host, List.of(), outcome.currentIps());
            }
            if (!deliveredSnapshot) {
                current.onPollFinished(host);
            }
        } finally {
            // After probe effects so DNS latency never delays ICMP/MTR scheduling (P29-004).
            observeDnsControl(host);
        }
    }

    /**
     * Forward-DNS control for hostname targets (P29-004 / P32-005). Runs on a dedicated executor so
     * resolver latency never blocks probe workers. Persists distinct dns_change events only — never
     * opens quality incidents or alert dispatch.
     */
    private void observeDnsControl(String host) {
        try {
            dnsControlExecutor.execute(() -> {
                try {
                    var event = dnsControl.observe(host);
                    if (event.isEmpty()) {
                        return;
                    }
                    PersistenceEventWriter events = persistenceEvents;
                    if (events != null) {
                        events.writeDnsChange(event.get());
                    }
                } catch (RuntimeException ex) {
                    LOG.warn("DNS control failed for {}: {}", host, ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            LOG.warn("DNS control executor rejected observe for {}: {}", host, ex.getMessage());
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
        dnsControlExecutor.shutdownNow();
        ownedForwardDns.close();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            probePool.awaitTermination(5, TimeUnit.SECONDS);
            dnsControlExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        // Bus lifecycle is owned by TelemetryAttachment / caller — drop the pointer only.
        pollEffects.clearTelemetry();
    }
}

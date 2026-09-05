package io.pingui.monitor;

import io.pingui.config.AlertSilenceConfig;
import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.config.LatencyHighRuleConfig;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.persistence.PersistenceEventWriter;
import io.pingui.probe.ProbeOutcome;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-poll side effects: telemetry, alert evaluation/dispatch, and persistence (P26-006 / P32-003).
 * Poll orchestration stays in {@link MonitorService}.
 */
final class PollResultEffects {
    private static final Logger LOG = LoggerFactory.getLogger(PollResultEffects.class);

    private final AlertRuleEngine alertRuleEngine;
    private final TelemetryEmission telemetry = new TelemetryEmission();
    private volatile AlertDispatcher alertDispatcher = AlertDispatcher.noop();
    private volatile PersistenceEventWriter persistenceEvents;
    private volatile EndpointDownRuleConfig endpointDownRule = EndpointDownRuleConfig.disabled();
    private volatile LatencyHighRuleConfig latencyHighRule = LatencyHighRuleConfig.disabled();
    private volatile boolean notifyResolved;
    private volatile String alertProfileName = "default";
    private volatile AlertSilenceConfig alertSilence = AlertSilenceConfig.none();
    private volatile Function<String, List<String>> hostTagsResolver = host -> List.of();
    /** Optional measured hop summary (loss/jitter from RTT series); never invents values. */
    private volatile Function<String, HopStatsSummary> measuredHopStatsResolver = host -> null;

    PollResultEffects(AlertRuleEngine alertRuleEngine) {
        this.alertRuleEngine = alertRuleEngine;
    }

    void setAlertDispatcher(AlertDispatcher alertDispatcher) {
        this.alertDispatcher = alertDispatcher != null ? alertDispatcher : AlertDispatcher.noop();
    }

    void setEndpointDownRule(EndpointDownRuleConfig endpointDownRule) {
        this.endpointDownRule = endpointDownRule != null ? endpointDownRule : EndpointDownRuleConfig.disabled();
    }

    void setLatencyHighRule(LatencyHighRuleConfig latencyHighRule) {
        this.latencyHighRule = latencyHighRule != null ? latencyHighRule : LatencyHighRuleConfig.disabled();
    }

    void setNotifyResolved(boolean notifyResolved) {
        this.notifyResolved = notifyResolved;
    }

    void setAlertSilence(AlertSilenceConfig alertSilence) {
        this.alertSilence = alertSilence != null ? alertSilence : AlertSilenceConfig.none();
    }

    void setHostTagsResolver(Function<String, List<String>> hostTagsResolver) {
        this.hostTagsResolver = hostTagsResolver != null ? hostTagsResolver : host -> List.of();
    }

    void setMeasuredHopStatsResolver(Function<String, HopStatsSummary> measuredHopStatsResolver) {
        this.measuredHopStatsResolver = measuredHopStatsResolver != null ? measuredHopStatsResolver : host -> null;
    }

    void setAlertProfileName(String alertProfileName) {
        if (alertProfileName == null || alertProfileName.isBlank()) {
            this.alertProfileName = "default";
        } else {
            this.alertProfileName = alertProfileName;
        }
        telemetry.setAlertProfileName(this.alertProfileName);
    }

    void setPersistenceEventWriter(PersistenceEventWriter persistenceEvents) {
        this.persistenceEvents = persistenceEvents;
    }

    void setTelemetryBus(io.pingui.telemetry.TelemetryBus telemetryBus) {
        telemetry.setTelemetryBus(telemetryBus);
    }

    void clearTelemetry() {
        telemetry.clear();
    }

    void offerTelemetrySuccess(
            String host, HostProbeMode probeMode, RouteSnapshot snapshot, double durationMs, PollSampleScope scope) {
        telemetry.offerSuccess(host, probeMode, snapshot, durationMs, scope != null ? scope : PollSampleScope.FULL);
    }

    void offerTelemetrySuccess(String host, HostProbeMode probeMode, RouteSnapshot snapshot, double durationMs) {
        offerTelemetrySuccess(host, probeMode, snapshot, durationMs, PollSampleScope.FULL);
    }

    void offerTelemetryFailure(String host, String message, HostProbeMode probeMode, double durationMs) {
        telemetry.offerFailure(host, message, probeMode, durationMs);
    }

    void offerTelemetryRouteChange(String host, List<String> oldIps, List<String> newIps, HostProbeMode probeMode) {
        telemetry.offerRouteChange(host, oldIps, newIps, probeMode);
    }

    /**
     * Writes canonical {@code poll_result} for a finished poll (P30-003 / P32-003). Safe no-op without
     * DB. Does not invent loss/jitter from reachability alone.
     */
    void recordPollResult(
            String host, HostProbeMode probeMode, RouteSnapshot snapshot, double durationMs, String error) {
        recordPollResult(
                host, probeMode, snapshot, durationMs, error, deriveProbeOutcome(snapshot, error, null), error == null);
    }

    void recordPollResult(
            String host,
            HostProbeMode probeMode,
            RouteSnapshot snapshot,
            double durationMs,
            String error,
            ProbeOutcome probeOutcome,
            boolean targetSampled) {
        PersistenceEventWriter events = persistenceEvents;
        if (events == null || host == null || host.isBlank() || probeMode == null) {
            return;
        }
        ProbeOutcome outcome = probeOutcome != null ? probeOutcome : deriveProbeOutcome(snapshot, error, null);
        Boolean reachable = null;
        Double terminalRtt = null;
        Double lossPercent = null;
        Double jitterMs = null;
        Long routeId = null;
        if (error != null) {
            reachable = false;
        } else if (snapshot != null) {
            reachable = TelemetryEmission.isTargetReachable(snapshot);
            OptionalDouble rtt = terminalRttMs(snapshot);
            if (rtt.isPresent()) {
                terminalRtt = rtt.getAsDouble();
            }
            HopStatsSummary measured = resolveMeasuredStats(host);
            if (measured != null) {
                // Session hop stats accumulate probes → measured loss; jitter only with RTT series.
                lossPercent = measured.lossPct();
                jitterMs = measured.jitterMs();
            }
            try {
                routeId = events.observeRoute(host, snapshot.nodes(), Instant.now());
            } catch (RuntimeException ex) {
                LOG.warn("Persistence route upsert failed for {}: {}", host, ex.getMessage());
            }
        }
        try {
            events.writePollResult(
                    host,
                    probeMode.yamlValue(),
                    Instant.now(),
                    reachable,
                    terminalRtt,
                    jitterMs,
                    lossPercent,
                    durationMs,
                    routeId,
                    error,
                    outcome,
                    targetSampled);
        } catch (RuntimeException ex) {
            LOG.warn("Persistence poll_result failed for {}: {}", host, ex.getMessage());
        }
    }

    private HopStatsSummary resolveMeasuredStats(String host) {
        Function<String, HopStatsSummary> resolver = measuredHopStatsResolver;
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.apply(host);
        } catch (RuntimeException ex) {
            LOG.warn("Measured hop stats resolve failed for {}: {}", host, ex.getMessage());
            return null;
        }
    }

    static ProbeOutcome deriveProbeOutcome(RouteSnapshot snapshot, String error, ProbeOutcome hinted) {
        if (hinted != null) {
            return hinted;
        }
        if (error != null) {
            return ProbeOutcome.NETWORK_ERROR;
        }
        if (snapshot == null) {
            return ProbeOutcome.NETWORK_ERROR;
        }
        return TelemetryEmission.isTargetReachable(snapshot) ? ProbeOutcome.SUCCESS : ProbeOutcome.TIMEOUT;
    }

    void evaluateEndpointDown(String host, RouteSnapshot snapshot) {
        EndpointDownRuleConfig rule = endpointDownRule;
        if (rule == null || !rule.enabled() || snapshot == null) {
            return;
        }
        boolean down = !TelemetryEmission.isTargetReachable(snapshot);
        Instant now = Instant.now();
        try {
            alertRuleEngine
                    .observeEndpointDown(host, down, now, alertProfileName, rule)
                    .ifPresent(this::onQualityAlertEdge);
        } catch (RuntimeException ex) {
            LOG.warn("endpoint_down rule failed for {}: {}", host, ex.getMessage());
        }
    }

    void evaluateLatencyHigh(String host, RouteSnapshot snapshot) {
        LatencyHighRuleConfig rule = latencyHighRule;
        if (rule == null || !rule.enabled() || snapshot == null) {
            return;
        }
        if (!TelemetryEmission.isTargetReachable(snapshot)) {
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

    void persistBaselineRouteChange(String host, List<String> currentIps) {
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

    void dispatchRouteChangeAlert(String host, List<String> oldIps, List<String> newIps) {
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
        if (isSilenced(host)) {
            return;
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

    static boolean isFirstBaseline(List<String> previousIps, List<String> currentIps) {
        return previousIps.isEmpty() && currentIps != null && !currentIps.isEmpty();
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
        if (isSilenced(event.host())) {
            return;
        }
        dispatchQualityAlert(event);
    }

    private boolean isSilenced(String host) {
        AlertSilenceConfig silence = alertSilence;
        if (silence == null || silence.isEmpty()) {
            return false;
        }
        List<String> tags;
        try {
            tags = hostTagsResolver.apply(host);
        } catch (RuntimeException ex) {
            LOG.warn("host tags resolve failed for {}: {}", host, ex.getMessage());
            tags = List.of();
        }
        return silence.isSilenced(host, tags, Instant.now());
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
}

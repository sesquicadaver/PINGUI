package io.pingui.monitor;

import io.pingui.config.EndpointDownRuleConfig;
import io.pingui.config.LatencyHighRuleConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure quality-alert state machine for {@code endpoint_down} and {@code latency_high} (P21-002 /
 * P22-002 / P23 / ADR_ALERT_RULES + ADR_HOST_PROBLEM_INDICATOR).
 *
 * <p>Thread-safe per host. Probe errors are not observed here. Session problem stats ({@link
 * HostProblemSummary}) survive channel suppressions and {@link #ack}.
 */
public final class AlertRuleEngine {
    private enum Phase {
        OK,
        PENDING,
        FIRING
    }

    private final Map<String, HostState> endpointStates = new ConcurrentHashMap<>();
    private final Map<String, LatencyState> latencyStates = new ConcurrentHashMap<>();

    /**
     * Observes one poll sample for endpoint reachability.
     *
     * @param down {@code true} when the endpoint is unreachable / timed out
     * @return FIRING or RESOLVED event when a lifecycle edge occurs
     */
    public Optional<QualityAlertEvent> observeEndpointDown(
            String host, boolean down, Instant now, String profile, EndpointDownRuleConfig rule) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host required");
        }
        if (rule == null || !rule.enabled()) {
            return Optional.empty();
        }
        Instant at = now != null ? now : Instant.now();
        HostState state = endpointStates.computeIfAbsent(host, ignored -> new HostState());
        synchronized (state) {
            if (down) {
                return onConditionTrue(
                        host,
                        at,
                        profile,
                        rule.failAfter(),
                        rule.clearAfter(),
                        rule.cooldownMinutes(),
                        state,
                        QualityAlertEvent::endpointDownFiring,
                        QualityAlertEvent::endpointDownResolved);
            }
            return onConditionFalse(
                    host,
                    at,
                    profile,
                    rule.failAfter(),
                    rule.clearAfter(),
                    state,
                    QualityAlertEvent::endpointDownResolved);
        }
    }

    /**
     * Observes one successful terminal RTT for {@code latency_high}.
     *
     * <p>Compares {@code rttMs} to {@code multiplier × AVG} using AVG from samples <em>before</em>
     * this probe. Updates the running mean after the comparison. Call only for reachable targets.
     *
     * @return FIRING or RESOLVED when a lifecycle edge occurs
     */
    public Optional<QualityAlertEvent> observeLatencyHigh(
            String host, double rttMs, Instant now, String profile, LatencyHighRuleConfig rule) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host required");
        }
        if (Double.isNaN(rttMs) || Double.isInfinite(rttMs) || rttMs < 0.0) {
            throw new IllegalArgumentException("rttMs must be a finite >= 0 value");
        }
        if (rule == null || !rule.enabled()) {
            return Optional.empty();
        }
        Instant at = now != null ? now : Instant.now();
        LatencyState state = latencyStates.computeIfAbsent(host, ignored -> new LatencyState());
        synchronized (state) {
            boolean high = isLatencyHigh(state, rttMs, rule);
            // Do not fold bad samples into AVG — otherwise ×2 baseline climbs and fail_after never completes.
            if (!high) {
                updateLatencyAvg(state, rttMs);
            }
            if (high) {
                return onConditionTrue(
                        host,
                        at,
                        profile,
                        rule.failAfter(),
                        rule.clearAfter(),
                        rule.cooldownMinutes(),
                        state,
                        (h, p, t, d) ->
                                QualityAlertEvent.latencyHighFiring(h, p, t, withLatencyDetail(d, rttMs, state, rule)),
                        (h, p, t, d) -> QualityAlertEvent.latencyHighResolved(
                                h, p, t, withLatencyDetail(d, rttMs, state, rule)));
            }
            return onConditionFalse(
                    host,
                    at,
                    profile,
                    rule.failAfter(),
                    rule.clearAfter(),
                    state,
                    (h, p, t, d) ->
                            QualityAlertEvent.latencyHighResolved(h, p, t, withLatencyDetail(d, rttMs, state, rule)));
        }
    }

    /**
     * Session problem view for a host: prefers unread {@code endpoint_down}, then unread {@code
     * latency_high}, else any rule with session FIRING history.
     */
    public Optional<HostProblemSummary> problemSummary(String host, Instant now) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        Instant at = now != null ? now : Instant.now();
        Optional<HostProblemSummary> down = summaryOf(endpointStates.get(host), host, at, true);
        Optional<HostProblemSummary> latency = summaryOf(latencyStates.get(host), host, at, false);
        if (down.isPresent() && down.get().unread()) {
            return down;
        }
        if (latency.isPresent() && latency.get().unread()) {
            return latency;
        }
        if (down.isPresent()) {
            return down;
        }
        return latency;
    }

    public Optional<HostProblemSummary> problemSummary(String host) {
        return problemSummary(host, Instant.now());
    }

    /**
     * Marks host problems as viewed: clears {@code unread} on both rules until the next FIRING.
     *
     * @return {@code true} when any host state existed
     */
    public boolean ack(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        boolean found = false;
        HostState endpoint = endpointStates.get(host);
        if (endpoint != null) {
            synchronized (endpoint) {
                ackState(endpoint);
            }
            found = true;
        }
        LatencyState latency = latencyStates.get(host);
        if (latency != null) {
            synchronized (latency) {
                ackState(latency);
            }
            found = true;
        }
        return found;
    }

    /** Drops per-host endpoint_down state. */
    public void clearEndpointHost(String host) {
        if (host != null) {
            endpointStates.remove(host);
        }
    }

    /** Drops per-host latency_high state (including AVG). */
    public void clearLatencyHost(String host) {
        if (host != null) {
            latencyStates.remove(host);
        }
    }

    /** Drops per-host state for both rules (tests / host removed). */
    public void clearHost(String host) {
        clearEndpointHost(host);
        clearLatencyHost(host);
    }

    public void clearAll() {
        endpointStates.clear();
        latencyStates.clear();
    }

    /** Package-visible for tests — running AVG before next sample, if any. */
    Optional<Double> latencyAvg(String host) {
        LatencyState state = latencyStates.get(host);
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            return state.sampleCount > 0 ? Optional.of(state.sumMs / state.sampleCount) : Optional.empty();
        }
    }

    private static boolean isLatencyHigh(LatencyState state, double rttMs, LatencyHighRuleConfig rule) {
        if (state.sampleCount < 1) {
            return false;
        }
        double avg = state.sumMs / state.sampleCount;
        boolean relative = rttMs >= rule.multiplier() * avg;
        boolean absolute = rule.hasAbsoluteThreshold() && rttMs >= rule.thresholdMs();
        return relative || absolute;
    }

    private static void updateLatencyAvg(LatencyState state, double rttMs) {
        state.sumMs += rttMs;
        state.sampleCount++;
    }

    private static Map<String, Object> withLatencyDetail(
            Map<String, Object> base, double rttMs, LatencyState state, LatencyHighRuleConfig rule) {
        Map<String, Object> map = new java.util.LinkedHashMap<>(base);
        map.put("rtt_ms", rttMs);
        if (state.sampleCount > 0) {
            map.put("avg_ms", state.sumMs / state.sampleCount);
        }
        map.put("multiplier", rule.multiplier());
        if (rule.hasAbsoluteThreshold()) {
            map.put("threshold_ms", rule.thresholdMs());
        }
        return map;
    }

    @FunctionalInterface
    private interface EdgeFactory {
        QualityAlertEvent create(String host, String profile, Instant now, Map<String, Object> detail);
    }

    private static Optional<QualityAlertEvent> onConditionTrue(
            String host,
            Instant now,
            String profile,
            int failAfter,
            int clearAfter,
            int cooldownMinutes,
            HostState state,
            EdgeFactory firingFactory,
            EdgeFactory resolvedFactory) {
        state.okStreak = 0;
        state.failStreak++;
        if (state.phase == Phase.FIRING) {
            return Optional.empty();
        }
        if (state.failStreak < failAfter) {
            state.phase = Phase.PENDING;
            return Optional.empty();
        }
        enterFiring(state, now);
        if (!cooldownElapsed(state, now, cooldownMinutes)) {
            return Optional.empty();
        }
        state.lastFiringEmit = now;
        Map<String, Object> detail = QualityAlertEvent.detailOf(failAfter, state.failStreak, clearAfter);
        return Optional.of(firingFactory.create(host, profile, now, detail));
    }

    private static Optional<QualityAlertEvent> onConditionFalse(
            String host,
            Instant now,
            String profile,
            int failAfter,
            int clearAfter,
            HostState state,
            EdgeFactory resolvedFactory) {
        state.failStreak = 0;
        if (state.phase != Phase.FIRING) {
            state.phase = Phase.OK;
            state.okStreak = 0;
            return Optional.empty();
        }
        state.okStreak++;
        if (state.okStreak < clearAfter) {
            return Optional.empty();
        }
        leaveFiring(state, now);
        Map<String, Object> detail = QualityAlertEvent.detailOf(failAfter, 0, clearAfter);
        return Optional.of(resolvedFactory.create(host, profile, now, detail));
    }

    private static void enterFiring(HostState state, Instant now) {
        state.phase = Phase.FIRING;
        state.fireCount++;
        state.unread = true;
        state.incidentStartedAt = now;
        state.lastStartedAt = now;
        state.lastState = HostProblemSummary.STATE_FIRING;
    }

    private static void leaveFiring(HostState state, Instant now) {
        state.phase = Phase.OK;
        state.okStreak = 0;
        if (state.incidentStartedAt != null) {
            Duration duration = Duration.between(state.incidentStartedAt, now);
            if (!duration.isNegative() && duration.compareTo(state.maxDuration) > 0) {
                state.maxDuration = duration;
            }
            state.incidentStartedAt = null;
        }
        state.lastResolvedAt = now;
        state.lastState = HostProblemSummary.STATE_RESOLVED;
    }

    private static boolean cooldownElapsed(HostState state, Instant now, int cooldownMinutes) {
        if (state.lastFiringEmit == null || cooldownMinutes <= 0) {
            return true;
        }
        Duration elapsed = Duration.between(state.lastFiringEmit, now);
        return !elapsed.isNegative() && elapsed.toMinutes() >= cooldownMinutes;
    }

    private static void ackState(HostState state) {
        state.unread = false;
        if (state.phase != Phase.FIRING) {
            state.lastState = HostProblemSummary.STATE_OK;
        }
    }

    private static Optional<HostProblemSummary> summaryOf(HostState state, String host, Instant now, boolean endpoint) {
        if (state == null) {
            return Optional.empty();
        }
        synchronized (state) {
            if (state.fireCount == 0) {
                return Optional.empty();
            }
            Duration maxDuration = state.maxDuration;
            if (state.incidentStartedAt != null) {
                Duration open = Duration.between(state.incidentStartedAt, now);
                if (!open.isNegative() && open.compareTo(maxDuration) > 0) {
                    maxDuration = open;
                }
            }
            String rule = endpoint ? QualityAlertEvent.EVENT_ENDPOINT_DOWN : QualityAlertEvent.EVENT_LATENCY_HIGH;
            String description = endpoint
                    ? HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN
                    : HostProblemSummary.DESCRIPTION_LATENCY_HIGH;
            return Optional.of(new HostProblemSummary(
                    host,
                    rule,
                    state.unread,
                    state.fireCount,
                    maxDuration,
                    state.lastStartedAt,
                    state.lastResolvedAt,
                    state.lastState,
                    description));
        }
    }

    private static class HostState {
        private Phase phase = Phase.OK;
        private int failStreak;
        private int okStreak;
        private Instant lastFiringEmit;
        private boolean unread;
        private int fireCount;
        private Duration maxDuration = Duration.ZERO;
        private Instant lastStartedAt;
        private Instant lastResolvedAt;
        private Instant incidentStartedAt;
        private String lastState = HostProblemSummary.STATE_OK;
    }

    private static final class LatencyState extends HostState {
        private double sumMs;
        private int sampleCount;
    }
}

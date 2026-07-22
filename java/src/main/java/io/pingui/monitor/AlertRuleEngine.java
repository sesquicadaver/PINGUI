package io.pingui.monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure quality-alert state machine for {@code endpoint_down} (P21-002 / ADR_ALERT_RULES).
 *
 * <p>Thread-safe per host. Probe errors are not observed here — only boolean reachability samples.
 */
public final class AlertRuleEngine {
    private enum Phase {
        OK,
        PENDING,
        FIRING
    }

    private final Map<String, HostState> states = new ConcurrentHashMap<>();

    /**
     * Observes one poll sample for a host.
     *
     * @param down {@code true} when the endpoint is unreachable / timed out
     * @return FIRING or RESOLVED event when a notification edge occurs
     */
    public Optional<QualityAlertEvent> observeEndpointDown(
            String host,
            boolean down,
            Instant now,
            String profile,
            EndpointDownRuleConfig rule,
            boolean notifyResolved) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host required");
        }
        if (rule == null || !rule.enabled()) {
            return Optional.empty();
        }
        Instant at = now != null ? now : Instant.now();
        HostState state = states.computeIfAbsent(host, ignored -> new HostState());
        synchronized (state) {
            if (down) {
                return onDown(host, at, profile, rule, state);
            }
            return onUp(host, at, profile, rule, notifyResolved, state);
        }
    }

    /** Drops per-host state (tests / host removed). */
    public void clearHost(String host) {
        if (host != null) {
            states.remove(host);
        }
    }

    public void clearAll() {
        states.clear();
    }

    private Optional<QualityAlertEvent> onDown(
            String host, Instant now, String profile, EndpointDownRuleConfig rule, HostState state) {
        state.okStreak = 0;
        state.failStreak++;
        if (state.phase == Phase.FIRING) {
            return Optional.empty();
        }
        if (state.failStreak < rule.failAfter()) {
            state.phase = Phase.PENDING;
            return Optional.empty();
        }
        state.phase = Phase.FIRING;
        if (!cooldownElapsed(state, now, rule)) {
            return Optional.empty();
        }
        state.lastFiringEmit = now;
        Map<String, Object> detail = QualityAlertEvent.detailOf(rule.failAfter(), state.failStreak, rule.clearAfter());
        return Optional.of(QualityAlertEvent.endpointDownFiring(host, profile, now, detail));
    }

    private Optional<QualityAlertEvent> onUp(
            String host,
            Instant now,
            String profile,
            EndpointDownRuleConfig rule,
            boolean notifyResolved,
            HostState state) {
        state.failStreak = 0;
        if (state.phase != Phase.FIRING) {
            state.phase = Phase.OK;
            state.okStreak = 0;
            return Optional.empty();
        }
        state.okStreak++;
        if (state.okStreak < rule.clearAfter()) {
            return Optional.empty();
        }
        state.phase = Phase.OK;
        state.okStreak = 0;
        if (!notifyResolved) {
            return Optional.empty();
        }
        Map<String, Object> detail = QualityAlertEvent.detailOf(rule.failAfter(), 0, rule.clearAfter());
        return Optional.of(QualityAlertEvent.endpointDownResolved(host, profile, now, detail));
    }

    private static boolean cooldownElapsed(HostState state, Instant now, EndpointDownRuleConfig rule) {
        if (state.lastFiringEmit == null || rule.cooldownMinutes() <= 0) {
            return true;
        }
        Duration elapsed = Duration.between(state.lastFiringEmit, now);
        return !elapsed.isNegative() && elapsed.toMinutes() >= rule.cooldownMinutes();
    }

    private static final class HostState {
        private Phase phase = Phase.OK;
        private int failStreak;
        private int okStreak;
        private Instant lastFiringEmit;
    }
}

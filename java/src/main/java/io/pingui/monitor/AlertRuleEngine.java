package io.pingui.monitor;

import io.pingui.config.EndpointDownRuleConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure quality-alert state machine for {@code endpoint_down} (P21-002 / P22-002 /
 * ADR_ALERT_RULES + ADR_HOST_PROBLEM_INDICATOR).
 *
 * <p>Thread-safe per host. Probe errors are not observed here — only boolean reachability samples.
 * Session problem stats ({@link HostProblemSummary}) survive channel suppressions and {@link #ack}.
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
     * @return FIRING or RESOLVED event when a lifecycle edge occurs (channels may still suppress RESOLVED)
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
        HostState state = states.computeIfAbsent(host, ignored -> new HostState());
        synchronized (state) {
            if (down) {
                return onDown(host, at, profile, rule, state);
            }
            return onUp(host, at, profile, rule, state);
        }
    }

    /**
     * Session problem view for a host, or empty when no FIRING occurred this session.
     *
     * @param now used to extend {@code max_duration} while still FIRING
     */
    public Optional<HostProblemSummary> problemSummary(String host, Instant now) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        HostState state = states.get(host);
        if (state == null) {
            return Optional.empty();
        }
        Instant at = now != null ? now : Instant.now();
        synchronized (state) {
            if (state.fireCount == 0) {
                return Optional.empty();
            }
            Duration maxDuration = state.maxDuration;
            if (state.incidentStartedAt != null) {
                Duration open = Duration.between(state.incidentStartedAt, at);
                if (!open.isNegative() && open.compareTo(maxDuration) > 0) {
                    maxDuration = open;
                }
            }
            return Optional.of(new HostProblemSummary(
                    host,
                    QualityAlertEvent.EVENT_ENDPOINT_DOWN,
                    state.unread,
                    state.fireCount,
                    maxDuration,
                    state.lastStartedAt,
                    state.lastResolvedAt,
                    state.lastState,
                    HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN));
        }
    }

    public Optional<HostProblemSummary> problemSummary(String host) {
        return problemSummary(host, Instant.now());
    }

    /**
     * Marks the host problem as viewed: clears {@code unread} (badge off) until the next FIRING.
     * Counters and timestamps are preserved.
     *
     * @return {@code true} when host state existed
     */
    public boolean ack(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        HostState state = states.get(host);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            state.unread = false;
            if (state.phase != Phase.FIRING) {
                state.lastState = HostProblemSummary.STATE_OK;
            }
            return true;
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
        enterFiring(state, now);
        if (!cooldownElapsed(state, now, rule)) {
            return Optional.empty();
        }
        state.lastFiringEmit = now;
        Map<String, Object> detail = QualityAlertEvent.detailOf(rule.failAfter(), state.failStreak, rule.clearAfter());
        return Optional.of(QualityAlertEvent.endpointDownFiring(host, profile, now, detail));
    }

    private Optional<QualityAlertEvent> onUp(
            String host, Instant now, String profile, EndpointDownRuleConfig rule, HostState state) {
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
        leaveFiring(state, now);
        Map<String, Object> detail = QualityAlertEvent.detailOf(rule.failAfter(), 0, rule.clearAfter());
        return Optional.of(QualityAlertEvent.endpointDownResolved(host, profile, now, detail));
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
        private boolean unread;
        private int fireCount;
        private Duration maxDuration = Duration.ZERO;
        private Instant lastStartedAt;
        private Instant lastResolvedAt;
        private Instant incidentStartedAt;
        private String lastState = HostProblemSummary.STATE_OK;
    }
}

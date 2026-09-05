package io.pingui.monitor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivery gate for quality alerts: silence and cooldown suppress channels only; lifecycle edges stay
 * in the engine / persistence (P32-006).
 */
final class QualityAlertDelivery {
    private final ConcurrentHashMap<String, QualityAlertEvent> pendingFiring = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastFiringDispatch = new ConcurrentHashMap<>();
    private volatile Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    Instant now() {
        return clock.instant();
    }

    /**
     * Decides whether to dispatch a lifecycle edge now. FIRING may be queued as pending when silence
     * or cooldown blocks delivery; RESOLVED clears pending for that rule.
     *
     * @return event to dispatch now, or empty when suppressed
     */
    java.util.Optional<QualityAlertEvent> acceptEdge(QualityAlertEvent event, boolean silenced, int cooldownMinutes) {
        Objects.requireNonNull(event, "event");
        String key = key(event.host(), event.event());
        if (QualityAlertEvent.STATE_RESOLVED.equals(event.state())) {
            pendingFiring.remove(key);
            if (silenced) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(event);
        }
        if (silenced || !cooldownElapsed(key, event.timestamp(), cooldownMinutes)) {
            pendingFiring.put(key, event);
            return java.util.Optional.empty();
        }
        pendingFiring.remove(key);
        lastFiringDispatch.put(key, event.timestamp());
        return java.util.Optional.of(event);
    }

    /**
     * Flushes a pending FIRING once when still active, not silenced, and cooldown has elapsed.
     *
     * @return event to dispatch, or empty
     */
    java.util.Optional<QualityAlertEvent> flushPending(
            String host, String ruleEvent, boolean stillFiring, boolean silenced, int cooldownMinutes) {
        if (host == null || host.isBlank() || ruleEvent == null || ruleEvent.isBlank()) {
            return java.util.Optional.empty();
        }
        String key = key(host, ruleEvent);
        QualityAlertEvent pending = pendingFiring.get(key);
        if (pending == null) {
            return java.util.Optional.empty();
        }
        if (!stillFiring) {
            pendingFiring.remove(key);
            return java.util.Optional.empty();
        }
        if (silenced) {
            return java.util.Optional.empty();
        }
        Instant at = now();
        if (!cooldownElapsed(key, at, cooldownMinutes)) {
            return java.util.Optional.empty();
        }
        pendingFiring.remove(key);
        lastFiringDispatch.put(key, at);
        return java.util.Optional.of(pending);
    }

    void clearHost(String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        pendingFiring.keySet().removeIf(k -> k.startsWith(host + '\0'));
        lastFiringDispatch.keySet().removeIf(k -> k.startsWith(host + '\0'));
    }

    void clearAll() {
        pendingFiring.clear();
        lastFiringDispatch.clear();
    }

    boolean hasPendingForTests(String host, String ruleEvent) {
        return pendingFiring.containsKey(key(host, ruleEvent));
    }

    private boolean cooldownElapsed(String key, Instant at, int cooldownMinutes) {
        if (cooldownMinutes <= 0) {
            return true;
        }
        Instant last = lastFiringDispatch.get(key);
        if (last == null) {
            return true;
        }
        Duration elapsed = Duration.between(last, at);
        return !elapsed.isNegative() && elapsed.toMinutes() >= cooldownMinutes;
    }

    private static String key(String host, String ruleEvent) {
        return host + '\0' + ruleEvent;
    }
}

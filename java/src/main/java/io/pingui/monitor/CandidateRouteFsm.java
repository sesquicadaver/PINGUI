package io.pingui.monitor;

import java.util.List;
import java.util.Objects;

/**
 * Active / candidate route FSM (P34-001).
 *
 * <p>A divergent observation becomes a candidate until the path is confirmed to the target; only
 * then is a single route-change event emitted and {@code active} swapped. Transient timeouts that
 * still match active topology are absorbed without events.
 */
public final class CandidateRouteFsm {
    private RouteIdentity active = RouteIdentity.empty();
    private RouteIdentity candidate = RouteIdentity.empty();

    /** Seeds active from a legacy reachable-IP bookmark when the FSM has no confirmed route yet. */
    public synchronized void seedActiveIfEmpty(RouteIdentity seed) {
        if (!active.isEmpty() || seed == null || seed.isEmpty()) {
            return;
        }
        active = seed;
        candidate = RouteIdentity.empty();
    }

    public synchronized RouteIdentity active() {
        return active;
    }

    public synchronized RouteIdentity candidate() {
        return candidate;
    }

    public synchronized void reset() {
        active = RouteIdentity.empty();
        candidate = RouteIdentity.empty();
    }

    /**
     * Observes a hop-indexed route sample.
     *
     * @param observed hop-indexed identity from the latest probe snapshot
     * @param targetConfirmed {@code true} when the observation includes a confirmed path to the
     *     target (TRACE target reachable / MTR {@code targetSampled} in MONITORING)
     */
    public synchronized Decision observe(RouteIdentity observed, boolean targetConfirmed) {
        Objects.requireNonNull(observed, "observed");
        if (observed.isEmpty()) {
            return Decision.unchanged(active.reachableIps());
        }

        if (!active.isEmpty() && observed.sameTopology(active)) {
            active = active.mergeKnown(observed);
            candidate = RouteIdentity.empty();
            return Decision.unchanged(active.reachableIps());
        }

        if (active.isEmpty()) {
            if (targetConfirmed) {
                active = observed;
                candidate = RouteIdentity.empty();
                return Decision.unchanged(active.reachableIps());
            }
            candidate = observed;
            return Decision.unchanged(List.of());
        }

        if (!targetConfirmed) {
            candidate = observed;
            return Decision.unchanged(active.reachableIps());
        }

        RouteIdentity confirmed = candidate.isEmpty() ? observed : candidate.mergeKnown(observed);
        if (confirmed.sameTopology(active)) {
            active = active.mergeKnown(confirmed);
            candidate = RouteIdentity.empty();
            return Decision.unchanged(active.reachableIps());
        }

        List<String> oldIps = active.reachableIps();
        List<String> newIps = confirmed.reachableIps();
        active = confirmed;
        candidate = RouteIdentity.empty();
        if (oldIps.equals(newIps)) {
            return Decision.unchanged(oldIps);
        }
        return Decision.changed(oldIps, newIps);
    }

    /** Result of one FSM observation. */
    public record Decision(boolean changed, List<String> oldIps, List<String> newIps) {
        public Decision {
            oldIps = List.copyOf(oldIps);
            newIps = List.copyOf(newIps);
        }

        static Decision unchanged(List<String> current) {
            List<String> ips = current == null ? List.of() : List.copyOf(current);
            return new Decision(false, ips, ips);
        }

        static Decision changed(List<String> oldIps, List<String> newIps) {
            return new Decision(true, oldIps, newIps);
        }
    }
}

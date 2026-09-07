package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.util.List;

/**
 * Detects confirmed route changes using hop-indexed identity + candidate FSM (P34-001).
 *
 * <p>Legacy {@link #detect(List, List)} remains for simple reachable-IP equality; TRACE/MTR should
 * prefer {@link #observe(CandidateRouteFsm, RouteSnapshot, boolean, List)}.
 */
public final class RouteChangeDetector {
    private RouteChangeDetector() {}

    /**
     * Observes a full probe snapshot through the candidate-route FSM.
     *
     * @param fsm per-host FSM (active/candidate)
     * @param snapshot latest hops (may include transient timeouts)
     * @param targetConfirmed path confirmed to target
     * @param previousIps legacy bookmark used only to seed an empty FSM
     */
    public static RouteChangeResult observe(
            CandidateRouteFsm fsm, RouteSnapshot snapshot, boolean targetConfirmed, List<String> previousIps) {
        if (fsm == null) {
            List<String> current = snapshot != null ? snapshot.routeIps() : List.of();
            return detect(previousIps == null ? List.of() : previousIps, current);
        }
        if (previousIps != null && !previousIps.isEmpty()) {
            fsm.seedActiveIfEmpty(RouteIdentity.fromReachableIps(previousIps));
        }
        RouteIdentity observed = snapshot == null || snapshot.nodes() == null
                ? RouteIdentity.empty()
                : RouteIdentity.fromHops(snapshot.nodes());
        CandidateRouteFsm.Decision decision = fsm.observe(observed, targetConfirmed);
        return new RouteChangeResult(decision.changed(), decision.oldIps(), decision.newIps());
    }

    /**
     * True when a reachable hop IP matches the authoritative {@code targetIp} (TRACE confirmation).
     *
     * <p>P35-001: no fallback to the last reachable router — incomplete paths must not confirm.
     */
    public static boolean targetReached(RouteSnapshot snapshot) {
        if (snapshot == null || snapshot.nodes() == null || snapshot.nodes().isEmpty()) {
            return false;
        }
        String targetIp = snapshot.targetIp();
        if (targetIp == null || targetIp.isBlank()) {
            return false;
        }
        for (HopNode hop : snapshot.nodes()) {
            if (hop != null && hop.isReachable() && targetIp.equals(hop.ip())) {
                return true;
            }
        }
        return false;
    }

    /** Legacy reachable-IP equality (no hop index / timeout awareness). */
    public static RouteChangeResult detect(List<String> previousIps, List<String> currentIps) {
        List<String> previous = previousIps == null ? List.of() : previousIps;
        List<String> current = currentIps == null ? List.of() : currentIps;
        if (previous.isEmpty()) {
            return new RouteChangeResult(false, List.copyOf(previous), List.copyOf(current));
        }
        if (previous.equals(current)) {
            return new RouteChangeResult(false, List.copyOf(previous), List.copyOf(current));
        }
        return new RouteChangeResult(true, List.copyOf(previous), List.copyOf(current));
    }

    public record RouteChangeResult(boolean changed, List<String> oldIps, List<String> newIps) {
        public RouteChangeResult {
            oldIps = List.copyOf(oldIps);
            newIps = List.copyOf(newIps);
        }
    }
}

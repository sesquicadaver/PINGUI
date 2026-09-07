package io.pingui.probe;

import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable per-host MTR probe cursor and partial route (P13-010 / P32-001 / P33-001 / P34-002).
 *
 * <p>{@code targetHop} is the stable monitoring span (1-based) only after a real target match.
 * Exhaustion without a match enters {@link Phase#TARGET_UNKNOWN} and may restart discovery a bounded
 * number of times ({@link #rediscoveryAttempts()}).
 */
public record MtrProbeState(
        String targetHost,
        String targetIp,
        int maxHops,
        Phase phase,
        int cursor,
        List<HopNode> nodes,
        List<String> lastCompleteRouteIps,
        int targetHop,
        int rediscoveryAttempts) {

    public enum Phase {
        DISCOVERING,
        /** Target hop identified; monitoring rotates across {@code 1..targetHop}. */
        MONITORING,
        /** maxHops exhausted without a real target match (P34-002). */
        TARGET_UNKNOWN
    }

    public MtrProbeState {
        if (maxHops < 1) {
            throw new IllegalArgumentException("maxHops must be >= 1");
        }
        if (cursor < 1) {
            throw new IllegalArgumentException("cursor must be >= 1");
        }
        if (targetHop < 0) {
            throw new IllegalArgumentException("targetHop must be >= 0");
        }
        if (rediscoveryAttempts < 0) {
            throw new IllegalArgumentException("rediscoveryAttempts must be >= 0");
        }
        nodes = List.copyOf(nodes);
        lastCompleteRouteIps = lastCompleteRouteIps == null ? List.of() : List.copyOf(lastCompleteRouteIps);
    }

    public static MtrProbeState initial(String targetHost, int maxHops) {
        return new MtrProbeState(targetHost, null, maxHops, Phase.DISCOVERING, 1, List.of(), List.of(), 0, 0);
    }

    public MtrProbeState withTargetIp(String resolvedIp) {
        return new MtrProbeState(
                targetHost,
                resolvedIp,
                maxHops,
                phase,
                cursor,
                nodes,
                lastCompleteRouteIps,
                targetHop,
                rediscoveryAttempts);
    }

    public MtrProbeState withPhase(Phase nextPhase) {
        return new MtrProbeState(
                targetHost,
                targetIp,
                maxHops,
                nextPhase,
                cursor,
                nodes,
                lastCompleteRouteIps,
                targetHop,
                rediscoveryAttempts);
    }

    public MtrProbeState withCursor(int nextCursor) {
        return new MtrProbeState(
                targetHost,
                targetIp,
                maxHops,
                phase,
                nextCursor,
                nodes,
                lastCompleteRouteIps,
                targetHop,
                rediscoveryAttempts);
    }

    public MtrProbeState withNodes(List<HopNode> nextNodes) {
        return new MtrProbeState(
                targetHost,
                targetIp,
                maxHops,
                phase,
                cursor,
                nextNodes,
                lastCompleteRouteIps,
                targetHop,
                rediscoveryAttempts);
    }

    public MtrProbeState withLastCompleteRouteIps(List<String> ips) {
        return new MtrProbeState(
                targetHost, targetIp, maxHops, phase, cursor, nodes, ips, targetHop, rediscoveryAttempts);
    }

    public MtrProbeState withTargetHop(int nextTargetHop) {
        return new MtrProbeState(
                targetHost,
                targetIp,
                maxHops,
                phase,
                cursor,
                nodes,
                lastCompleteRouteIps,
                nextTargetHop,
                rediscoveryAttempts);
    }

    public MtrProbeState withRediscoveryAttempts(int attempts) {
        return new MtrProbeState(
                targetHost, targetIp, maxHops, phase, cursor, nodes, lastCompleteRouteIps, targetHop, attempts);
    }

    /**
     * Hop count for monitoring rotation (P33-001). Prefers stable {@link #targetHop}; otherwise a
     * single-hop placeholder (never treat an unidentified router as the target span).
     */
    public int monitoringSpan() {
        if (targetHop > 0) {
            return targetHop;
        }
        return 1;
    }

    /** Reachable-prefix length (stops at first timeout). Prefer {@link #monitoringSpan()} for cursor. */
    public int monitoringHopCount() {
        int count = 0;
        for (HopNode node : nodes) {
            if (node.isReachable()) {
                count++;
            } else {
                break;
            }
        }
        return Math.max(1, count);
    }

    List<HopNode> mutableNodes() {
        return new ArrayList<>(nodes);
    }
}

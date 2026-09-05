package io.pingui.probe;

import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable per-host MTR probe cursor and partial route (P13-010 / P32-001 / P33-001).
 *
 * <p>{@code targetHop} is the stable monitoring span (1-based) once the target is discovered. Cursor
 * rotation uses {@link #monitoringSpan()}, not the reachable prefix, so an intermediate timeout
 * cannot permanently skip later hops / the target.
 */
public record MtrProbeState(
        String targetHost,
        String targetIp,
        int maxHops,
        Phase phase,
        int cursor,
        List<HopNode> nodes,
        List<String> lastCompleteRouteIps,
        int targetHop) {

    public enum Phase {
        DISCOVERING,
        MONITORING
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
        nodes = List.copyOf(nodes);
        lastCompleteRouteIps = lastCompleteRouteIps == null ? List.of() : List.copyOf(lastCompleteRouteIps);
    }

    public static MtrProbeState initial(String targetHost, int maxHops) {
        return new MtrProbeState(targetHost, null, maxHops, Phase.DISCOVERING, 1, List.of(), List.of(), 0);
    }

    public MtrProbeState withTargetIp(String resolvedIp) {
        return new MtrProbeState(
                targetHost, resolvedIp, maxHops, phase, cursor, nodes, lastCompleteRouteIps, targetHop);
    }

    public MtrProbeState withPhase(Phase nextPhase) {
        return new MtrProbeState(
                targetHost, targetIp, maxHops, nextPhase, cursor, nodes, lastCompleteRouteIps, targetHop);
    }

    public MtrProbeState withCursor(int nextCursor) {
        return new MtrProbeState(
                targetHost, targetIp, maxHops, phase, nextCursor, nodes, lastCompleteRouteIps, targetHop);
    }

    public MtrProbeState withNodes(List<HopNode> nextNodes) {
        return new MtrProbeState(
                targetHost, targetIp, maxHops, phase, cursor, nextNodes, lastCompleteRouteIps, targetHop);
    }

    public MtrProbeState withLastCompleteRouteIps(List<String> ips) {
        return new MtrProbeState(targetHost, targetIp, maxHops, phase, cursor, nodes, ips, targetHop);
    }

    public MtrProbeState withTargetHop(int nextTargetHop) {
        return new MtrProbeState(
                targetHost, targetIp, maxHops, phase, cursor, nodes, lastCompleteRouteIps, nextTargetHop);
    }

    /**
     * Hop count for monitoring rotation (P33-001). Prefers stable {@link #targetHop}; otherwise the
     * reachable prefix (legacy / pre-target discovery).
     */
    public int monitoringSpan() {
        if (targetHop > 0) {
            return targetHop;
        }
        return monitoringHopCount();
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

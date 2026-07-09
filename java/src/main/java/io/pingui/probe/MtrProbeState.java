package io.pingui.probe;

import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;

/** Immutable per-host MTR probe cursor and partial route (P13-010). */
public record MtrProbeState(
        String targetHost, String targetIp, int maxHops, Phase phase, int cursor, List<HopNode> nodes) {

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
        nodes = List.copyOf(nodes);
    }

    public static MtrProbeState initial(String targetHost, int maxHops) {
        return new MtrProbeState(targetHost, null, maxHops, Phase.DISCOVERING, 1, List.of());
    }

    public MtrProbeState withTargetIp(String resolvedIp) {
        return new MtrProbeState(targetHost, resolvedIp, maxHops, phase, cursor, nodes);
    }

    public MtrProbeState withPhase(Phase nextPhase) {
        return new MtrProbeState(targetHost, targetIp, maxHops, nextPhase, cursor, nodes);
    }

    public MtrProbeState withCursor(int nextCursor) {
        return new MtrProbeState(targetHost, targetIp, maxHops, phase, nextCursor, nodes);
    }

    public MtrProbeState withNodes(List<HopNode> nextNodes) {
        return new MtrProbeState(targetHost, targetIp, maxHops, phase, cursor, nextNodes);
    }

    /** Active hop count for monitoring rotation (reachable hops only). */
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

package io.pingui.probe;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.icmp.ProbeResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MTR-style per-hop poll state machine (P13-010).
 *
 * <p>One hop per {@link #poll} call: DISCOVERING builds the route incrementally; MONITORING
 * round-robins RTT refresh on known hops. Does not run a full trace each cycle.
 */
public final class MtrProbe {
    private final MtrHopProber hopProber;
    private final Map<String, MtrProbeState> states = new HashMap<>();

    public MtrProbe(MtrHopProber hopProber) {
        this.hopProber = hopProber;
    }

    /** Probes one hop for {@code host}. */
    public MtrPollOutcome poll(String host, int maxHops, double timeoutSeconds) {
        try {
            MtrProbeState state = states.computeIfAbsent(host, h -> MtrProbeState.initial(h, maxHops));
            StepResult step = advance(state, maxHops, timeoutSeconds);
            states.put(host, step.state());
            return MtrPollOutcome.ok(step.snapshot());
        } catch (IOException ex) {
            return MtrPollOutcome.failure(ex.getMessage());
        } catch (RuntimeException ex) {
            return MtrPollOutcome.failure(ex.getMessage());
        }
    }

    public void resetHost(String host) {
        states.remove(host);
    }

    MtrProbeState stateFor(String host) {
        return states.get(host);
    }

    private StepResult advance(MtrProbeState state, int maxHops, double timeoutSeconds) throws IOException {
        MtrProbeState working = state;
        if (working.targetIp() == null) {
            working = working.withTargetIp(hopProber.resolveTargetIp(working.targetHost()));
        }
        if (working.maxHops() != maxHops) {
            working = new MtrProbeState(
                    working.targetHost(),
                    working.targetIp(),
                    maxHops,
                    working.phase(),
                    working.cursor(),
                    working.nodes());
        }
        int hop = working.cursor();
        Optional<ProbeResult> probe = hopProber.probeHop(working.targetHost(), working.targetIp(), hop, timeoutSeconds);
        if (working.phase() == MtrProbeState.Phase.DISCOVERING) {
            return stepDiscovering(working, hop, probe);
        }
        return stepMonitoring(working, hop, probe);
    }

    private StepResult stepDiscovering(MtrProbeState state, int hop, Optional<ProbeResult> probe) {
        List<HopNode> nodes = state.mutableNodes();
        ensureNodeSlots(nodes, hop);
        if (probe.isEmpty()) {
            nodes.set(hop - 1, Models.timeout(hop));
            return finishDiscoveringStep(state, nodes, hop, false, false);
        }
        ProbeResult result = probe.get();
        HopNode node = new HopNode(hop, result.sourceIp(), result.rttMs(), false);
        nodes.set(hop - 1, node);
        if (result.target() || result.sourceIp().equals(state.targetIp())) {
            List<HopNode> trimmed = trimTrailingEmpty(nodes);
            MtrProbeState next = state.withNodes(trimmed)
                    .withPhase(MtrProbeState.Phase.MONITORING)
                    .withCursor(1);
            return toStepResult(next);
        }
        return finishDiscoveringStep(state, nodes, hop, false, false);
    }

    private StepResult finishDiscoveringStep(
            MtrProbeState state, List<HopNode> nodes, int hop, boolean forceMonitoring, boolean trimNodes) {
        List<HopNode> route = trimNodes ? trimTrailingEmpty(nodes) : List.copyOf(nodes);
        int nextHop = hop + 1;
        if (!forceMonitoring && nextHop <= state.maxHops()) {
            MtrProbeState next = state.withNodes(route).withCursor(nextHop);
            return toStepResult(next);
        }
        MtrProbeState next = state.withNodes(trimTrailingEmpty(nodes))
                .withPhase(MtrProbeState.Phase.MONITORING)
                .withCursor(1);
        return toStepResult(next);
    }

    private StepResult stepMonitoring(MtrProbeState state, int hop, Optional<ProbeResult> probe) {
        List<HopNode> nodes = state.mutableNodes();
        if (nodes.isEmpty() || hop > nodes.size()) {
            MtrProbeState rediscover =
                    state.withPhase(MtrProbeState.Phase.DISCOVERING).withCursor(1);
            return toStepResult(rediscover);
        }
        HopNode previous = nodes.get(hop - 1);
        if (probe.isEmpty()) {
            nodes.set(hop - 1, Models.timeout(hop));
            MtrProbeState next = state.withNodes(nodes).withCursor(nextMonitoringCursor(state, hop));
            return toStepResult(next);
        }
        ProbeResult result = probe.get();
        if (previous.isReachable() && !previous.ip().equals(result.sourceIp())) {
            List<HopNode> truncated = new ArrayList<>(nodes.subList(0, hop - 1));
            truncated.add(new HopNode(hop, result.sourceIp(), result.rttMs(), false));
            MtrProbeState next = state.withNodes(truncated)
                    .withPhase(MtrProbeState.Phase.DISCOVERING)
                    .withCursor(hop + 1);
            return toStepResult(next);
        }
        nodes.set(hop - 1, new HopNode(hop, result.sourceIp(), result.rttMs(), false));
        MtrProbeState next = state.withNodes(nodes).withCursor(nextMonitoringCursor(state, hop));
        return toStepResult(next);
    }

    private static int nextMonitoringCursor(MtrProbeState state, int probedHop) {
        int hopCount = state.monitoringHopCount();
        if (hopCount <= 1) {
            return 1;
        }
        int next = probedHop + 1;
        return next > hopCount ? 1 : next;
    }

    private static void ensureNodeSlots(List<HopNode> nodes, int hop) {
        while (nodes.size() < hop) {
            nodes.add(Models.timeout(nodes.size() + 1));
        }
    }

    private static List<HopNode> trimTrailingEmpty(List<HopNode> nodes) {
        int end = nodes.size();
        while (end > 0 && !nodes.get(end - 1).isReachable()) {
            end--;
        }
        if (end == nodes.size()) {
            return List.copyOf(nodes);
        }
        return List.copyOf(nodes.subList(0, end));
    }

    private static StepResult toStepResult(MtrProbeState state) {
        RouteSnapshot snapshot = new RouteSnapshot(state.targetHost(), state.targetIp(), state.nodes());
        return new StepResult(state, snapshot);
    }

    private record StepResult(MtrProbeState state, RouteSnapshot snapshot) {}
}

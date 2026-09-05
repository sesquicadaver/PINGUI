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
 * MTR-style per-hop poll state machine (P13-010 / P32-001).
 *
 * <p>One hop per {@link #poll} call: DISCOVERING builds the route incrementally; MONITORING
 * round-robins RTT refresh on known hops. Consumers must treat only {@link
 * MtrPollOutcome#freshHopSample()} as a new measurement.
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
            return step.outcome();
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
                    working.nodes(),
                    working.lastCompleteRouteIps());
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
            HopNode fresh = Models.timeout(hop);
            nodes.set(hop - 1, fresh);
            return finishDiscoveringStep(state, nodes, hop, fresh, false, MtrTargetOutcome.NOT_SAMPLED);
        }
        ProbeResult result = probe.get();
        HopNode node = new HopNode(hop, result.sourceIp(), result.rttMs(), false);
        nodes.set(hop - 1, node);
        boolean isTarget = result.target() || result.sourceIp().equals(state.targetIp());
        if (isTarget) {
            List<HopNode> trimmed = trimTrailingEmpty(nodes);
            List<String> completeIps = routeIps(trimmed);
            MtrProbeState next = state.withNodes(trimmed)
                    .withPhase(MtrProbeState.Phase.MONITORING)
                    .withCursor(1)
                    .withLastCompleteRouteIps(completeIps);
            return toStepResult(next, hop, node, true, MtrTargetOutcome.REACHABLE);
        }
        return finishDiscoveringStep(state, nodes, hop, node, false, MtrTargetOutcome.NOT_SAMPLED);
    }

    private StepResult finishDiscoveringStep(
            MtrProbeState state,
            List<HopNode> nodes,
            int hop,
            HopNode fresh,
            boolean forceMonitoring,
            MtrTargetOutcome targetOutcome) {
        List<HopNode> route = List.copyOf(nodes);
        int nextHop = hop + 1;
        if (!forceMonitoring && nextHop <= state.maxHops()) {
            MtrProbeState next = state.withNodes(route).withCursor(nextHop);
            return toStepResult(next, hop, fresh, false, targetOutcome);
        }
        // Max hops without seeing target: enter MONITORING on incomplete path; no complete baseline.
        List<HopNode> trimmed = trimTrailingEmpty(nodes);
        MtrProbeState next = state.withNodes(trimmed)
                .withPhase(MtrProbeState.Phase.MONITORING)
                .withCursor(1);
        boolean targetSampled = targetOutcome != MtrTargetOutcome.NOT_SAMPLED;
        return toStepResult(next, hop, fresh, targetSampled, targetOutcome);
    }

    private StepResult stepMonitoring(MtrProbeState state, int hop, Optional<ProbeResult> probe) {
        List<HopNode> nodes = state.mutableNodes();
        if (nodes.isEmpty() || hop > nodes.size()) {
            MtrProbeState rediscover =
                    state.withPhase(MtrProbeState.Phase.DISCOVERING).withCursor(1);
            return toStepResult(rediscover, hop, null, false, MtrTargetOutcome.NOT_SAMPLED);
        }
        HopNode previous = nodes.get(hop - 1);
        boolean probingTarget = isTargetHop(state, previous);
        if (probe.isEmpty()) {
            HopNode fresh = Models.timeout(hop);
            nodes.set(hop - 1, fresh);
            // Timeout is not a topology change — keep lastCompleteRouteIps unchanged.
            MtrProbeState next = state.withNodes(nodes).withCursor(nextMonitoringCursor(state, hop));
            MtrTargetOutcome outcome = probingTarget ? MtrTargetOutcome.UNREACHABLE : MtrTargetOutcome.NOT_SAMPLED;
            return toStepResult(next, hop, fresh, probingTarget, outcome);
        }
        ProbeResult result = probe.get();
        if (previous.isReachable() && !previous.ip().equals(result.sourceIp())) {
            List<HopNode> truncated = new ArrayList<>(nodes.subList(0, hop - 1));
            HopNode fresh = new HopNode(hop, result.sourceIp(), result.rttMs(), false);
            truncated.add(fresh);
            boolean isTarget = result.target() || result.sourceIp().equals(state.targetIp());
            MtrProbeState next = state.withNodes(truncated)
                    .withPhase(MtrProbeState.Phase.DISCOVERING)
                    .withCursor(hop + 1);
            // Topology broke relative to last complete route; discovery resumes.
            return toStepResult(
                    next, hop, fresh, isTarget, isTarget ? MtrTargetOutcome.REACHABLE : MtrTargetOutcome.NOT_SAMPLED);
        }
        HopNode fresh = new HopNode(hop, result.sourceIp(), result.rttMs(), false);
        nodes.set(hop - 1, fresh);
        boolean isTarget = result.target() || result.sourceIp().equals(state.targetIp()) || probingTarget;
        List<String> completeIps = state.lastCompleteRouteIps();
        if (isTarget || routeIps(nodes).equals(state.lastCompleteRouteIps())) {
            completeIps = routeIps(nodes);
        }
        MtrProbeState next = state.withNodes(nodes)
                .withCursor(nextMonitoringCursor(state, hop))
                .withLastCompleteRouteIps(completeIps);
        return toStepResult(
                next, hop, fresh, isTarget, isTarget ? MtrTargetOutcome.REACHABLE : MtrTargetOutcome.NOT_SAMPLED);
    }

    private static boolean isTargetHop(MtrProbeState state, HopNode hop) {
        if (hop == null || !hop.isReachable() || hop.ip() == null) {
            // Last hop slot may be the destination even when previously timed out.
            return false;
        }
        return hop.ip().equals(state.targetIp());
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

    private static List<String> routeIps(List<HopNode> nodes) {
        List<String> ips = new ArrayList<>();
        for (HopNode node : nodes) {
            if (node.isReachable() && node.ip() != null && !node.ip().isBlank()) {
                ips.add(node.ip());
            }
        }
        return List.copyOf(ips);
    }

    private static StepResult toStepResult(
            MtrProbeState state, int probedHop, HopNode fresh, boolean targetSampled, MtrTargetOutcome targetOutcome) {
        RouteSnapshot snapshot = new RouteSnapshot(state.targetHost(), state.targetIp(), state.nodes());
        MtrPollOutcome outcome = MtrPollOutcome.ok(
                snapshot, state.phase(), probedHop, fresh, targetSampled, targetOutcome, state.lastCompleteRouteIps());
        return new StepResult(state, outcome);
    }

    private record StepResult(MtrProbeState state, MtrPollOutcome outcome) {}
}

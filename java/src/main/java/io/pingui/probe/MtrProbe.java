package io.pingui.probe;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.icmp.ProbeResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MTR-style per-hop poll state machine (P13-010 / P32-001 / P32-002 / P33-001).
 *
 * <p>One hop per {@link #poll} call. Per-host state lives in a {@link ConcurrentHashMap}; a
 * generation token drops in-flight writes after {@link #resetHost(String)} / rename so a finished
 * poll cannot resurrect cleared state. Monitoring rotates across a stable {@code targetHop} span so
 * intermediate timeouts cannot permanently skip the target (P33-001).
 */
public final class MtrProbe {
    private final MtrHopProber hopProber;
    private final ConcurrentHashMap<String, HostSlot> states = new ConcurrentHashMap<>();

    public MtrProbe(MtrHopProber hopProber) {
        this.hopProber = hopProber;
    }

    /** Probes one hop for {@code host}. */
    public MtrPollOutcome poll(String host, int maxHops, double timeoutSeconds) {
        try {
            HostSlot slot = states.computeIfAbsent(host, ignored -> new HostSlot());
            long generation;
            MtrProbeState start;
            synchronized (slot) {
                generation = slot.generation;
                if (slot.state == null) {
                    slot.state = MtrProbeState.initial(host, maxHops);
                }
                start = slot.state;
            }
            StepResult step = advance(start, maxHops, timeoutSeconds);
            synchronized (slot) {
                if (slot.generation == generation) {
                    slot.state = step.state();
                }
            }
            return step.outcome();
        } catch (IOException ex) {
            return MtrPollOutcome.failure(ex.getMessage());
        } catch (RuntimeException ex) {
            return MtrPollOutcome.failure(ex.getMessage());
        }
    }

    /** Drops state and bumps generation so an in-flight poll cannot write back (P32-002). */
    public void resetHost(String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        HostSlot removed = states.remove(host);
        if (removed != null) {
            synchronized (removed) {
                removed.generation++;
                removed.state = null;
            }
        }
    }

    /**
     * Clears MTR state for a renamed host. The new name starts discovery from scratch (P32-002).
     */
    public void renameHost(String oldHost, String newHost) {
        resetHost(oldHost);
        if (newHost != null && !newHost.isBlank() && !newHost.equals(oldHost)) {
            resetHost(newHost);
        }
    }

    MtrProbeState stateFor(String host) {
        HostSlot slot = states.get(host);
        if (slot == null) {
            return null;
        }
        synchronized (slot) {
            return slot.state;
        }
    }

    /** Test hook: generation for {@code host}, or empty when absent. */
    Optional<Long> generationFor(String host) {
        HostSlot slot = states.get(host);
        if (slot == null) {
            return Optional.empty();
        }
        synchronized (slot) {
            return Optional.of(slot.generation);
        }
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
                    working.lastCompleteRouteIps(),
                    working.targetHop());
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
            int discoveredTargetHop = trimmed.isEmpty() ? hop : trimmed.size();
            MtrProbeState next = state.withNodes(trimmed)
                    .withPhase(MtrProbeState.Phase.MONITORING)
                    .withCursor(1)
                    .withTargetHop(discoveredTargetHop)
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
        List<HopNode> trimmed = trimTrailingEmpty(nodes);
        int inferredTarget = state.targetHop() > 0 ? state.targetHop() : trimmed.size();
        MtrProbeState next = state.withNodes(trimmed)
                .withPhase(MtrProbeState.Phase.MONITORING)
                .withCursor(1)
                .withTargetHop(inferredTarget > 0 ? inferredTarget : state.targetHop());
        boolean targetSampled = targetOutcome != MtrTargetOutcome.NOT_SAMPLED;
        return toStepResult(next, hop, fresh, targetSampled, targetOutcome);
    }

    private StepResult stepMonitoring(MtrProbeState state, int hop, Optional<ProbeResult> probe) {
        List<HopNode> nodes = state.mutableNodes();
        ensureNodeSlots(nodes, Math.max(hop, state.monitoringSpan()));
        if (nodes.isEmpty() || hop > nodes.size()) {
            MtrProbeState rediscover = state.withPhase(MtrProbeState.Phase.DISCOVERING)
                    .withCursor(1)
                    .withTargetHop(0);
            return toStepResult(rediscover, hop, null, false, MtrTargetOutcome.NOT_SAMPLED);
        }
        HopNode previous = nodes.get(hop - 1);
        boolean probingTarget = isTargetSlot(state, hop, previous);
        if (probe.isEmpty()) {
            HopNode fresh = Models.timeout(hop);
            nodes.set(hop - 1, fresh);
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
                    .withCursor(hop + 1)
                    .withTargetHop(0);
            return toStepResult(
                    next, hop, fresh, isTarget, isTarget ? MtrTargetOutcome.REACHABLE : MtrTargetOutcome.NOT_SAMPLED);
        }
        HopNode fresh = new HopNode(hop, result.sourceIp(), result.rttMs(), false);
        nodes.set(hop - 1, fresh);
        boolean isTarget = result.target() || result.sourceIp().equals(state.targetIp()) || probingTarget;
        List<String> completeIps = state.lastCompleteRouteIps();
        int nextTargetHop = state.targetHop();
        if (isTarget) {
            nextTargetHop = hop;
            completeIps = routeIps(nodes);
        } else if (routeIps(nodes).equals(state.lastCompleteRouteIps())) {
            completeIps = routeIps(nodes);
        }
        MtrProbeState next = state.withNodes(nodes)
                .withCursor(nextMonitoringCursor(state, hop))
                .withTargetHop(nextTargetHop)
                .withLastCompleteRouteIps(completeIps);
        return toStepResult(
                next, hop, fresh, isTarget, isTarget ? MtrTargetOutcome.REACHABLE : MtrTargetOutcome.NOT_SAMPLED);
    }

    /** True when this hop index is the stable target slot (P33-001), else IP match fallback. */
    private static boolean isTargetSlot(MtrProbeState state, int hop, HopNode node) {
        if (state.targetHop() > 0) {
            return hop == state.targetHop();
        }
        return isTargetHopByIp(state, node);
    }

    private static boolean isTargetHopByIp(MtrProbeState state, HopNode hop) {
        if (hop == null || !hop.isReachable() || hop.ip() == null) {
            return false;
        }
        return hop.ip().equals(state.targetIp());
    }

    private static int nextMonitoringCursor(MtrProbeState state, int probedHop) {
        int hopCount = state.monitoringSpan();
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

    /** Per-host mutable slot: generation invalidates in-flight commits after reset. */
    private static final class HostSlot {
        private long generation;
        private MtrProbeState state;
    }
}

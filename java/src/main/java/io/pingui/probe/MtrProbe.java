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
 * MTR-style per-hop poll state machine (P13-010 / P32-001 / P32-002 / P33-001 / P34-002).
 *
 * <p>One hop per {@link #poll} call. Per-host state lives in a {@link ConcurrentHashMap}; a
 * generation token drops in-flight writes after {@link #resetHost(String)} / rename so a finished
 * poll cannot resurrect cleared state. {@code targetHop} is set only after a real target match;
 * maxHops exhaustion enters {@link MtrProbeState.Phase#TARGET_UNKNOWN} with burst rediscovery and
 * capped exponential poll-backoff (P35-002).
 */
public final class MtrProbe {
    /** Burst of full discovery passes after exhaustion without identifying the target (P34-002). */
    static final int MAX_TARGET_REDISCOVERIES = 5;

    /** Initial idle polls after a failed rediscovery burst (P35-002). */
    static final int REDISCOVERY_BACKOFF_INITIAL_POLLS = 4;

    /** Cap for exponential poll-backoff between rediscovery bursts (P35-002). */
    static final int REDISCOVERY_BACKOFF_MAX_POLLS = 64;

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
                    working.targetHop(),
                    working.rediscoveryAttempts(),
                    working.rediscoveryBackoffRemaining(),
                    working.rediscoveryBackoffStep());
        }
        if (working.phase() == MtrProbeState.Phase.TARGET_UNKNOWN) {
            return stepTargetUnknown(working, maxHops, timeoutSeconds);
        }
        int hop = working.cursor();
        Optional<ProbeResult> probe = hopProber.probeHop(working.targetHost(), working.targetIp(), hop, timeoutSeconds);
        if (working.phase() == MtrProbeState.Phase.DISCOVERING) {
            return stepDiscovering(working, hop, probe);
        }
        return stepMonitoring(working, hop, probe);
    }

    /**
     * After exhaustion without a target match: restart discovery in bursts of {@link
     * #MAX_TARGET_REDISCOVERIES}, then wait with capped exponential poll-backoff before the next
     * burst (P35-002). Never permanently stops probing.
     */
    private StepResult stepTargetUnknown(MtrProbeState state, int maxHops, double timeoutSeconds) throws IOException {
        if (state.rediscoveryBackoffRemaining() > 0) {
            return toStepResult(
                    state.withRediscoveryBackoffRemaining(state.rediscoveryBackoffRemaining() - 1),
                    0,
                    null,
                    false,
                    MtrTargetOutcome.NOT_SAMPLED);
        }
        int nextAttempt = state.rediscoveryAttempts() + 1;
        if (nextAttempt > MAX_TARGET_REDISCOVERIES) {
            int nextStep = nextBackoffStep(state.rediscoveryBackoffStep());
            int remainingAfterThisPoll = Math.max(0, nextStep - 1);
            MtrProbeState cooling = state.withRediscoveryAttempts(0)
                    .withRediscoveryBackoffStep(nextStep)
                    .withRediscoveryBackoffRemaining(remainingAfterThisPoll);
            return toStepResult(cooling, 0, null, false, MtrTargetOutcome.NOT_SAMPLED);
        }
        MtrProbeState rediscover = state.withPhase(MtrProbeState.Phase.DISCOVERING)
                .withCursor(1)
                .withNodes(List.of())
                .withTargetHop(0)
                .withRediscoveryAttempts(nextAttempt);
        int hop = 1;
        Optional<ProbeResult> probe =
                hopProber.probeHop(rediscover.targetHost(), rediscover.targetIp(), hop, timeoutSeconds);
        return stepDiscovering(rediscover, hop, probe);
    }

    static int nextBackoffStep(int previousStep) {
        if (previousStep <= 0) {
            return REDISCOVERY_BACKOFF_INITIAL_POLLS;
        }
        long doubled = (long) previousStep * 2L;
        return (int) Math.min(REDISCOVERY_BACKOFF_MAX_POLLS, doubled);
    }

    private StepResult stepDiscovering(MtrProbeState state, int hop, Optional<ProbeResult> probe) {
        List<HopNode> nodes = state.mutableNodes();
        ensureNodeSlots(nodes, hop);
        if (probe.isEmpty()) {
            HopNode fresh = Models.timeout(hop);
            nodes.set(hop - 1, fresh);
            return finishDiscoveringStep(state, nodes, hop, fresh);
        }
        ProbeResult result = probe.get();
        HopNode node = new HopNode(hop, result.sourceIp(), result.rttMs(), false);
        nodes.set(hop - 1, node);
        boolean isTarget = result.target() || result.sourceIp().equals(state.targetIp());
        if (isTarget) {
            return enterMonitoringWithTarget(state, nodes, hop, node);
        }
        return finishDiscoveringStep(state, nodes, hop, node);
    }

    private static StepResult enterMonitoringWithTarget(
            MtrProbeState state, List<HopNode> nodes, int hop, HopNode fresh) {
        List<HopNode> trimmed = trimTrailingEmpty(nodes);
        List<String> completeIps = routeIps(trimmed);
        int discoveredTargetHop = trimmed.isEmpty() ? hop : trimmed.size();
        MtrProbeState next = state.withNodes(trimmed)
                .withPhase(MtrProbeState.Phase.MONITORING)
                .withCursor(1)
                .withTargetHop(discoveredTargetHop)
                .withLastCompleteRouteIps(completeIps)
                .withRediscoveryAttempts(0)
                .withRediscoveryBackoffRemaining(0)
                .withRediscoveryBackoffStep(0);
        return toStepResult(next, hop, fresh, true, MtrTargetOutcome.REACHABLE);
    }

    private StepResult finishDiscoveringStep(MtrProbeState state, List<HopNode> nodes, int hop, HopNode fresh) {
        List<HopNode> route = List.copyOf(nodes);
        int nextHop = hop + 1;
        if (nextHop <= state.maxHops()) {
            MtrProbeState next = state.withNodes(route).withCursor(nextHop);
            return toStepResult(next, hop, fresh, false, MtrTargetOutcome.NOT_SAMPLED);
        }
        // maxHops exhausted without a real target match — never infer targetHop (P34-002).
        List<HopNode> trimmed = trimTrailingEmpty(nodes);
        List<HopNode> kept = trimmed.isEmpty() ? route : trimmed;
        MtrProbeState next = state.withNodes(kept)
                .withPhase(MtrProbeState.Phase.TARGET_UNKNOWN)
                .withCursor(1)
                .withTargetHop(0);
        return toStepResult(next, hop, fresh, false, MtrTargetOutcome.NOT_SAMPLED);
    }

    private StepResult stepMonitoring(MtrProbeState state, int hop, Optional<ProbeResult> probe) {
        if (state.targetHop() < 1) {
            // Safety: monitoring without an identified target is invalid — rediscover.
            MtrProbeState unknown = state.withPhase(MtrProbeState.Phase.TARGET_UNKNOWN)
                    .withCursor(1)
                    .withTargetHop(0);
            return toStepResult(unknown, hop, null, false, MtrTargetOutcome.NOT_SAMPLED);
        }
        List<HopNode> nodes = state.mutableNodes();
        ensureNodeSlots(nodes, Math.max(hop, state.monitoringSpan()));
        if (nodes.isEmpty() || hop > nodes.size()) {
            MtrProbeState rediscover = state.withPhase(MtrProbeState.Phase.DISCOVERING)
                    .withCursor(1)
                    .withTargetHop(0);
            return toStepResult(rediscover, hop, null, false, MtrTargetOutcome.NOT_SAMPLED);
        }
        HopNode previous = nodes.get(hop - 1);
        boolean probingTarget = hop == state.targetHop();
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
            if (isTarget) {
                return enterMonitoringWithTarget(state, truncated, hop, fresh);
            }
            MtrProbeState next = state.withNodes(truncated)
                    .withPhase(MtrProbeState.Phase.DISCOVERING)
                    .withCursor(hop + 1)
                    .withTargetHop(0);
            return toStepResult(next, hop, fresh, false, MtrTargetOutcome.NOT_SAMPLED);
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
        // Never report target_sampled until the target hop is identified (P34-002).
        boolean sampled = targetSampled && state.targetHop() > 0;
        MtrTargetOutcome outcome = sampled ? targetOutcome : MtrTargetOutcome.NOT_SAMPLED;
        RouteSnapshot snapshot = new RouteSnapshot(state.targetHost(), state.targetIp(), state.nodes());
        MtrPollOutcome pollOutcome = MtrPollOutcome.ok(
                snapshot, state.phase(), probedHop, fresh, sampled, outcome, state.lastCompleteRouteIps());
        return new StepResult(state, pollOutcome);
    }

    private record StepResult(MtrProbeState state, MtrPollOutcome outcome) {}

    /** Per-host mutable slot: generation invalidates in-flight commits after reset. */
    private static final class HostSlot {
        private long generation;
        private MtrProbeState state;
    }
}

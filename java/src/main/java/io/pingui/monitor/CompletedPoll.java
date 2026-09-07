package io.pingui.monitor;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProbeOutcome;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable probe-derived poll aggregate (P34-005 / P35-004).
 *
 * <p>Built on the probe thread <em>before</em> GUI/daemon {@link SessionStore} projection so {@code
 * poll_result} never depends on mutable FX/store state.
 */
public record CompletedPoll(
        String host,
        HostProbeMode probeMode,
        RouteSnapshot snapshot,
        double durationMs,
        String error,
        ProbeOutcome probeOutcome,
        boolean targetSampled,
        Instant observedAt,
        HopStatsSummary measuredTerminalStats,
        Map<Integer, String> lastKnownHopIps) {

    public CompletedPoll {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(probeMode, "probeMode");
        Objects.requireNonNull(observedAt, "observedAt");
        probeOutcome = probeOutcome != null ? probeOutcome : ProbeOutcome.NETWORK_ERROR;
        lastKnownHopIps = lastKnownHopIps == null || lastKnownHopIps.isEmpty() ? Map.of() : Map.copyOf(lastKnownHopIps);
    }

    /** Monitor/DNS/internal failure — never counted as sampled downtime. */
    public static CompletedPoll failure(
            String host,
            HostProbeMode probeMode,
            double durationMs,
            String error,
            ProbeOutcome probeOutcome,
            Instant observedAt) {
        return new CompletedPoll(
                host,
                probeMode,
                null,
                durationMs,
                error,
                probeOutcome != null ? probeOutcome : ProbeOutcome.NETWORK_ERROR,
                false,
                observedAt,
                null,
                Map.of());
    }

    /**
     * Successful probe path. {@code priorTerminalStats} is a <em>copy</em> of hop stats before this
     * poll's sample is applied to the store; projected loss/jitter include the current sample when
     * the terminal hop is fresh under {@code sampleScope}.
     */
    public static CompletedPoll success(
            String host,
            HostProbeMode probeMode,
            RouteSnapshot snapshot,
            double durationMs,
            ProbeOutcome probeOutcome,
            boolean targetSampled,
            Instant observedAt,
            HopProbeStats priorTerminalStats,
            PollSampleScope sampleScope,
            Map<Integer, String> lastKnownHopIps) {
        return success(
                host,
                probeMode,
                snapshot,
                durationMs,
                probeOutcome,
                targetSampled,
                observedAt,
                priorTerminalStats,
                sampleScope,
                lastKnownHopIps,
                null);
    }

    /**
     * Like {@link #success(String, HostProbeMode, RouteSnapshot, double, ProbeOutcome, boolean,
     * Instant, HopProbeStats, PollSampleScope, Map)} with an explicit session {@code knownTargetHop}
     * (P35-004).
     */
    public static CompletedPoll success(
            String host,
            HostProbeMode probeMode,
            RouteSnapshot snapshot,
            double durationMs,
            ProbeOutcome probeOutcome,
            boolean targetSampled,
            Instant observedAt,
            HopProbeStats priorTerminalStats,
            PollSampleScope sampleScope,
            Map<Integer, String> lastKnownHopIps,
            Integer knownTargetHop) {
        HopStatsSummary measured = null;
        if (targetSampled && snapshot != null) {
            Integer resolvedHop = resolveKnownTargetHop(snapshot, sampleScope, lastKnownHopIps, knownTargetHop);
            measured = HopStats.projectTerminalAfterSample(priorTerminalStats, snapshot, sampleScope, resolvedHop);
        }
        return new CompletedPoll(
                host,
                probeMode,
                snapshot,
                durationMs,
                null,
                probeOutcome != null ? probeOutcome : ProbeOutcome.SUCCESS,
                targetSampled,
                observedAt,
                measured,
                lastKnownHopIps != null ? lastKnownHopIps : Map.of());
    }

    /**
     * Target hop for poll_result / measured stats (P35-004).
     *
     * <p>Prefers a reachable {@code targetIp} match; on timeout attributes by known {@code
     * targetHop} / {@code freshHop}, never by matching IP {@code *}.
     */
    public static HopNode terminalHop(RouteSnapshot snapshot) {
        return terminalHop(snapshot, null, null);
    }

    /**
     * @param knownTargetHop 1-based hop index from session / MTR when the target IP is not among
     *     reachable hops (timeout)
     * @param scope poll freshness; when {@code targetSampled}, {@code freshHop} is the target sample
     */
    public static HopNode terminalHop(RouteSnapshot snapshot, Integer knownTargetHop, PollSampleScope scope) {
        if (snapshot == null || snapshot.nodes() == null || snapshot.nodes().isEmpty()) {
            return null;
        }
        String targetIp = snapshot.targetIp();
        if (targetIp != null && !targetIp.isBlank()) {
            for (HopNode hop : snapshot.nodes()) {
                if (hop != null && hop.isReachable() && targetIp.equals(hop.ip())) {
                    return hop;
                }
            }
            Integer indexed = resolveKnownTargetHop(snapshot, scope, Map.of(), knownTargetHop);
            HopNode byIndex = hopAt(snapshot, indexed);
            if (byIndex != null) {
                return byIndex;
            }
            // Target known but neither reachable nor indexed — do not guess via last hop.
            return null;
        }
        for (int i = snapshot.nodes().size() - 1; i >= 0; i--) {
            HopNode hop = snapshot.nodes().get(i);
            if (hop != null && hop.isReachable()) {
                return hop;
            }
        }
        return snapshot.nodes().get(snapshot.nodes().size() - 1);
    }

    /**
     * Resolves the 1-based target hop index for timeout attribution (P35-004).
     *
     * <p>Order: sampled {@code freshHop} → explicit session hop → last-known IP map match.
     */
    public static Integer resolveKnownTargetHop(
            RouteSnapshot snapshot,
            PollSampleScope scope,
            Map<Integer, String> lastKnownHopIps,
            Integer knownTargetHop) {
        if (scope != null && scope.targetSampled() && scope.hasFreshHopSample()) {
            return scope.freshHop();
        }
        if (knownTargetHop != null && knownTargetHop >= 1) {
            return knownTargetHop;
        }
        if (snapshot == null) {
            return null;
        }
        String targetIp = snapshot.targetIp();
        if (targetIp == null || targetIp.isBlank() || lastKnownHopIps == null || lastKnownHopIps.isEmpty()) {
            return null;
        }
        for (Map.Entry<Integer, String> entry : lastKnownHopIps.entrySet()) {
            if (entry.getKey() != null && entry.getKey() >= 1 && targetIp.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    static HopNode hopAt(RouteSnapshot snapshot, Integer hop) {
        if (snapshot == null || hop == null || hop < 1 || snapshot.nodes() == null) {
            return null;
        }
        for (HopNode node : snapshot.nodes()) {
            if (node != null && node.hop() == hop) {
                return node;
            }
        }
        if (hop <= snapshot.nodes().size()) {
            return snapshot.nodes().get(hop - 1);
        }
        return null;
    }

    /** Defensive copy helper for callers that hold a live last-known map. */
    public static Map<Integer, String> copyLastKnown(Map<Integer, String> known) {
        if (known == null || known.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new HashMap<>(known));
    }
}

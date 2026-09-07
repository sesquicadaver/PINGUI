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
 * Immutable probe-derived poll aggregate (P34-005).
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
        HopStatsSummary measured = null;
        if (targetSampled && snapshot != null) {
            measured = HopStats.projectTerminalAfterSample(priorTerminalStats, snapshot, sampleScope);
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

    /** Target hop for poll_result / measured stats (target IP match, else last reachable). */
    public static HopNode terminalHop(RouteSnapshot snapshot) {
        if (snapshot == null || snapshot.nodes().isEmpty()) {
            return null;
        }
        String targetIp = snapshot.targetIp();
        if (targetIp != null && !targetIp.isBlank()) {
            for (HopNode node : snapshot.nodes()) {
                if (node.isReachable() && targetIp.equals(node.ip())) {
                    return node;
                }
            }
            // Target known but unreachable — still attribute stats to matching hop index if present.
            for (HopNode node : snapshot.nodes()) {
                if (targetIp.equals(node.ip())) {
                    return node;
                }
            }
            return null;
        }
        for (int i = snapshot.nodes().size() - 1; i >= 0; i--) {
            HopNode node = snapshot.nodes().get(i);
            if (node.isReachable()) {
                return node;
            }
        }
        return snapshot.nodes().get(snapshot.nodes().size() - 1);
    }

    /** Defensive copy helper for callers that hold a live last-known map. */
    public static Map<Integer, String> copyLastKnown(Map<Integer, String> known) {
        if (known == null || known.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new HashMap<>(known));
    }
}

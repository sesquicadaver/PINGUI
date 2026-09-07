package io.pingui.monitor;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HopStatsSummary;
import io.pingui.model.Models.RouteSnapshot;

/**
 * Per-hop jitter and packet loss calculations (parity with Python hop_stats.py).
 *
 * <p>P34-006 / P35-003: {@code poll_result} loss uses a sliding attempt window (size {@link
 * Models#MAX_HOP_RTT_SAMPLES}), not session-lifetime counters. Single-packet loss is {@code null};
 * jitter is population stddev over the RTT window and {@code null} with fewer than {@link
 * #MIN_JITTER_RTT_SAMPLES} samples.
 */
public final class HopStats {
    /**
     * Minimum sliding-window attempts before {@code poll_result.loss_percent} is measured (P34-006 /
     * P35-003). One ICMP echo cannot define a loss rate.
     */
    public static final int MIN_LOSS_WINDOW_PROBES = 2;

    /** Sliding loss window length — aligned with the hop RTT sample cap (P35-003). */
    public static final int LOSS_WINDOW_SIZE = Models.MAX_HOP_RTT_SAMPLES;

    /** Minimum successful RTT samples for jitter (population stddev over the hop RTT window). */
    public static final int MIN_JITTER_RTT_SAMPLES = 2;

    private HopStats() {}

    public static void recordProbe(HopProbeStats stats, HopNode node) {
        stats.recordProbeAttempt();
        if (!node.isReachable() || node.pingMs() == null) {
            return;
        }
        stats.recordProbeSuccess(node.pingMs());
        if (stats.getRttSamples().size() > Models.MAX_HOP_RTT_SAMPLES) {
            stats.getRttSamples()
                    .subList(0, stats.getRttSamples().size() - Models.MAX_HOP_RTT_SAMPLES)
                    .clear();
        }
    }

    /**
     * UI / session loss over the sliding attempt window (0 when empty). Lifetime {@code probes} /
     * {@code successes} remain for persistence compatibility only (P35-003).
     */
    public static double lossPct(HopProbeStats stats) {
        int windowProbes = stats.getWindowProbes();
        if (windowProbes == 0) {
            return 0.0;
        }
        int failures = windowProbes - stats.getWindowSuccesses();
        return failures * 100.0 / windowProbes;
    }

    /**
     * Loss for {@code poll_result} / rollup: {@code null} until {@link #MIN_LOSS_WINDOW_PROBES}
     * attempts exist in the sliding window (P34-006 / P35-003).
     */
    public static Double lossPctInWindow(HopProbeStats stats) {
        if (stats == null || stats.getWindowProbes() < MIN_LOSS_WINDOW_PROBES) {
            return null;
        }
        return lossPct(stats);
    }

    /**
     * Population standard deviation of RTT samples in the hop window; {@code null} when fewer than
     * {@link #MIN_JITTER_RTT_SAMPLES} RTTs (P34-006 moments/window).
     */
    public static Double jitterMs(java.util.List<Double> samples) {
        if (samples == null || samples.size() < MIN_JITTER_RTT_SAMPLES) {
            return null;
        }
        double mean =
                samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance =
                samples.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / samples.size();
        return Math.sqrt(variance);
    }

    /** Session/UI summary — loss may be reported after a single probe in the window. */
    public static HopStatsSummary summarize(HopProbeStats stats) {
        if (stats.getWindowProbes() == 0 && stats.getProbes() == 0) {
            return null;
        }
        if (stats.getWindowProbes() == 0) {
            return null;
        }
        return new HopStatsSummary(jitterMs(stats.getRttSamples()), lossPct(stats));
    }

    /**
     * Canonical metrics for {@code poll_result}: loss only with an explicit sliding probe window;
     * jitter only with an RTT series (P34-006 / P35-003).
     */
    public static HopStatsSummary summarizeForPollResult(HopProbeStats stats) {
        if (stats == null || stats.getWindowProbes() == 0) {
            return null;
        }
        return new HopStatsSummary(jitterMs(stats.getRttSamples()), lossPctInWindow(stats));
    }

    /**
     * Projects terminal-hop loss/jitter as if {@code sample} were recorded onto a copy of {@code
     * prior} (P34-005 / P34-006). Does not mutate session state. When the terminal hop is not fresh
     * under {@code scope}, returns a poll_result summary of {@code prior} only (or null).
     */
    public static HopStatsSummary projectTerminalAfterSample(
            HopProbeStats prior, RouteSnapshot snapshot, PollSampleScope scope) {
        HopNode terminal = CompletedPoll.terminalHop(snapshot);
        if (terminal == null) {
            return prior != null ? summarizeForPollResult(prior) : null;
        }
        PollSampleScope safe = scope != null ? scope : PollSampleScope.FULL;
        boolean fresh = safe.allHopsFresh() || (safe.hasFreshHopSample() && terminal.hop() == safe.freshHop());
        if (!fresh) {
            return prior != null ? summarizeForPollResult(prior) : null;
        }
        return summarizeAfter(prior, terminal);
    }

    /** Copy-on-write apply of one hop probe for immutable poll aggregates (P34-005 / P35-003). */
    public static HopStatsSummary summarizeAfter(HopProbeStats prior, HopNode sample) {
        HopProbeStats projected = prior == null ? new HopProbeStats() : prior.copy();
        recordProbe(projected, sample);
        return summarizeForPollResult(projected);
    }

    public static Double minRtt(java.util.List<Double> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        return samples.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }

    public static Double maxRtt(java.util.List<Double> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        return samples.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    public static Double avgRtt(java.util.List<Double> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        return samples.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    public static HostTargetStats targetStats(HopNode terminal, HopProbeStats stats) {
        if (stats == null || stats.getWindowProbes() == 0) {
            return null;
        }
        java.util.List<Double> samples = stats.getRttSamples();
        return new HostTargetStats(
                lossPct(stats),
                minRtt(samples),
                avgRtt(samples),
                maxRtt(samples),
                terminal.timeout() || !terminal.isReachable());
    }
}

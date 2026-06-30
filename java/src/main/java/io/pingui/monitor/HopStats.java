package io.pingui.monitor;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HopProbeStats;
import io.pingui.model.Models.HopStatsSummary;

/** Per-hop jitter and packet loss calculations (parity with Python hop_stats.py). */
public final class HopStats {
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

    public static double lossPct(HopProbeStats stats) {
        if (stats.getProbes() == 0) {
            return 0.0;
        }
        int failures = stats.getProbes() - stats.getSuccesses();
        return failures * 100.0 / stats.getProbes();
    }

    public static Double jitterMs(java.util.List<Double> samples) {
        if (samples.size() < 2) {
            return null;
        }
        double mean =
                samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance =
                samples.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / samples.size();
        return Math.sqrt(variance);
    }

    public static HopStatsSummary summarize(HopProbeStats stats) {
        if (stats.getProbes() == 0) {
            return null;
        }
        return new HopStatsSummary(jitterMs(stats.getRttSamples()), lossPct(stats));
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
        if (stats == null || stats.getProbes() == 0) {
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

package io.pingui.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * One time-bucket aggregate ({@code metric_rollup}, P30-005 / P32-004).
 *
 * <p>Stores additive counters/sums; averages and availability are computed on read so buckets merge
 * correctly across nullable RTT/loss samples.
 */
public record MetricRollupRecord(
        long hostId,
        String hostAddress,
        Instant bucketStart,
        int bucketSizeSeconds,
        int sampleCount,
        int reachableSamples,
        int reachableCount,
        int rttSamples,
        double rttSum,
        Double rttMin,
        Double rttMax,
        int lossSamples,
        double lossSum) {

    public MetricRollupRecord {
        Objects.requireNonNull(hostAddress, "hostAddress");
        Objects.requireNonNull(bucketStart, "bucketStart");
        if (bucketSizeSeconds < 1) {
            throw new IllegalArgumentException("bucketSizeSeconds must be >= 1");
        }
        if (sampleCount < 1) {
            throw new IllegalArgumentException("sampleCount must be >= 1");
        }
        if (reachableSamples < 0 || reachableCount < 0 || rttSamples < 0 || lossSamples < 0) {
            throw new IllegalArgumentException("sample counters must be >= 0");
        }
        if (reachableCount > reachableSamples) {
            throw new IllegalArgumentException("reachableCount cannot exceed reachableSamples");
        }
    }

    /** Alias for {@link #sampleCount()} (historical API). */
    public int samples() {
        return sampleCount;
    }

    /** {@code reachable_count / reachable_samples}, or {@code null} when unknown. */
    public Double uptimeRatio() {
        return reachableSamples == 0 ? null : (double) reachableCount / reachableSamples;
    }

    /** {@code rtt_sum / rtt_samples}, or {@code null} when no RTT samples. */
    public Double rttAvg() {
        return rttSamples == 0 ? null : rttSum / rttSamples;
    }

    /** {@code loss_sum / loss_samples}, or {@code null} when no loss samples. */
    public Double lossAvg() {
        return lossSamples == 0 ? null : lossSum / lossSamples;
    }
}

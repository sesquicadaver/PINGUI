package io.pingui.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * One time-bucket aggregate ({@code metric_rollup}, P30-005). {@code bucketSizeSeconds} is typically
 * 300 (5m) or 3600 (1h).
 */
public record MetricRollupRecord(
        long hostId,
        String hostAddress,
        Instant bucketStart,
        int bucketSizeSeconds,
        int samples,
        Double uptimeRatio,
        Double rttMin,
        Double rttAvg,
        Double rttMax,
        Double lossAvg) {

    public MetricRollupRecord {
        Objects.requireNonNull(hostAddress, "hostAddress");
        Objects.requireNonNull(bucketStart, "bucketStart");
        if (bucketSizeSeconds < 1) {
            throw new IllegalArgumentException("bucketSizeSeconds must be >= 1");
        }
        if (samples < 1) {
            throw new IllegalArgumentException("samples must be >= 1");
        }
    }
}

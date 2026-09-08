package io.pingui.geoip;

import java.time.Instant;

/**
 * Operator-visible counters for {@link IpMetadataService} (P36-006 / P36-010).
 *
 * <p>Exported on {@code GET /ops}, Prometheus scrape, and App Status pressure line.
 */
public record GeoIpOpsStats(
        int cacheSize,
        int cacheCapacity,
        int queueDepth,
        int queueCapacity,
        int pendingLookups,
        long hits,
        long misses,
        long unknowns,
        long errors,
        long rejected,
        long coalesced,
        long reloadFailures,
        Instant lastSuccessfulReload) {

    /** Zero snapshot when enrichment is disabled or supplier is absent. */
    public static GeoIpOpsStats empty() {
        return new GeoIpOpsStats(0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null);
    }

    /** True when the operator should notice enrichment pressure. */
    public boolean hasPressure() {
        return rejected > 0L || errors > 0L || reloadFailures > 0L;
    }
}

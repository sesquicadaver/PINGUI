package io.pingui.geoip;

import java.time.Instant;

/**
 * Operator-visible counters for {@link IpMetadataService} (P36-006).
 *
 * <p>Full {@code /ops} / Prometheus / App Status wiring lands in P36-010; this snapshot is the
 * stable contract those surfaces will export.
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
        Instant lastSuccessfulReload) {}

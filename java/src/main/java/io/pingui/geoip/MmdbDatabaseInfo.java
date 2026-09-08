package io.pingui.geoip;

import java.time.Instant;

/**
 * Immutable MMDB identity for ops/diagnostics (P36-005).
 *
 * @param databaseType MaxMind metadata database type (e.g. {@code GeoLite2-City})
 * @param buildEpochSeconds database build time as epoch seconds
 */
public record MmdbDatabaseInfo(String databaseType, long buildEpochSeconds) {
    public MmdbDatabaseInfo {
        if (databaseType == null || databaseType.isBlank()) {
            throw new IllegalArgumentException("databaseType is required");
        }
        databaseType = databaseType.strip();
    }

    public Instant buildInstant() {
        return Instant.ofEpochSecond(buildEpochSeconds);
    }
}

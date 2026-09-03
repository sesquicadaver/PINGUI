package io.pingui.persistence;

import java.time.Instant;
import java.util.Objects;

/** One deduplicated route history row ({@code route}, P30-004). */
public record RouteRecord(
        long id,
        long hostId,
        String hostAddress,
        String signature,
        String hopsJson,
        Instant firstSeen,
        Instant lastSeen,
        int seenCount) {

    public RouteRecord {
        Objects.requireNonNull(hostAddress, "hostAddress");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(hopsJson, "hopsJson");
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (seenCount < 1) {
            throw new IllegalArgumentException("seenCount must be >= 1");
        }
    }
}

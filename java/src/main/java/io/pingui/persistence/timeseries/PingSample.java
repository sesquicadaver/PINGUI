package io.pingui.persistence.timeseries;

import java.time.Instant;
import java.util.Objects;

/** One RTT observation for a hop on a monitored target (Python PingSample parity). */
public record PingSample(String targetHost, int hop, String hopIp, double rttMs, Instant observedAt) {
    public PingSample {
        Objects.requireNonNull(targetHost, "targetHost");
        Objects.requireNonNull(hopIp, "hopIp");
        Objects.requireNonNull(observedAt, "observedAt");
        if (hop < 1) {
            throw new IllegalArgumentException("hop must be >= 1");
        }
    }
}

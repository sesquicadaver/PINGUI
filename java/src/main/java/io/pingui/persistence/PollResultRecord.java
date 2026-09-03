package io.pingui.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * One finished-poll aggregate row ({@code poll_result}, P30-003). Canonical history for RTT/loss
 * charts — not a duplicate of {@code telemetry_sample}.
 */
public record PollResultRecord(
        long id,
        long hostId,
        String hostAddress,
        Instant observedAt,
        String probeMode,
        Boolean reachable,
        Double terminalRttMs,
        Double jitterMs,
        Double lossPercent,
        Double durationMs,
        Long routeId,
        String errorCode) {

    public PollResultRecord {
        Objects.requireNonNull(hostAddress, "hostAddress");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(probeMode, "probeMode");
    }
}

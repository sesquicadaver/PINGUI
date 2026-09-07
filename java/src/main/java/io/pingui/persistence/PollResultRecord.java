package io.pingui.persistence;

import io.pingui.probe.ProbeOutcome;
import java.time.Instant;
import java.util.Objects;

/**
 * One finished-poll aggregate row ({@code poll_result}, P30-003 / P32-003 / P34-006). Canonical history for
 * RTT — not a duplicate of {@code telemetry_sample}. {@code lossPercent} is null until an explicit
 * probe window ({@link io.pingui.monitor.HopStats#MIN_LOSS_WINDOW_PROBES}); {@code jitterMs} only when
 * an RTT series exists.
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
        String errorCode,
        ProbeOutcome probeOutcome,
        boolean targetSampled) {

    public PollResultRecord {
        Objects.requireNonNull(hostAddress, "hostAddress");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(probeMode, "probeMode");
        probeOutcome = probeOutcome != null ? probeOutcome : ProbeOutcome.NETWORK_ERROR;
    }
}

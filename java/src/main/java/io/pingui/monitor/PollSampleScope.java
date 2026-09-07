package io.pingui.monitor;

/**
 * Which samples from a poll are fresh (P32-001 / P35-002).
 *
 * <p>{@code freshHop == null} means every hop in the snapshot is a new measurement (TRACE / PING /
 * TCP). For MTR, only the probed hop is fresh. {@link #UNSAMPLED} means no hop is fresh and the
 * target was not observed (MTR {@code TARGET_UNKNOWN} idle) — never treat as {@link #FULL}.
 */
public record PollSampleScope(Integer freshHop, boolean targetSampled) {
    public static final PollSampleScope FULL = new PollSampleScope(null, true);

    /**
     * Idle / unknown-target scope: no fresh hop index, {@code targetSampled=false} (P35-002).
     *
     * <p>{@code freshHop=0} is an intentional sentinel so {@link #allHopsFresh()} is false and hop
     * stats / ping history are not rewritten from a stale partial path.
     */
    public static final PollSampleScope UNSAMPLED = new PollSampleScope(0, false);

    public static PollSampleScope mtr(int probedHop, boolean targetSampled) {
        if (probedHop < 1) {
            throw new IllegalArgumentException("probedHop must be >= 1");
        }
        return new PollSampleScope(probedHop, targetSampled);
    }

    /** True when hop statistics / telemetry should treat every node as a new sample. */
    public boolean allHopsFresh() {
        return freshHop == null;
    }

    /** True when a single MTR hop index carries a fresh sample. */
    public boolean hasFreshHopSample() {
        return freshHop != null && freshHop >= 1;
    }
}

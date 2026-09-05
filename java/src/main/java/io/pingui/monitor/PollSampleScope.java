package io.pingui.monitor;

/**
 * Which samples from a poll are fresh (P32-001).
 *
 * <p>{@code freshHop == null} means every hop in the snapshot is a new measurement (TRACE / PING /
 * TCP). For MTR, only the probed hop is fresh.
 */
public record PollSampleScope(Integer freshHop, boolean targetSampled) {
    public static final PollSampleScope FULL = new PollSampleScope(null, true);

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
}

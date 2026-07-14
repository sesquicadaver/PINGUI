package io.pingui.probe;

import java.util.List;
import java.util.OptionalInt;

/** Outcome of a {@link MtuDiscovery} sweep (P17-020). */
public record MtuDiscoveryResult(
        OptionalInt maxGoodPayload,
        OptionalInt recommendedMtu,
        List<MtuProbeStep> steps,
        boolean stoppedOnLoss,
        boolean cancelled) {

    public MtuDiscoveryResult {
        maxGoodPayload = maxGoodPayload != null ? maxGoodPayload : OptionalInt.empty();
        recommendedMtu = recommendedMtu != null ? recommendedMtu : OptionalInt.empty();
        steps = steps != null ? List.copyOf(steps) : List.of();
    }

    /** One size sample during the sweep. */
    public record MtuProbeStep(int payloadBytes, int sent, int lost, double lossPct, boolean stoppedHere) {
        public MtuProbeStep {
            if (payloadBytes < 0 || sent < 0 || lost < 0 || lost > sent) {
                throw new IllegalArgumentException("invalid probe step counts");
            }
        }
    }
}

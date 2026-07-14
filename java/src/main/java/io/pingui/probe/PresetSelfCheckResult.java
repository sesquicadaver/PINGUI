package io.pingui.probe;

import java.util.List;
import java.util.OptionalDouble;

/** Outcome of {@link PresetSelfCheck} (P17-030). */
public record PresetSelfCheckResult(List<PresetCheck> checks, boolean anyWarn) {

    public PresetSelfCheckResult {
        checks = checks != null ? List.copyOf(checks) : List.of();
    }

    /** One preset sample batch. */
    public record PresetCheck(
            String presetId,
            String label,
            List<String> extendedArgs,
            int sent,
            int lost,
            double lossPct,
            OptionalDouble avgRttMs,
            boolean warn) {
        public PresetCheck {
            if (presetId == null || presetId.isBlank()) {
                throw new IllegalArgumentException("presetId required");
            }
            label = label != null ? label : presetId;
            extendedArgs = extendedArgs != null ? List.copyOf(extendedArgs) : List.of();
            if (sent < 0 || lost < 0 || lost > sent) {
                throw new IllegalArgumentException("invalid probe counts");
            }
            avgRttMs = avgRttMs != null ? avgRttMs : OptionalDouble.empty();
        }
    }
}

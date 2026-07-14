package io.pingui.probe;

import java.util.List;

/**
 * Parameters for informational DF / DSCP / Burst self-check (P17-030).
 *
 * <p>Defaults: 3 probes per preset, 1s timeout, warn when loss% ≥ 1.
 */
public record PresetSelfCheckConfig(
        int probesPerPreset, double timeoutSeconds, double lossWarnPct, boolean ipv6, List<String> presetIds) {

    public static final List<String> DEFAULT_PRESET_IDS = List.of("df", "dscp", "burst");

    public PresetSelfCheckConfig {
        if (probesPerPreset < 1) {
            throw new IllegalArgumentException("probesPerPreset must be >= 1");
        }
        if (timeoutSeconds <= 0.0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        if (lossWarnPct < 0.0 || lossWarnPct > 100.0) {
            throw new IllegalArgumentException("lossWarnPct must be 0..100");
        }
        if (presetIds == null || presetIds.isEmpty()) {
            throw new IllegalArgumentException("presetIds must be non-empty");
        }
        presetIds = List.copyOf(presetIds);
    }

    public static PresetSelfCheckConfig ipv4Defaults() {
        return new PresetSelfCheckConfig(3, 1.0, 1.0, false, DEFAULT_PRESET_IDS);
    }

    public static PresetSelfCheckConfig ipv6Defaults() {
        return new PresetSelfCheckConfig(3, 1.0, 1.0, true, DEFAULT_PRESET_IDS);
    }
}

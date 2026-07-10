package io.pingui.monitor;

/** Monitoring strategy per host (P13-001); orthogonal to transport {@link io.pingui.probe.ProbeMode}. */
public enum HostProbeMode {
    TRACE("trace"),
    MTR("mtr"),
    PING_ONLY("ping_only");

    private final String yamlValue;

    HostProbeMode(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    public String yamlValue() {
        return yamlValue;
    }

    public boolean isPingOnly() {
        return this == PING_ONLY;
    }

    public static HostProbeMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("probe_mode value required");
        }
        String normalized = value.strip().toLowerCase();
        for (HostProbeMode mode : values()) {
            if (mode.yamlValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown probe_mode: " + value);
    }
}

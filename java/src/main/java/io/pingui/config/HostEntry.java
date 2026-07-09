package io.pingui.config;

import io.pingui.monitor.HostProbeMode;

/** One monitored target inside a tracing profile. */
public record HostEntry(
        String address,
        boolean enabled,
        boolean pingOnly,
        PingExpertEntry pingExpert,
        HostProbeMode probeModeOverride) {
    public HostEntry {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address required");
        }
        pingExpert = pingExpert != null ? pingExpert.normalized() : PingExpertEntry.empty();
    }

    public HostEntry(String address, boolean enabled, boolean pingOnly, PingExpertEntry pingExpert) {
        this(address, enabled, pingOnly, pingExpert, null);
    }

    public static HostEntry basic(String address, boolean enabled) {
        return new HostEntry(address, enabled, false, PingExpertEntry.empty());
    }

    public HostEntry withPingExpert(PingExpertEntry expert) {
        return new HostEntry(
                address,
                enabled,
                pingOnly,
                expert != null ? expert.normalized() : PingExpertEntry.empty(),
                probeModeOverride);
    }

    public HostEntry withPingOnly(boolean hostPingOnly) {
        return new HostEntry(address, enabled, hostPingOnly, pingExpert, probeModeOverride);
    }

    public HostEntry withProbeModeOverride(HostProbeMode override) {
        return new HostEntry(address, enabled, pingOnly, pingExpert, override);
    }

    /** Effective monitoring strategy: host override, then legacy {@code ping_only}, then profile default. */
    public HostProbeMode effectiveProbeMode(HostProbeMode profileDefault) {
        if (probeModeOverride != null) {
            return probeModeOverride;
        }
        if (pingOnly) {
            return HostProbeMode.PING_ONLY;
        }
        return profileDefault != null ? profileDefault : HostProbeMode.TRACE;
    }
}

package io.pingui.config;

import io.pingui.monitor.HostPollSchedule;
import io.pingui.monitor.HostProbeMode;
import java.util.OptionalDouble;

/** One monitored target inside a tracing profile. */
public record HostEntry(
        String address,
        boolean enabled,
        boolean pingOnly,
        PingExpertEntry pingExpert,
        HostProbeMode probeModeOverride,
        Double intervalSecondsOverride) {
    public HostEntry {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address required");
        }
        pingExpert = pingExpert != null ? pingExpert.normalized() : PingExpertEntry.empty();
        if (intervalSecondsOverride != null && intervalSecondsOverride <= 0) {
            throw new IllegalArgumentException("interval must be positive");
        }
    }

    public HostEntry(String address, boolean enabled, boolean pingOnly, PingExpertEntry pingExpert) {
        this(address, enabled, pingOnly, pingExpert, null, null);
    }

    public HostEntry(
            String address,
            boolean enabled,
            boolean pingOnly,
            PingExpertEntry pingExpert,
            HostProbeMode probeModeOverride) {
        this(address, enabled, pingOnly, pingExpert, probeModeOverride, null);
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
                probeModeOverride,
                intervalSecondsOverride);
    }

    public HostEntry withPingOnly(boolean hostPingOnly) {
        return new HostEntry(address, enabled, hostPingOnly, pingExpert, probeModeOverride, intervalSecondsOverride);
    }

    public HostEntry withProbeModeOverride(HostProbeMode override) {
        return new HostEntry(address, enabled, pingOnly, pingExpert, override, intervalSecondsOverride);
    }

    public HostEntry withIntervalSecondsOverride(Double override) {
        return new HostEntry(address, enabled, pingOnly, pingExpert, probeModeOverride, override);
    }

    public OptionalDouble intervalOverride() {
        return intervalSecondsOverride != null ? OptionalDouble.of(intervalSecondsOverride) : OptionalDouble.empty();
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

    /** Effective poll cadence for this host (P13-020). */
    public double effectiveIntervalSeconds(HostProbeMode profileDefault, double profileIntervalSeconds) {
        HostProbeMode mode = effectiveProbeMode(profileDefault);
        return HostPollSchedule.effectiveInterval(mode, profileIntervalSeconds, intervalOverride());
    }
}

package io.pingui;

import io.pingui.config.TracingProfile;
import io.pingui.probe.ProbeMode;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** CLI overrides for tracing profile fields; empty = keep YAML value. */
public record CliProfileOverrides(
        OptionalDouble intervalSeconds,
        OptionalInt maxHops,
        OptionalDouble timeoutSeconds,
        Optional<ProbeMode> probeMode) {

    public static CliProfileOverrides none() {
        return new CliProfileOverrides(
                OptionalDouble.empty(), OptionalInt.empty(), OptionalDouble.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return intervalSeconds.isEmpty() && maxHops.isEmpty() && timeoutSeconds.isEmpty() && probeMode.isEmpty();
    }

    /** Returns profile with only present CLI fields replaced. */
    public TracingProfile applyTo(TracingProfile profile) {
        return new TracingProfile(
                intervalSeconds.orElse(profile.intervalSeconds()),
                maxHops.orElse(profile.maxHops()),
                timeoutSeconds.orElse(profile.timeoutSeconds()),
                probeMode.orElse(profile.probeMode()),
                profile.hosts());
    }
}

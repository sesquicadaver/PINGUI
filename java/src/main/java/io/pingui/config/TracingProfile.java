package io.pingui.config;

import io.pingui.probe.ProbeMode;
import java.util.ArrayList;
import java.util.List;

/** Named tracing session: poll settings, host list, and alert channels. */
public record TracingProfile(
        double intervalSeconds,
        int maxHops,
        double timeoutSeconds,
        ProbeMode probeMode,
        List<HostEntry> hosts,
        AlertConfig alerts) {
    public TracingProfile {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be positive");
        }
        if (maxHops < 1) {
            throw new IllegalArgumentException("maxHops must be >= 1");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        probeMode = probeMode != null ? probeMode : ProbeMode.AUTO;
        hosts = List.copyOf(hosts != null ? hosts : List.of());
        alerts = alerts != null ? alerts : AlertConfig.disabled();
    }

    public static TracingProfile defaults(List<HostEntry> hosts) {
        return new TracingProfile(1.0, 20, 0.5, ProbeMode.AUTO, hosts, AlertConfig.disabled());
    }

    public TracingProfile withHosts(List<HostEntry> newHosts) {
        return new TracingProfile(intervalSeconds, maxHops, timeoutSeconds, probeMode, newHosts, alerts);
    }

    public TracingProfile withAlerts(AlertConfig newAlerts) {
        return new TracingProfile(intervalSeconds, maxHops, timeoutSeconds, probeMode, hosts, newAlerts);
    }

    public List<String> hostAddresses() {
        List<String> out = new ArrayList<>();
        for (HostEntry entry : hosts) {
            out.add(entry.address());
        }
        return List.copyOf(out);
    }
}

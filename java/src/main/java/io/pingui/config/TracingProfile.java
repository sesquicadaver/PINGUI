package io.pingui.config;

import io.pingui.probe.ProbeMode;
import java.util.ArrayList;
import java.util.List;

/** Named tracing session: poll settings, host list, alert channels, and persistence events. */
public record TracingProfile(
        double intervalSeconds,
        int maxHops,
        double timeoutSeconds,
        ProbeMode probeMode,
        List<HostEntry> hosts,
        AlertConfig alerts,
        PersistenceEventsConfig persistence) {
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
        persistence = persistence != null ? persistence : PersistenceEventsConfig.defaults();
    }

    public static TracingProfile defaults(List<HostEntry> hosts) {
        return new TracingProfile(
                1.0, 20, 0.5, ProbeMode.AUTO, hosts, AlertConfig.disabled(), PersistenceEventsConfig.defaults());
    }

    public TracingProfile withHosts(List<HostEntry> newHosts) {
        return new TracingProfile(intervalSeconds, maxHops, timeoutSeconds, probeMode, newHosts, alerts, persistence);
    }

    public TracingProfile withAlerts(AlertConfig newAlerts) {
        return new TracingProfile(intervalSeconds, maxHops, timeoutSeconds, probeMode, hosts, newAlerts, persistence);
    }

    public TracingProfile withPersistence(PersistenceEventsConfig newPersistence) {
        return new TracingProfile(intervalSeconds, maxHops, timeoutSeconds, probeMode, hosts, alerts, newPersistence);
    }

    public List<String> hostAddresses() {
        List<String> out = new ArrayList<>();
        for (HostEntry entry : hosts) {
            out.add(entry.address());
        }
        return List.copyOf(out);
    }
}

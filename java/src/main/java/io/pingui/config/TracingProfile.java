package io.pingui.config;

import io.pingui.monitor.HostProbeMode;
import io.pingui.probe.ProbeMode;
import java.util.ArrayList;
import java.util.List;

/** Named tracing session: poll settings, host list, alert channels, and persistence. */
public record TracingProfile(
        double intervalSeconds,
        int maxHops,
        double timeoutSeconds,
        ProbeMode probeMode,
        HostProbeMode hostProbeMode,
        List<HostEntry> hosts,
        AlertConfig alerts,
        PersistenceConfig persistence,
        int maxConcurrentTraces) {
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
        if (maxConcurrentTraces < 1) {
            throw new IllegalArgumentException("maxConcurrentTraces must be >= 1");
        }
        probeMode = probeMode != null ? probeMode : ProbeMode.AUTO;
        hostProbeMode = hostProbeMode != null ? hostProbeMode : HostProbeMode.TRACE;
        hosts = List.copyOf(hosts != null ? hosts : List.of());
        alerts = alerts != null ? alerts : AlertConfig.disabled();
        persistence = persistence != null ? persistence : PersistenceConfig.defaults();
    }

    /** Legacy constructor without explicit {@code hostProbeMode} (defaults to trace). */
    public TracingProfile(
            double intervalSeconds,
            int maxHops,
            double timeoutSeconds,
            ProbeMode probeMode,
            List<HostEntry> hosts,
            AlertConfig alerts,
            PersistenceConfig persistence) {
        this(
                intervalSeconds,
                maxHops,
                timeoutSeconds,
                probeMode,
                HostProbeMode.TRACE,
                hosts,
                alerts,
                persistence,
                io.pingui.monitor.TraceConcurrencyLimiter.DEFAULT_MAX);
    }

    public static TracingProfile defaults(List<HostEntry> hosts) {
        return new TracingProfile(
                1.0,
                20,
                0.5,
                ProbeMode.AUTO,
                HostProbeMode.TRACE,
                hosts,
                AlertConfig.disabled(),
                PersistenceConfig.defaults(),
                io.pingui.monitor.TraceConcurrencyLimiter.DEFAULT_MAX);
    }

    public TracingProfile withHosts(List<HostEntry> newHosts) {
        return new TracingProfile(
                intervalSeconds,
                maxHops,
                timeoutSeconds,
                probeMode,
                hostProbeMode,
                newHosts,
                alerts,
                persistence,
                maxConcurrentTraces);
    }

    public TracingProfile withAlerts(AlertConfig newAlerts) {
        return new TracingProfile(
                intervalSeconds,
                maxHops,
                timeoutSeconds,
                probeMode,
                hostProbeMode,
                hosts,
                newAlerts,
                persistence,
                maxConcurrentTraces);
    }

    public TracingProfile withPersistence(PersistenceConfig newPersistence) {
        return new TracingProfile(
                intervalSeconds,
                maxHops,
                timeoutSeconds,
                probeMode,
                hostProbeMode,
                hosts,
                alerts,
                newPersistence,
                maxConcurrentTraces);
    }

    public List<String> hostAddresses() {
        List<String> out = new ArrayList<>();
        for (HostEntry entry : hosts) {
            out.add(entry.address());
        }
        return List.copyOf(out);
    }
}

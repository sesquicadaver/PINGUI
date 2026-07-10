package io.pingui.monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shortens poll cadence after route changes (P13-021 / ADR_PROBE_MODES). */
public final class BurstSchedulePolicy {
    public static final double BURST_FACTOR = 0.25;
    public static final Duration BURST_DURATION = Duration.ofMinutes(5);

    private final ConcurrentMap<String, Instant> burstEnds = new ConcurrentHashMap<>();

    /** Arms burst only for reroutes, not incremental hop discovery (MTR DISCOVERING). */
    public static boolean shouldArmBurst(List<String> oldIps, List<String> newIps) {
        if (oldIps == null || newIps == null || oldIps.isEmpty() || newIps.isEmpty()) {
            return false;
        }
        if (oldIps.equals(newIps)) {
            return false;
        }
        if (newIps.size() > oldIps.size()) {
            boolean prefixMatch = true;
            for (int i = 0; i < oldIps.size(); i++) {
                if (!oldIps.get(i).equals(newIps.get(i))) {
                    prefixMatch = false;
                    break;
                }
            }
            if (prefixMatch) {
                return false;
            }
        }
        return true;
    }

    /** Starts or extends a burst window for {@code host} from {@code now}. */
    public void onRouteChange(String host, Instant now) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host required");
        }
        if (now == null) {
            throw new IllegalArgumentException("now required");
        }
        burstEnds.put(host, now.plus(BURST_DURATION));
    }

    /** Returns {@code baseIntervalSeconds} or burst-shortened value while active. */
    public double effectiveInterval(String host, double baseIntervalSeconds, Instant now) {
        if (baseIntervalSeconds <= 0) {
            throw new IllegalArgumentException("baseIntervalSeconds must be positive");
        }
        if (now == null) {
            throw new IllegalArgumentException("now required");
        }
        Instant burstEnd = burstEnds.get(host);
        if (burstEnd == null) {
            return baseIntervalSeconds;
        }
        if (now.isBefore(burstEnd)) {
            double burstInterval = baseIntervalSeconds * BURST_FACTOR;
            return Math.max(HostPollSchedule.TICK_SECONDS, burstInterval);
        }
        burstEnds.remove(host);
        return baseIntervalSeconds;
    }

    public boolean isBurstActive(String host, Instant now) {
        Instant burstEnd = burstEnds.get(host);
        return burstEnd != null && now.isBefore(burstEnd);
    }

    public void clearHost(String host) {
        burstEnds.remove(host);
    }

    public void renameHost(String oldHost, String newHost) {
        Instant burstEnd = burstEnds.remove(oldHost);
        if (burstEnd != null) {
            burstEnds.put(newHost, burstEnd);
        }
    }
}

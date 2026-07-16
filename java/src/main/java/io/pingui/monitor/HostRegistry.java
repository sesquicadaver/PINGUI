package io.pingui.monitor;

import io.pingui.config.ConfigError;
import io.pingui.config.HostsConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe registry of monitored hosts: membership, enabled flag, probe mode, and poll bookmarks.
 *
 * <p>Extracted from {@link MonitorService} (P19-005). Interval overrides remain external via
 * {@link MonitorService.HostPollIntervalResolver}.
 */
final class HostRegistry {
    private final Object lock = new Object();
    private final List<String> hosts = new ArrayList<>();
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final Map<String, HostProbeMode> probeModes = new HashMap<>();
    private final Map<String, List<String>> lastRoutes = new HashMap<>();
    private final Map<String, Instant> lastPollAt = new HashMap<>();
    private final Map<String, AtomicBoolean> pollsInFlight = new ConcurrentHashMap<>();

    List<String> hosts() {
        synchronized (lock) {
            return List.copyOf(hosts);
        }
    }

    List<String> enabledHosts() {
        synchronized (lock) {
            return hosts.stream()
                    .filter(h -> Boolean.TRUE.equals(enabled.get(h)))
                    .toList();
        }
    }

    boolean canAdd() {
        synchronized (lock) {
            return hosts.size() < HostsConfig.MAX_HOSTS;
        }
    }

    boolean contains(String host) {
        synchronized (lock) {
            return hosts.contains(host);
        }
    }

    void add(String host, boolean hostEnabled, HostProbeMode probeMode) {
        synchronized (lock) {
            if (hosts.contains(host)) {
                throw new ConfigError("Host already in list: " + host);
            }
            hosts.add(host);
            enabled.put(host, hostEnabled);
            probeModes.put(host, probeMode != null ? probeMode : HostProbeMode.TRACE);
            lastRoutes.put(host, List.of());
            lastPollAt.remove(host);
        }
    }

    void remove(String host) {
        synchronized (lock) {
            if (!hosts.remove(host)) {
                throw new ConfigError("Unknown host: " + host);
            }
            enabled.remove(host);
            probeModes.remove(host);
            lastRoutes.remove(host);
            lastPollAt.remove(host);
        }
        pollsInFlight.remove(host);
    }

    void rename(String oldHost, String newHost) {
        synchronized (lock) {
            int index = hosts.indexOf(oldHost);
            if (index < 0) {
                throw new ConfigError("Unknown host: " + oldHost);
            }
            hosts.set(index, newHost);
            Boolean wasEnabled = enabled.remove(oldHost);
            if (wasEnabled != null) {
                enabled.put(newHost, wasEnabled);
            }
            HostProbeMode wasMode = probeModes.remove(oldHost);
            if (wasMode != null) {
                probeModes.put(newHost, wasMode);
            }
            lastRoutes.put(newHost, lastRoutes.remove(oldHost));
            Instant wasLastPoll = lastPollAt.remove(oldHost);
            if (wasLastPoll != null) {
                lastPollAt.put(newHost, wasLastPoll);
            }
        }
        AtomicBoolean inFlight = pollsInFlight.remove(oldHost);
        if (inFlight != null) {
            pollsInFlight.put(newHost, inFlight);
        }
    }

    void setEnabled(String host, boolean hostEnabled) {
        synchronized (lock) {
            requireKnown(host);
            enabled.put(host, hostEnabled);
        }
    }

    /** Sets probe mode and clears route/poll bookmarks for a fresh schedule. */
    void setProbeMode(String host, HostProbeMode probeMode) {
        synchronized (lock) {
            requireKnown(host);
            HostProbeMode mode = probeMode != null ? probeMode : HostProbeMode.TRACE;
            probeModes.put(host, mode);
            lastRoutes.put(host, List.of());
            lastPollAt.remove(host);
        }
    }

    HostProbeMode mappedMode(String host, HostProbeMode profileDefault) {
        synchronized (lock) {
            return probeModes.getOrDefault(host, profileDefault);
        }
    }

    Instant lastPollAt(String host) {
        synchronized (lock) {
            return lastPollAt.get(host);
        }
    }

    List<String> copyLastRoute(String host) {
        synchronized (lock) {
            return List.copyOf(lastRoutes.getOrDefault(host, List.of()));
        }
    }

    void markPolled(String host, Instant when) {
        synchronized (lock) {
            if (hosts.contains(host)) {
                lastPollAt.put(host, when);
            }
        }
    }

    void putLastRoute(String host, List<String> currentIps) {
        synchronized (lock) {
            if (hosts.contains(host)) {
                lastRoutes.put(host, List.copyOf(currentIps));
            }
        }
    }

    /**
     * Snapshot for the start of a poll: known host, previous route IPs, and mapped probe mode.
     * Returns {@code null} if the host was removed.
     */
    PollStart beginPoll(String host, HostProbeMode profileDefault, Instant now) {
        synchronized (lock) {
            if (!hosts.contains(host)) {
                return null;
            }
            List<String> previousIps = List.copyOf(lastRoutes.getOrDefault(host, List.of()));
            HostProbeMode mapped = probeModes.getOrDefault(host, profileDefault);
            lastPollAt.put(host, now);
            return new PollStart(previousIps, mapped);
        }
    }

    /** True when host is still present and mapped mode matches {@code mappedAtStart}. */
    boolean mappedModeUnchanged(String host, HostProbeMode mappedAtStart, HostProbeMode profileDefault) {
        synchronized (lock) {
            if (!hosts.contains(host)) {
                return false;
            }
            return probeModes.getOrDefault(host, profileDefault) == mappedAtStart;
        }
    }

    AtomicBoolean inFlightFlag(String host) {
        return pollsInFlight.computeIfAbsent(host, ignored -> new AtomicBoolean(false));
    }

    private void requireKnown(String host) {
        if (!hosts.contains(host)) {
            throw new ConfigError("Unknown host: " + host);
        }
    }

    /** Immutable poll-start bookmark (previous route + mapped mode). */
    record PollStart(List<String> previousIps, HostProbeMode mappedMode) {}
}

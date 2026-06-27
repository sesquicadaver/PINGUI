package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProcessRouteProbe;
import io.pingui.probe.RouteProbe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Background polling of enabled hosts (cross-platform, no Qt). */
public final class MonitorService implements AutoCloseable {
    public interface Listener {
        void onDataReceived(String host, RouteSnapshot snapshot);

        void onRouteChanged(String host, List<String> oldIps, List<String> newIps);

        void onProbeError(String host, String message);
    }

    private final RoutePoller poller;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object lock = new Object();
    private final List<String> hosts = new ArrayList<>();
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final Map<String, List<String>> lastRoutes = new HashMap<>();
    private final double intervalSeconds;
    private final int maxHops;
    private final double timeoutSeconds;
    private Listener listener;

    public MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds) {
        this(intervalSeconds, maxHops, timeoutSeconds, new ProcessRouteProbe());
    }

    MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds, RouteProbe probe) {
        this.intervalSeconds = intervalSeconds;
        this.maxHops = maxHops;
        this.timeoutSeconds = timeoutSeconds;
        this.poller = new RoutePoller(probe);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "pingui-monitor");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::cycle, 0, (long) (intervalSeconds * 1000), TimeUnit.MILLISECONDS);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public List<String> hosts() {
        synchronized (lock) {
            return List.copyOf(hosts);
        }
    }

    public List<String> enabledHosts() {
        synchronized (lock) {
            return hosts.stream().filter(h -> Boolean.TRUE.equals(enabled.get(h))).toList();
        }
    }

    public boolean canAddHost() {
        synchronized (lock) {
            return hosts.size() < io.pingui.config.HostsConfig.MAX_HOSTS;
        }
    }

    public void addHost(String host, boolean hostEnabled) {
        synchronized (lock) {
            if (hosts.contains(host)) {
                throw new io.pingui.config.ConfigError("Host already in list: " + host);
            }
            hosts.add(host);
            enabled.put(host, hostEnabled);
            lastRoutes.put(host, List.of());
        }
    }

    public void removeHost(String host) {
        synchronized (lock) {
            if (!hosts.remove(host)) {
                throw new io.pingui.config.ConfigError("Unknown host: " + host);
            }
            enabled.remove(host);
            lastRoutes.remove(host);
        }
    }

    public void renameHost(String oldHost, String newHost) {
        synchronized (lock) {
            int index = hosts.indexOf(oldHost);
            if (index < 0) {
                throw new io.pingui.config.ConfigError("Unknown host: " + oldHost);
            }
            hosts.set(index, newHost);
            Boolean wasEnabled = enabled.remove(oldHost);
            if (wasEnabled != null) {
                enabled.put(newHost, wasEnabled);
            }
            lastRoutes.put(newHost, lastRoutes.remove(oldHost));
        }
    }

    public void setHostEnabled(String host, boolean hostEnabled) {
        synchronized (lock) {
            if (!hosts.contains(host)) {
                throw new io.pingui.config.ConfigError("Unknown host: " + host);
            }
            enabled.put(host, hostEnabled);
        }
    }

    private void cycle() {
        if (!running.get()) {
            return;
        }
        List<String> active;
        synchronized (lock) {
            active = hosts.stream().filter(h -> Boolean.TRUE.equals(enabled.get(h))).toList();
        }
        for (String host : active) {
            if (!running.get()) {
                break;
            }
            List<String> previousIps;
            synchronized (lock) {
                previousIps = List.copyOf(lastRoutes.getOrDefault(host, List.of()));
            }
            HostPollOutcome outcome = poller.pollHostRoute(host, previousIps, maxHops, timeoutSeconds);
            Listener current = listener;
            if (current == null) {
                continue;
            }
            if (outcome.error() != null) {
                current.onProbeError(host, outcome.error());
                continue;
            }
            if (outcome.routeChanged()) {
                current.onRouteChanged(host, outcome.oldIps(), outcome.newIps());
            }
            synchronized (lock) {
                lastRoutes.put(host, outcome.currentIps());
            }
            if (outcome.snapshot() != null) {
                current.onDataReceived(host, outcome.snapshot());
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

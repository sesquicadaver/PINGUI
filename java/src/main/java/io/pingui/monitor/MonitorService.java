package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.ProbeMode;
import io.pingui.probe.RouteProbe;
import io.pingui.probe.RouteProbeFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Background polling of enabled hosts (cross-platform, no Qt). */
public final class MonitorService implements AutoCloseable {
    private static final int MAX_PARALLEL_PROBES = 4;

    public interface Listener {
        void onDataReceived(String host, RouteSnapshot snapshot);

        void onRouteChanged(String host, List<String> oldIps, List<String> newIps);

        void onProbeError(String host, String message);
    }

    private final RoutePoller poller;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService probePool;
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
        this(intervalSeconds, maxHops, timeoutSeconds, ProbeMode.AUTO);
    }

    public MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds, ProbeMode probeMode) {
        this(intervalSeconds, maxHops, timeoutSeconds, RouteProbeFactory.create(probeMode));
    }

    MonitorService(double intervalSeconds, int maxHops, double timeoutSeconds, RouteProbe probe) {
        this.intervalSeconds = intervalSeconds;
        this.maxHops = maxHops;
        this.timeoutSeconds = timeoutSeconds;
        this.poller = new RoutePoller(probe);
        this.probePool = Executors.newFixedThreadPool(
                MAX_PARALLEL_PROBES,
                r -> {
                    Thread thread = new Thread(r, "pingui-probe");
                    thread.setDaemon(true);
                    return thread;
                });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "pingui-monitor");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::cycle, 0, (long) (intervalSeconds * 1000), TimeUnit.MILLISECONDS);
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
        if (active.isEmpty()) {
            return;
        }
        try {
            List<CompletableFuture<Void>> probes = new ArrayList<>();
            for (String host : active) {
                if (!running.get()) {
                    break;
                }
                probes.add(CompletableFuture.runAsync(() -> pollHost(host), probePool));
            }
            CompletableFuture.allOf(probes.toArray(CompletableFuture[]::new)).join();
        } catch (RuntimeException ex) {
            // Keep scheduler alive if a probe task fails unexpectedly.
        }
    }

    private void pollHost(String host) {
        if (!running.get()) {
            return;
        }
        List<String> previousIps;
        synchronized (lock) {
            if (!hosts.contains(host)) {
                return;
            }
            previousIps = List.copyOf(lastRoutes.getOrDefault(host, List.of()));
        }
        HostPollOutcome outcome = poller.pollHostRoute(host, previousIps, maxHops, timeoutSeconds);
        Listener current = listener;
        if (current == null || !isKnownHost(host)) {
            return;
        }
        if (outcome.error() != null) {
            current.onProbeError(host, outcome.error());
            return;
        }
        if (outcome.routeChanged()) {
            current.onRouteChanged(host, outcome.oldIps(), outcome.newIps());
        }
        synchronized (lock) {
            if (hosts.contains(host)) {
                lastRoutes.put(host, outcome.currentIps());
            }
        }
        if (outcome.snapshot() != null && isKnownHost(host)) {
            current.onDataReceived(host, outcome.snapshot());
        }
    }

    private boolean isKnownHost(String host) {
        synchronized (lock) {
            return hosts.contains(host);
        }
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdownNow();
        probePool.shutdownNow();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            probePool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

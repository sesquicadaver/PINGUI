package io.pingui.observability;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process Prometheus text exposition state (P15-010).
 *
 * <p>Thread-safe scrape snapshot for daemon {@code GET /metrics}. Not a remote_write client.
 */
public final class PrometheusExporter {
    private final ConcurrentHashMap<RttKey, Double> rttMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> routeChangeTotal = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> targetReachable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, Double> traceDurationMs = new ConcurrentHashMap<>();

    /** Records last-known RTT for a hop (gauge). */
    public void recordRtt(String host, int hop, double rttMilliseconds) {
        Objects.requireNonNull(host, "host");
        if (hop < 1) {
            throw new IllegalArgumentException("hop must be >= 1");
        }
        rttMs.put(new RttKey(host, hop), rttMilliseconds);
    }

    /** Removes RTT gauges for {@code host} before rewriting a shorter route. */
    public void clearHostRtt(String host) {
        Objects.requireNonNull(host, "host");
        rttMs.keySet().removeIf(key -> key.host().equals(host));
    }

    /** Increments route-change counter for {@code host} (real changes only). */
    public void incrementRouteChange(String host) {
        Objects.requireNonNull(host, "host");
        routeChangeTotal.computeIfAbsent(host, ignored -> new AtomicLong()).incrementAndGet();
    }

    /** Sets target reachability gauge ({@code 1.0} reachable, {@code 0.0} otherwise). */
    public void recordReachable(String host, boolean reachable) {
        Objects.requireNonNull(host, "host");
        targetReachable.put(host, reachable ? 1.0 : 0.0);
    }

    /** Records last poll duration for host + probe_mode (gauge). */
    public void recordTraceDuration(String host, String probeMode, double durationMilliseconds) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(probeMode, "probeMode");
        traceDurationMs.put(new DurationKey(host, probeMode), durationMilliseconds);
    }

    /** Renders Prometheus text exposition format 0.0.4. */
    public String scrape() {
        StringBuilder out = new StringBuilder(512);
        writeGaugeHeader(out, "pingui_rtt_ms", "Last known RTT in milliseconds");
        for (Map.Entry<RttKey, Double> entry : rttMs.entrySet()) {
            out.append("pingui_rtt_ms{host=\"")
                    .append(escapeLabel(entry.getKey().host()))
                    .append("\",hop=\"")
                    .append(entry.getKey().hop())
                    .append("\"} ")
                    .append(formatDouble(entry.getValue()))
                    .append('\n');
        }
        writeCounterHeader(out, "pingui_route_change_total", "Detected route-change count");
        for (Map.Entry<String, AtomicLong> entry : routeChangeTotal.entrySet()) {
            out.append("pingui_route_change_total{host=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\"} ")
                    .append(entry.getValue().get())
                    .append('\n');
        }
        writeGaugeHeader(out, "pingui_target_reachable", "Target reachable when hop IP matches targetIp (1 or 0)");
        for (Map.Entry<String, Double> entry : targetReachable.entrySet()) {
            out.append("pingui_target_reachable{host=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\"} ")
                    .append(formatDouble(entry.getValue()))
                    .append('\n');
        }
        writeGaugeHeader(out, "pingui_trace_duration_ms", "Last trace/mtr/ping duration in milliseconds");
        for (Map.Entry<DurationKey, Double> entry : traceDurationMs.entrySet()) {
            out.append("pingui_trace_duration_ms{host=\"")
                    .append(escapeLabel(entry.getKey().host()))
                    .append("\",probe_mode=\"")
                    .append(escapeLabel(entry.getKey().probeMode()))
                    .append("\"} ")
                    .append(formatDouble(entry.getValue()))
                    .append('\n');
        }
        return out.toString();
    }

    static String escapeLabel(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"");
    }

    private static String formatDouble(double value) {
        return Double.toString(value);
    }

    private static void writeGaugeHeader(StringBuilder out, String name, String help) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" gauge\n");
    }

    private static void writeCounterHeader(StringBuilder out, String name, String help) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" counter\n");
    }

    private record RttKey(String host, int hop) {}

    private record DurationKey(String host, String probeMode) {}
}

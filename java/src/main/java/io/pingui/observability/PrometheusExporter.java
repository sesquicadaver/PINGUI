package io.pingui.observability;

import io.pingui.dns.DnsOpsSnapshot;
import io.pingui.dns.DnsOpsStats;
import io.pingui.geoip.GeoIpOpsStats;
import io.pingui.telemetry.MetricNames;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * In-process Prometheus text exposition state (P15-010 / P34-007 / P35-005 / P36-010).
 *
 * <p>Thread-safe scrape snapshot for daemon {@code GET /metrics}. Not a remote_write client.
 */
public final class PrometheusExporter {
    private final ConcurrentHashMap<RttKey, Double> rttMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> routeChangeTotal = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> targetReachable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, Double> traceDurationMs = new ConcurrentHashMap<>();
    private volatile Supplier<DnsOpsSnapshot> dnsOpsSupplier;
    private volatile Supplier<GeoIpOpsStats> geoIpOpsSupplier;

    /** Optional live DNS queue counters for scrape (P34-007 / P35-005). */
    public void setDnsOpsSupplier(Supplier<DnsOpsSnapshot> dnsOpsSupplier) {
        this.dnsOpsSupplier = dnsOpsSupplier;
    }

    /** Optional live GeoIP enrichment counters for scrape (P36-010). */
    public void setGeoIpOpsSupplier(Supplier<GeoIpOpsStats> geoIpOpsSupplier) {
        this.geoIpOpsSupplier = geoIpOpsSupplier;
    }

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
        writeGaugeHeader(out, MetricNames.RTT_MS, "Last known RTT in milliseconds");
        for (Map.Entry<RttKey, Double> entry : rttMs.entrySet()) {
            out.append(MetricNames.RTT_MS)
                    .append("{host=\"")
                    .append(escapeLabel(entry.getKey().host()))
                    .append("\",hop=\"")
                    .append(entry.getKey().hop())
                    .append("\"} ")
                    .append(formatDouble(entry.getValue()))
                    .append('\n');
        }
        writeCounterHeader(out, MetricNames.ROUTE_CHANGE_TOTAL, "Detected route-change count");
        for (Map.Entry<String, AtomicLong> entry : routeChangeTotal.entrySet()) {
            out.append(MetricNames.ROUTE_CHANGE_TOTAL)
                    .append("{host=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\"} ")
                    .append(entry.getValue().get())
                    .append('\n');
        }
        writeGaugeHeader(out, MetricNames.TARGET_REACHABLE, "Target reachable when hop IP matches targetIp (1 or 0)");
        for (Map.Entry<String, Double> entry : targetReachable.entrySet()) {
            out.append(MetricNames.TARGET_REACHABLE)
                    .append("{host=\"")
                    .append(escapeLabel(entry.getKey()))
                    .append("\"} ")
                    .append(formatDouble(entry.getValue()))
                    .append('\n');
        }
        writeGaugeHeader(out, MetricNames.TRACE_DURATION_MS, "Last trace/mtr/ping duration in milliseconds");
        for (Map.Entry<DurationKey, Double> entry : traceDurationMs.entrySet()) {
            out.append(MetricNames.TRACE_DURATION_MS)
                    .append("{host=\"")
                    .append(escapeLabel(entry.getKey().host()))
                    .append("\",probe_mode=\"")
                    .append(escapeLabel(entry.getKey().probeMode()))
                    .append("\"} ")
                    .append(formatDouble(entry.getValue()))
                    .append('\n');
        }
        appendDnsOps(out);
        appendGeoIpOps(out);
        return out.toString();
    }

    private void appendDnsOps(StringBuilder out) {
        Supplier<DnsOpsSnapshot> supplier = dnsOpsSupplier;
        DnsOpsSnapshot snapshot = supplier != null ? supplier.get() : null;
        if (snapshot == null) {
            snapshot = DnsOpsSnapshot.empty();
        }
        DnsOpsStats resolve = snapshot.resolve();
        DnsOpsStats control = snapshot.control();
        writeCounterHeader(out, MetricNames.DNS_REJECTED_TOTAL, "DNS lookups rejected because the queue was full");
        out.append(MetricNames.DNS_REJECTED_TOTAL)
                .append(' ')
                .append(resolve.rejectedCount())
                .append('\n');
        writeCounterHeader(out, MetricNames.DNS_DROPPED_TOTAL, "DNS lookups dropped on queue overflow");
        out.append(MetricNames.DNS_DROPPED_TOTAL)
                .append(' ')
                .append(resolve.droppedCount())
                .append('\n');
        writeCounterHeader(out, MetricNames.DNS_COALESCED_TOTAL, "DNS lookups coalesced onto an in-flight resolve");
        out.append(MetricNames.DNS_COALESCED_TOTAL)
                .append(' ')
                .append(resolve.coalescedCount())
                .append('\n');
        writeCounterHeader(out, MetricNames.DNS_TIMEOUT_TOTAL, "DNS lookups that hit the hard timeout");
        out.append(MetricNames.DNS_TIMEOUT_TOTAL)
                .append(' ')
                .append(resolve.timeoutCount())
                .append('\n');
        writeGaugeHeader(out, MetricNames.DNS_QUEUE_DEPTH, "Current DNS resolve executor queue depth");
        out.append(MetricNames.DNS_QUEUE_DEPTH)
                .append(' ')
                .append(resolve.queued())
                .append('\n');
        writeGaugeHeader(out, MetricNames.DNS_QUEUE_CAPACITY, "DNS resolve executor queue capacity");
        out.append(MetricNames.DNS_QUEUE_CAPACITY)
                .append(' ')
                .append(resolve.queueCapacity())
                .append('\n');
        writeCounterHeader(
                out,
                MetricNames.DNS_CONTROL_REJECTED_TOTAL,
                "DNS-control observes rejected because the outer queue was full");
        out.append(MetricNames.DNS_CONTROL_REJECTED_TOTAL)
                .append(' ')
                .append(control.rejectedCount())
                .append('\n');
        writeCounterHeader(
                out, MetricNames.DNS_CONTROL_DROPPED_TOTAL, "DNS-control observes dropped on outer queue overflow");
        out.append(MetricNames.DNS_CONTROL_DROPPED_TOTAL)
                .append(' ')
                .append(control.droppedCount())
                .append('\n');
        writeCounterHeader(
                out, MetricNames.DNS_CONTROL_COALESCED_TOTAL, "DNS-control observes coalesced onto a pending host job");
        out.append(MetricNames.DNS_CONTROL_COALESCED_TOTAL)
                .append(' ')
                .append(control.coalescedCount())
                .append('\n');
        writeGaugeHeader(out, MetricNames.DNS_CONTROL_QUEUE_DEPTH, "Current DNS-control outer executor queue depth");
        out.append(MetricNames.DNS_CONTROL_QUEUE_DEPTH)
                .append(' ')
                .append(control.queued())
                .append('\n');
        writeGaugeHeader(out, MetricNames.DNS_CONTROL_QUEUE_CAPACITY, "DNS-control outer executor queue capacity");
        out.append(MetricNames.DNS_CONTROL_QUEUE_CAPACITY)
                .append(' ')
                .append(control.queueCapacity())
                .append('\n');
        writeGaugeHeader(out, MetricNames.DNS_CONTROL_PENDING, "Hosts with a pending or running DNS-control observe");
        out.append(MetricNames.DNS_CONTROL_PENDING)
                .append(' ')
                .append(control.inFlight())
                .append('\n');
    }

    private void appendGeoIpOps(StringBuilder out) {
        Supplier<GeoIpOpsStats> supplier = geoIpOpsSupplier;
        GeoIpOpsStats stats = supplier != null ? supplier.get() : null;
        if (stats == null) {
            stats = GeoIpOpsStats.empty();
        }
        writeCounterHeader(out, MetricNames.GEOIP_HITS_TOTAL, "GeoIP cache hits");
        out.append(MetricNames.GEOIP_HITS_TOTAL)
                .append(' ')
                .append(stats.hits())
                .append('\n');
        writeCounterHeader(out, MetricNames.GEOIP_MISSES_TOTAL, "GeoIP cache misses that triggered lookup");
        out.append(MetricNames.GEOIP_MISSES_TOTAL)
                .append(' ')
                .append(stats.misses())
                .append('\n');
        writeCounterHeader(out, MetricNames.GEOIP_UNKNOWNS_TOTAL, "GeoIP lookups that resolved to NONE");
        out.append(MetricNames.GEOIP_UNKNOWNS_TOTAL)
                .append(' ')
                .append(stats.unknowns())
                .append('\n');
        writeCounterHeader(out, MetricNames.GEOIP_ERRORS_TOTAL, "GeoIP lookup errors");
        out.append(MetricNames.GEOIP_ERRORS_TOTAL)
                .append(' ')
                .append(stats.errors())
                .append('\n');
        writeCounterHeader(out, MetricNames.GEOIP_REJECTED_TOTAL, "GeoIP offers rejected because the queue was full");
        out.append(MetricNames.GEOIP_REJECTED_TOTAL)
                .append(' ')
                .append(stats.rejected())
                .append('\n');
        writeCounterHeader(out, MetricNames.GEOIP_COALESCED_TOTAL, "GeoIP lookups coalesced onto an in-flight IP");
        out.append(MetricNames.GEOIP_COALESCED_TOTAL)
                .append(' ')
                .append(stats.coalesced())
                .append('\n');
        writeCounterHeader(out, MetricNames.GEOIP_RELOAD_FAILURES_TOTAL, "GeoIP MMDB reload failures");
        out.append(MetricNames.GEOIP_RELOAD_FAILURES_TOTAL)
                .append(' ')
                .append(stats.reloadFailures())
                .append('\n');
        writeGaugeHeader(out, MetricNames.GEOIP_CACHE_SIZE, "Current GeoIP LRU cache size");
        out.append(MetricNames.GEOIP_CACHE_SIZE)
                .append(' ')
                .append(stats.cacheSize())
                .append('\n');
        writeGaugeHeader(out, MetricNames.GEOIP_CACHE_CAPACITY, "GeoIP LRU cache capacity");
        out.append(MetricNames.GEOIP_CACHE_CAPACITY)
                .append(' ')
                .append(stats.cacheCapacity())
                .append('\n');
        writeGaugeHeader(out, MetricNames.GEOIP_QUEUE_DEPTH, "Current GeoIP enrichment executor queue depth");
        out.append(MetricNames.GEOIP_QUEUE_DEPTH)
                .append(' ')
                .append(stats.queueDepth())
                .append('\n');
        writeGaugeHeader(out, MetricNames.GEOIP_QUEUE_CAPACITY, "GeoIP enrichment executor queue capacity");
        out.append(MetricNames.GEOIP_QUEUE_CAPACITY)
                .append(' ')
                .append(stats.queueCapacity())
                .append('\n');
        writeGaugeHeader(out, MetricNames.GEOIP_PENDING, "In-flight GeoIP lookups");
        out.append(MetricNames.GEOIP_PENDING)
                .append(' ')
                .append(stats.pendingLookups())
                .append('\n');
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

package io.pingui.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Optional 5-minute avg/max RTT aggregates for remote LOG (P16-034 / ADR_TELEMETRY).
 *
 * <p>When {@code logAggregates} is {@code false} (default), the job is a no-op. When enabled, RTT hop
 * samples are bucketed into aligned windows and flushed as {@link TelemetryEvent#RTT_AGGREGATE}
 * events — so {@code events_only} LOG sinks still receive them, while raw high-freq samples stay
 * filtered. YAML {@code log_aggregates:} wiring is P16-040.
 */
public final class AggregateTelemetryJob {
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

    private final boolean logAggregates;
    private final Duration window;
    private final SinkRegistry registry;
    private final Clock clock;

    private final Object lock = new Object();
    /** windowStartEpochMs → (HopKey → accumulator). */
    private final Map<Long, Map<HopKey, Acc>> windows = new HashMap<>();

    public AggregateTelemetryJob(SinkRegistry registry, boolean logAggregates) {
        this(registry, logAggregates, DEFAULT_WINDOW, Clock.systemUTC());
    }

    public AggregateTelemetryJob(SinkRegistry registry, boolean logAggregates, Duration window, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.logAggregates = logAggregates;
        this.window = Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean logAggregates() {
        return logAggregates;
    }

    public Duration window() {
        return window;
    }

    /** Default-off job (programmatic stand-in for {@code log_aggregates: false}). */
    public static AggregateTelemetryJob disabled(SinkRegistry registry) {
        return new AggregateTelemetryJob(registry, false);
    }

    /** Enabled with the canonical 5-minute window ({@code log_aggregates: true}). */
    public static AggregateTelemetryJob enabled(SinkRegistry registry) {
        return new AggregateTelemetryJob(registry, true);
    }

    /**
     * Ingests one sample. Non-RTT or hop-less samples are ignored. No-op when disabled.
     */
    public void accept(MetricSample sample) {
        if (!logAggregates || sample == null) {
            return;
        }
        if (!MetricNames.RTT_MS.equals(sample.name()) || sample.hop() == null) {
            return;
        }
        Instant ts = sample.timestamp() != null ? sample.timestamp() : clock.instant();
        long windowStartMs = alignWindowStart(ts.toEpochMilli(), window.toMillis());
        HopKey key = new HopKey(sample.host(), sample.hop(), sample.labels());
        synchronized (lock) {
            Map<HopKey, Acc> bucket = windows.computeIfAbsent(windowStartMs, ignored -> new HashMap<>());
            Acc acc = bucket.computeIfAbsent(key, ignored -> new Acc());
            acc.add(sample.value());
        }
    }

    /**
     * Emits aggregates for every window that has fully closed strictly before {@code now}.
     *
     * @return number of {@link TelemetryEvent}s emitted
     */
    public int flushDue() {
        return flushDue(clock.instant());
    }

    public int flushDue(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!logAggregates) {
            return 0;
        }
        long nowMs = now.toEpochMilli();
        long windowMs = window.toMillis();
        List<TelemetryEvent> emit = new ArrayList<>();
        synchronized (lock) {
            Iterator<Map.Entry<Long, Map<HopKey, Acc>>> it = windows.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Map<HopKey, Acc>> entry = it.next();
                long startMs = entry.getKey();
                if (startMs + windowMs > nowMs) {
                    continue;
                }
                Instant windowStart = Instant.ofEpochMilli(startMs);
                Instant eventTs = Instant.ofEpochMilli(startMs + windowMs);
                for (Map.Entry<HopKey, Acc> hop : entry.getValue().entrySet()) {
                    emit.add(toEvent(hop.getKey(), hop.getValue(), windowStart, eventTs));
                }
                it.remove();
            }
        }
        for (TelemetryEvent event : emit) {
            registry.emitEvent(event);
        }
        return emit.size();
    }

    /**
     * Forces emit of all open windows (including incomplete current window). Useful for tests /
     * shutdown.
     */
    public int flushAll() {
        if (!logAggregates) {
            return 0;
        }
        List<TelemetryEvent> emit = new ArrayList<>();
        synchronized (lock) {
            for (Map.Entry<Long, Map<HopKey, Acc>> entry : windows.entrySet()) {
                Instant windowStart = Instant.ofEpochMilli(entry.getKey());
                Instant eventTs = Instant.ofEpochMilli(entry.getKey() + window.toMillis());
                for (Map.Entry<HopKey, Acc> hop : entry.getValue().entrySet()) {
                    emit.add(toEvent(hop.getKey(), hop.getValue(), windowStart, eventTs));
                }
            }
            windows.clear();
        }
        for (TelemetryEvent event : emit) {
            registry.emitEvent(event);
        }
        return emit.size();
    }

    int openWindowCount() {
        synchronized (lock) {
            return windows.size();
        }
    }

    static long alignWindowStart(long epochMs, long windowMs) {
        if (windowMs <= 0) {
            throw new IllegalArgumentException("windowMs must be positive");
        }
        long floor = Math.floorDiv(epochMs, windowMs);
        return Math.multiplyExact(floor, windowMs);
    }

    private TelemetryEvent toEvent(HopKey key, Acc acc, Instant windowStart, Instant eventTs) {
        String message = formatMessage(key.hop(), acc, windowStart);
        return TelemetryEvent.rttAggregate(key.host(), message, key.labels(), eventTs);
    }

    private String formatMessage(int hop, Acc acc, Instant windowStart) {
        double avg = acc.sum / acc.count;
        StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        sb.append("\"window_sec\":").append(window.getSeconds());
        sb.append(",\"window_start\":").append(TelemetryJson.quote(windowStart.toString()));
        sb.append(",\"hop\":").append(hop);
        sb.append(",\"avg_ms\":").append(formatDouble(avg));
        sb.append(",\"max_ms\":").append(formatDouble(acc.max));
        sb.append(",\"count\":").append(acc.count);
        sb.append(",\"metric\":").append(TelemetryJson.quote(MetricNames.RTT_MS));
        sb.append('}');
        return sb.toString();
    }

    private static String formatDouble(double value) {
        if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record HopKey(String host, int hop, Map<String, String> labels) {
        private HopKey {
            host = TelemetryJson.requireNonBlank(host, "host");
            if (hop < 1) {
                throw new IllegalArgumentException("hop must be >= 1");
            }
            labels = labels == null ? Map.of() : Map.copyOf(new TreeMap<>(labels));
        }
    }

    private static final class Acc {
        private long count;
        private double sum;
        private double max = Double.NEGATIVE_INFINITY;

        void add(double value) {
            if (!Double.isFinite(value)) {
                return;
            }
            count++;
            sum += value;
            if (value > max) {
                max = value;
            }
        }
    }
}

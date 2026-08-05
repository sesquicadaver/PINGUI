package io.pingui.telemetry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fan-out registry for {@link TelemetrySink} instances (P16-011 / ADR_TELEMETRY).
 *
 * <p>Empty registry is a silent no-op. Sink exceptions and per-call timeouts are logged, counted via
 * {@link #failureCount()}, and do not stop other sinks or the poll loop (P26-002).
 */
public final class SinkRegistry implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SinkRegistry.class.getName());

    /** Default per-sink call budget so one hanging sink cannot starve peers indefinitely. */
    public static final Duration DEFAULT_SINK_CALL_TIMEOUT = Duration.ofSeconds(5);

    private final ConcurrentHashMap<String, TelemetrySink> sinks = new ConcurrentHashMap<>();
    private final AtomicLong failureCount = new AtomicLong();
    private final Duration sinkCallTimeout;
    private final ExecutorService sinkCalls;

    public SinkRegistry() {
        this(DEFAULT_SINK_CALL_TIMEOUT);
    }

    /**
     * @param sinkCallTimeout max time for one sink {@code onSample}/{@code onEvent}; {@link
     *     Duration#ZERO} runs calls on the caller thread (tests that intentionally hang a sink).
     */
    public SinkRegistry(Duration sinkCallTimeout) {
        Objects.requireNonNull(sinkCallTimeout, "sinkCallTimeout");
        if (sinkCallTimeout.isNegative()) {
            throw new IllegalArgumentException("sinkCallTimeout must be >= 0");
        }
        this.sinkCallTimeout = sinkCallTimeout;
        this.sinkCalls = sinkCallTimeout.isZero()
                ? null
                : Executors.newCachedThreadPool(r -> {
                    Thread thread = new Thread(r, "pingui-telemetry-sink");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public Duration sinkCallTimeout() {
        return sinkCallTimeout;
    }

    /** Register or replace a sink by {@link TelemetrySink#id()}. Previous sink (if any) is closed. */
    public void register(TelemetrySink sink) {
        Objects.requireNonNull(sink, "sink");
        String id = Objects.requireNonNull(sink.id(), "sink.id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("sink.id must be non-blank");
        }
        TelemetrySink previous = sinks.put(id, sink);
        if (previous != null && previous != sink) {
            closeQuietly(previous);
        }
    }

    /** Remove sink by id; no-op if absent. Returns true if a sink was removed. */
    public boolean unregister(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        TelemetrySink removed = sinks.remove(id);
        if (removed != null) {
            closeQuietly(removed);
            return true;
        }
        return false;
    }

    public boolean contains(String id) {
        return id != null && sinks.containsKey(id);
    }

    public int size() {
        return sinks.size();
    }

    /**
     * Cumulative count of sink call failures (sample/event delivery, including call timeouts). Close
     * failures are logged but not counted here.
     */
    public long failureCount() {
        return failureCount.get();
    }

    /** Snapshot of registered ids (stable copy). */
    public List<String> ids() {
        return List.copyOf(sinks.keySet());
    }

    public void emitSample(MetricSample sample) {
        Objects.requireNonNull(sample, "sample");
        for (TelemetrySink sink : sinks.values()) {
            invokeSink(safeId(sink), () -> {
                if (sink.eventsOnly()) {
                    return;
                }
                sink.onSample(sample);
            });
        }
    }

    public void emitEvent(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        for (TelemetrySink sink : sinks.values()) {
            invokeSink(safeId(sink), () -> sink.onEvent(event));
        }
    }

    private void invokeSink(String sinkId, Runnable call) {
        if (sinkCalls == null) {
            try {
                call.run();
            } catch (RuntimeException ex) {
                failureCount.incrementAndGet();
                LOG.log(Level.WARNING, "Telemetry sink failed: " + sinkId, ex);
            }
            return;
        }
        Future<?> future = sinkCalls.submit(call);
        try {
            future.get(sinkCallTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            failureCount.incrementAndGet();
            LOG.log(Level.WARNING, "Telemetry sink timed out: " + sinkId, ex);
        } catch (ExecutionException ex) {
            failureCount.incrementAndGet();
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            LOG.log(Level.WARNING, "Telemetry sink failed: " + sinkId, cause);
        } catch (InterruptedException ex) {
            future.cancel(true);
            // Shutdown flush often runs on an interrupted bus worker — deliver sync so events are not lost.
            try {
                call.run();
            } catch (RuntimeException rex) {
                failureCount.incrementAndGet();
                LOG.log(Level.WARNING, "Telemetry sink failed after interrupt: " + sinkId, rex);
            }
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        List<TelemetrySink> snapshot = new ArrayList<>(sinks.values());
        sinks.clear();
        for (TelemetrySink sink : snapshot) {
            closeQuietly(sink);
        }
        if (sinkCalls != null) {
            sinkCalls.shutdownNow();
        }
    }

    private static String safeId(TelemetrySink sink) {
        try {
            String id = sink.id();
            return id == null || id.isBlank() ? "?" : id;
        } catch (RuntimeException ex) {
            return "?";
        }
    }

    private static void closeQuietly(TelemetrySink sink) {
        try {
            sink.close();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Telemetry sink close failed: " + safeId(sink), ex);
        }
    }
}

package io.pingui.telemetry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fan-out registry for {@link TelemetrySink} instances (P16-011 / ADR_TELEMETRY).
 *
 * <p>Empty registry is a silent no-op. Sink exceptions and per-call timeouts are logged, counted via
 * {@link #failureCount()}, and do not stop other sinks or the poll loop (P26-002 / P28-001).
 *
 * <p><b>Hang isolation (P28-001):</b> calls run on a <em>bounded</em> pool (not {@code
 * newCachedThreadPool}). A sink with an in-flight or still-hung call is skipped until that call
 * returns. Shutdown / interrupt does <em>not</em> sync-redispatch a hung sink onto the caller.
 */
public final class SinkRegistry implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SinkRegistry.class.getName());

    /** Default per-sink call budget so one hanging sink cannot starve peers indefinitely. */
    public static final Duration DEFAULT_SINK_CALL_TIMEOUT = Duration.ofSeconds(5);

    /** Fixed pool size for async sink calls (P28-001). */
    public static final int DEFAULT_SINK_POOL_SIZE = 8;

    private final ConcurrentHashMap<String, TelemetrySink> sinks = new ConcurrentHashMap<>();
    /** Per-sink gate: true while a call is running (including after caller-side timeout). */
    private final ConcurrentHashMap<String, AtomicBoolean> busyBySink = new ConcurrentHashMap<>();

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
        this(sinkCallTimeout, DEFAULT_SINK_POOL_SIZE);
    }

    /**
     * @param sinkPoolSize fixed thread-pool size when {@code sinkCallTimeout > 0}; ignored for zero
     *     timeout
     */
    public SinkRegistry(Duration sinkCallTimeout, int sinkPoolSize) {
        Objects.requireNonNull(sinkCallTimeout, "sinkCallTimeout");
        if (sinkCallTimeout.isNegative()) {
            throw new IllegalArgumentException("sinkCallTimeout must be >= 0");
        }
        if (sinkPoolSize < 1) {
            throw new IllegalArgumentException("sinkPoolSize must be >= 1");
        }
        this.sinkCallTimeout = sinkCallTimeout;
        this.sinkCalls = sinkCallTimeout.isZero() ? null : newBoundedSinkPool(sinkPoolSize);
    }

    private static ExecutorService newBoundedSinkPool(int poolSize) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(poolSize * 4),
                runnable -> {
                    Thread thread = new Thread(runnable, "pingui-telemetry-sink");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
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
        busyBySink.remove(id);
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
     * Cumulative count of sink call failures (sample/event delivery, including call timeouts and
     * busy/hung skips). Close failures are logged but not counted here.
     */
    public long failureCount() {
        return failureCount.get();
    }

    /** Package-visible for tests — whether a sink currently has an in-flight or hung call. */
    boolean isSinkBusy(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        AtomicBoolean busy = busyBySink.get(id);
        return busy != null && busy.get();
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
        AtomicBoolean busy = busyBySink.computeIfAbsent(sinkId, ignored -> new AtomicBoolean(false));
        if (!busy.compareAndSet(false, true)) {
            failureCount.incrementAndGet();
            LOG.log(Level.WARNING, "Telemetry sink skipped (busy/hung): " + sinkId);
            return;
        }
        if (sinkCalls == null) {
            try {
                call.run();
            } catch (RuntimeException ex) {
                failureCount.incrementAndGet();
                LOG.log(Level.WARNING, "Telemetry sink failed: " + sinkId, ex);
            } finally {
                busy.set(false);
            }
            return;
        }
        Future<?> future;
        try {
            future = sinkCalls.submit(() -> {
                try {
                    call.run();
                } finally {
                    busy.set(false);
                }
            });
        } catch (RejectedExecutionException ex) {
            busy.set(false);
            failureCount.incrementAndGet();
            LOG.log(Level.WARNING, "Telemetry sink rejected (pool full): " + sinkId, ex);
            return;
        }
        try {
            future.get(sinkCallTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            failureCount.incrementAndGet();
            LOG.log(Level.WARNING, "Telemetry sink timed out: " + sinkId, ex);
            // Leave busy=true until the hung task's finally clears it — no new calls, no sync retry.
        } catch (ExecutionException ex) {
            failureCount.incrementAndGet();
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            LOG.log(Level.WARNING, "Telemetry sink failed: " + sinkId, cause);
        } catch (InterruptedException ex) {
            // Future.get clears the interrupt flag when it throws. Wait once more so a healthy sink
            // can finish during bus shutdown; never sync-redispatch a hung call onto this thread.
            try {
                future.get(sinkCallTimeout.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException te) {
                future.cancel(true);
                failureCount.incrementAndGet();
                LOG.log(Level.WARNING, "Telemetry sink timed out after interrupt: " + sinkId, te);
            } catch (ExecutionException ee) {
                failureCount.incrementAndGet();
                Throwable cause = ee.getCause() == null ? ee : ee.getCause();
                LOG.log(Level.WARNING, "Telemetry sink failed: " + sinkId, cause);
            } catch (InterruptedException ie) {
                future.cancel(true);
            } finally {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        List<TelemetrySink> snapshot = new ArrayList<>(sinks.values());
        sinks.clear();
        busyBySink.clear();
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

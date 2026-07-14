package io.pingui.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fan-out registry for {@link TelemetrySink} instances (P16-011 / ADR_TELEMETRY).
 *
 * <p>Empty registry is a silent no-op. Sink exceptions are logged and do not stop other sinks.
 */
public final class SinkRegistry implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SinkRegistry.class.getName());

    private final ConcurrentHashMap<String, TelemetrySink> sinks = new ConcurrentHashMap<>();

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

    /** Snapshot of registered ids (stable copy). */
    public List<String> ids() {
        return List.copyOf(sinks.keySet());
    }

    public void emitSample(MetricSample sample) {
        Objects.requireNonNull(sample, "sample");
        for (TelemetrySink sink : sinks.values()) {
            String sinkId = "?";
            try {
                sinkId = safeId(sink);
                if (sink.eventsOnly()) {
                    continue;
                }
                sink.onSample(sample);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Telemetry sink sample failed: " + sinkId, ex);
            }
        }
    }

    public void emitEvent(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        for (TelemetrySink sink : sinks.values()) {
            String sinkId = "?";
            try {
                sinkId = safeId(sink);
                sink.onEvent(event);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Telemetry sink event failed: " + sinkId, ex);
            }
        }
    }

    @Override
    public void close() {
        List<TelemetrySink> snapshot = new ArrayList<>(sinks.values());
        sinks.clear();
        for (TelemetrySink sink : snapshot) {
            closeQuietly(sink);
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

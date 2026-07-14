package io.pingui.telemetry;

/**
 * Pluggable writer for telemetry samples/events (P16-011 / ADR_TELEMETRY).
 *
 * <p>Implementations must not throw into the poll loop; {@link SinkRegistry} also isolates failures.
 */
public interface TelemetrySink extends AutoCloseable {
    /** Stable registry key (unique per process). */
    String id();

    /** High-frequency samples (RTT, gauges). Skipped when {@link #eventsOnly()} is true. */
    void onSample(MetricSample sample);

    /** Rare events ({@code route_change}, {@code probe_error}, …). */
    void onEvent(TelemetryEvent event);

    /**
     * When true, the registry delivers only events (LOG sinks default; P16-033).
     */
    default boolean eventsOnly() {
        return false;
    }

    @Override
    default void close() {
        // no-op
    }

    /** Process-wide no-op sink (empty registry is also a no-op fan-out). */
    static TelemetrySink noop() {
        return NoOpTelemetrySink.INSTANCE;
    }
}

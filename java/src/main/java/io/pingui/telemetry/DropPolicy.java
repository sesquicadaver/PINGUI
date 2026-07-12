package io.pingui.telemetry;

/**
 * Backpressure policy when the telemetry queue is full (P16-012 / ADR_TELEMETRY).
 *
 * <p><b>DROP_OLDEST</b> (default): discard the head of the queue, then enqueue the new item. Prefer
 * fresher samples/events for NOC sinks.
 *
 * <p><b>DROP_NEWEST</b>: reject the incoming offer and keep queued items. Prefer preserving backlog
 * already accepted by the bus.
 *
 * <p>Every discarded item increments {@link TelemetryBus#droppedCount()}.
 */
public enum DropPolicy {
    DROP_OLDEST,
    DROP_NEWEST
}

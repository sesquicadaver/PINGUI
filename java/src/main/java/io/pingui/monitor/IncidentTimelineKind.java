package io.pingui.monitor;

/**
 * Kind of a compact incident-timeline row (P29-002).
 *
 * <p>{@link #DNS_CHANGE} is reserved until DNS control (P29-004) persists events.
 */
public enum IncidentTimelineKind {
    ENDPOINT_DOWN,
    LATENCY_HIGH,
    ROUTE_CHANGE,
    PROBLEM_ACK,
    DNS_CHANGE,
    PROBE_ERROR
}

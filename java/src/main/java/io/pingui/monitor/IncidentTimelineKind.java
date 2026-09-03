package io.pingui.monitor;

/**
 * Kind of a compact incident-timeline row (P29-002 / P29-004).
 *
 * <p>{@link #DNS_CHANGE} is emitted by forward DNS control (address set / NXDOMAIN / timeout /
 * SERVFAIL) — not an auto-incident.
 */
public enum IncidentTimelineKind {
    ENDPOINT_DOWN,
    LATENCY_HIGH,
    ROUTE_CHANGE,
    PROBLEM_ACK,
    DNS_CHANGE,
    PROBE_ERROR
}

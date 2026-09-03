package io.pingui.monitor;

/** Target reachability, independent of route tracing (P31-002). */
public enum EndpointState {
    UP,
    DEGRADED,
    DOWN,
    UNKNOWN
}

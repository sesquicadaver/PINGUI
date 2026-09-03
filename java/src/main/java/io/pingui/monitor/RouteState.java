package io.pingui.monitor;

/** Path-tracing status, independent of endpoint reachability (P31-002). */
public enum RouteState {
    STABLE,
    CHANGED,
    INCOMPLETE,
    NOT_TRACED
}

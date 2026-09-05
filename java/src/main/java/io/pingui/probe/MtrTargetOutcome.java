package io.pingui.probe;

/** Target reachability for the hop probed this MTR cycle (P32-001). */
public enum MtrTargetOutcome {
    /** This poll did not probe the destination hop. */
    NOT_SAMPLED,
    /** Destination hop was probed and answered. */
    REACHABLE,
    /** Destination hop was probed and timed out / unreachable. */
    UNREACHABLE
}

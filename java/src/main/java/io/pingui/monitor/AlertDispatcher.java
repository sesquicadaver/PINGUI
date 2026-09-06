package io.pingui.monitor;

/** Sends route-change and quality alerts to external channels (P10-011 / P21-002 / ADR_ALERTS). */
public interface AlertDispatcher extends AutoCloseable {
    void dispatch(RouteChangeEvent event);

    /** Optional quality rule emits ({@code endpoint_down}); default no-op. */
    default void dispatchQuality(QualityAlertEvent event) {}

    /**
     * Releases owned resources (webhook HTTP pools, etc.). Safe to call more than once (P33-006).
     * Default is a no-op for channels without lifecycle.
     */
    @Override
    default void close() {}

    static AlertDispatcher noop() {
        return NoOpAlertDispatcher.INSTANCE;
    }
}

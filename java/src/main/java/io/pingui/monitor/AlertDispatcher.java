package io.pingui.monitor;

/** Sends route-change and quality alerts to external channels (P10-011 / P21-002 / ADR_ALERTS). */
public interface AlertDispatcher {
    void dispatch(RouteChangeEvent event);

    /** Optional quality rule emits ({@code endpoint_down}); default no-op. */
    default void dispatchQuality(QualityAlertEvent event) {}

    static AlertDispatcher noop() {
        return NoOpAlertDispatcher.INSTANCE;
    }
}

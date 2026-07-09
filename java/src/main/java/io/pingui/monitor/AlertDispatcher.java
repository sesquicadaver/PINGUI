package io.pingui.monitor;

/** Sends route-change alerts to external channels (P10-011 / ADR_ALERTS). */
@FunctionalInterface
public interface AlertDispatcher {
    void dispatch(RouteChangeEvent event);

    static AlertDispatcher noop() {
        return NoOpAlertDispatcher.INSTANCE;
    }
}

package io.pingui.monitor;

/** Default alert dispatcher when alerts are disabled. */
enum NoOpAlertDispatcher implements AlertDispatcher {
    INSTANCE;

    @Override
    public void dispatch(RouteChangeEvent event) {
        // Alerts disabled.
    }
}

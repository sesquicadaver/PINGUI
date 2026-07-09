package io.pingui.ui;

import io.pingui.monitor.DesktopAlertDispatcher;
import io.pingui.monitor.RouteChangeEvent;

/** Manual smoke entry for desktop route-change notifications (P10-020). */
public final class RouteChangeNotifier {
    private static final DesktopAlertDispatcher DISPATCHER = new DesktopAlertDispatcher();

    private RouteChangeNotifier() {}

    /** Send a desktop notification for {@code event}; no-op when unsupported. */
    public static void notifyRouteChange(RouteChangeEvent event) {
        DISPATCHER.dispatch(event);
    }
}

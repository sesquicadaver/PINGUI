package io.pingui.ui;

import io.pingui.monitor.DesktopAlertDispatcher;
import io.pingui.monitor.RouteChangeEvent;

/** Manual smoke entry for desktop route-change popups (P10-020). */
public final class RouteChangeNotifier {
    private static final DesktopAlertDispatcher DISPATCHER =
            new DesktopAlertDispatcher(new JavaFxDesktopAlertSink());

    private RouteChangeNotifier() {}

    /** Show an in-app popup for {@code event}; no-op when JavaFX is unavailable. */
    public static void notifyRouteChange(RouteChangeEvent event) {
        DISPATCHER.dispatch(event);
    }
}

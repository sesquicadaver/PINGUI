package io.pingui.monitor;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desktop alert channel via an in-app popup sink (P10-020 / ADR_ALERTS).
 *
 * <p>Does not call {@code notify-send}, D-Bus, tray toasts, or other OS notification APIs.
 */
public final class DesktopAlertDispatcher implements AlertDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(DesktopAlertDispatcher.class);

    private final DesktopAlertSink sink;

    public DesktopAlertDispatcher() {
        this(DesktopAlertSink.noop());
    }

    public DesktopAlertDispatcher(DesktopAlertSink sink) {
        this.sink = Objects.requireNonNullElseGet(sink, DesktopAlertSink::noop);
    }

    @Override
    public void dispatch(RouteChangeEvent event) {
        if (event == null) {
            return;
        }
        String oldStr = event.oldIps().isEmpty() ? "(none)" : String.join(" -> ", event.oldIps());
        String newStr = event.newIps().isEmpty() ? "(none)" : String.join(" -> ", event.newIps());
        String body = event.host() + ": " + oldStr + " → " + newStr;
        show("PINGUI route change", body);
    }

    @Override
    public void dispatchQuality(QualityAlertEvent event) {
        if (event == null) {
            return;
        }
        show(event.desktopTitle(), event.desktopBody());
    }

    private void show(String title, String body) {
        try {
            sink.show(title, body);
        } catch (RuntimeException ex) {
            LOG.warn("Desktop alert popup failed: {}", ex.getMessage());
        }
    }
}

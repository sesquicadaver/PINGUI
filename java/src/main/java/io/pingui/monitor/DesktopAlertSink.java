package io.pingui.monitor;

/**
 * Presents a desktop alert as an in-app popup (no OS notification bus).
 *
 * <p>GUI wires a JavaFX implementation that keeps <strong>one popup per host</strong> and updates
 * it; daemon / tests use {@link #noop()} or a recording sink.
 */
@FunctionalInterface
public interface DesktopAlertSink {
    /**
     * Shows or updates a popup for {@code host}.
     *
     * @param host monitored target key (one window per host in the GUI sink)
     * @param title window title
     * @param body alert body text
     */
    void show(String host, String title, String body);

    static DesktopAlertSink noop() {
        return (host, title, body) -> {};
    }
}

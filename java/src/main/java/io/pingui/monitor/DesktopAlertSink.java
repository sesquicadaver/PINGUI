package io.pingui.monitor;

/**
 * Presents a desktop alert as an in-app popup (no OS notification bus).
 *
 * <p>GUI wires a JavaFX implementation; daemon / tests use {@link #noop()} or a recording sink.
 */
@FunctionalInterface
public interface DesktopAlertSink {
    void show(String title, String body);

    static DesktopAlertSink noop() {
        return (title, body) -> {};
    }
}

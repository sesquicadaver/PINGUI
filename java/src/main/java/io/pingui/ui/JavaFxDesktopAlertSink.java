package io.pingui.ui;

import io.pingui.monitor.DesktopAlertSink;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-app desktop alert popup (JavaFX {@link Alert}), without OS notification buses
 * ({@code notify-send}, D-Bus, tray toasts).
 */
public final class JavaFxDesktopAlertSink implements DesktopAlertSink {
    private static final Logger LOG = LoggerFactory.getLogger(JavaFxDesktopAlertSink.class);

    private final Supplier<Window> ownerSupplier;

    public JavaFxDesktopAlertSink() {
        this((Window) null);
    }

    public JavaFxDesktopAlertSink(Window owner) {
        this(() -> owner);
    }

    public JavaFxDesktopAlertSink(Supplier<Window> ownerSupplier) {
        this.ownerSupplier = ownerSupplier != null ? ownerSupplier : () -> null;
    }

    @Override
    public void show(String title, String body) {
        String safeTitle = title != null ? title : "PINGUI";
        String safeBody = body != null ? body : "";
        try {
            if (Platform.isFxApplicationThread()) {
                showOnFxThread(safeTitle, safeBody);
            } else {
                Platform.runLater(() -> showOnFxThread(safeTitle, safeBody));
            }
        } catch (IllegalStateException ex) {
            LOG.debug("JavaFX toolkit not ready; skipping desktop alert popup");
        }
    }

    private void showOnFxThread(String title, String body) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, body, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.initModality(Modality.NONE);
        Window owner = ownerSupplier.get();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setResizable(true);
        alert.show();
    }
}

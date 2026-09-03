package io.pingui.ui;

import io.pingui.monitor.DesktopAlertSink;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-app desktop alert popup (JavaFX {@link Stage}), without OS notification buses
 * ({@code notify-send}, D-Bus, tray toasts).
 *
 * <p>Keeps <strong>one open window per monitored host</strong>: subsequent alerts for the same
 * endpoint update title/body and bring the existing window to front instead of spawning another.
 */
public final class JavaFxDesktopAlertSink implements DesktopAlertSink {
    private static final Logger LOG = LoggerFactory.getLogger(JavaFxDesktopAlertSink.class);

    private final Supplier<Window> ownerSupplier;
    /** When true, stages are created but not {@code show()}n (Monocle-safe unit tests). */
    private final boolean deferNativeShow;
    /** FX-thread map: host key → open popup stage + body label. */
    private final Map<String, HostPopup> openByHost = new ConcurrentHashMap<>();

    public JavaFxDesktopAlertSink() {
        this((Window) null);
    }

    public JavaFxDesktopAlertSink(Window owner) {
        this(() -> owner, false);
    }

    public JavaFxDesktopAlertSink(Supplier<Window> ownerSupplier) {
        this(ownerSupplier, false);
    }

    /** Test constructor: skip native {@link Stage#show()} under headless Monocle. */
    JavaFxDesktopAlertSink(Supplier<Window> ownerSupplier, boolean deferNativeShow) {
        this.ownerSupplier = ownerSupplier != null ? ownerSupplier : () -> null;
        this.deferNativeShow = deferNativeShow;
    }

    static JavaFxDesktopAlertSink forTests() {
        return new JavaFxDesktopAlertSink(() -> null, true);
    }

    @Override
    public void show(String host, String title, String body) {
        String safeHost = host == null || host.isBlank() ? "_" : host.strip();
        String safeTitle = title != null ? title : "PINGUI";
        String safeBody = body != null ? body : "";
        try {
            if (Platform.isFxApplicationThread()) {
                showOnFxThread(safeHost, safeTitle, safeBody);
            } else {
                Platform.runLater(() -> showOnFxThread(safeHost, safeTitle, safeBody));
            }
        } catch (IllegalStateException ex) {
            LOG.debug("JavaFX toolkit not ready; skipping desktop alert popup");
        }
    }

    /** Test hook: number of tracked per-host windows. */
    int trackedHostCountForTests() {
        return openByHost.size();
    }

    /** Test hook: stage currently tracked for {@code host}, or {@code null}. */
    Stage openStageForTests(String host) {
        HostPopup popup = openByHost.get(host);
        return popup == null ? null : popup.stage();
    }

    /** Test hook: body text shown for {@code host}. */
    String openBodyForTests(String host) {
        HostPopup popup = openByHost.get(host);
        return popup == null ? null : popup.bodyLabel().getText();
    }

    /** Test hook: drop tracked popup without native hide. */
    void closeForTests(String host) {
        HostPopup popup = openByHost.remove(host);
        if (popup != null) {
            popup.markClosed();
        }
    }

    private void showOnFxThread(String host, String title, String body) {
        HostPopup existing = openByHost.get(host);
        if (existing != null && existing.isOpen()) {
            existing.stage().setTitle(title);
            existing.bodyLabel().setText(body);
            if (!deferNativeShow && existing.stage().isShowing()) {
                existing.stage().toFront();
            }
            return;
        }
        if (existing != null) {
            openByHost.remove(host, existing);
        }
        HostPopup popup = createPopup(host, title, body);
        openByHost.put(host, popup);
        popup.markOpen();
        if (!deferNativeShow) {
            popup.stage().show();
        }
    }

    private HostPopup createPopup(String host, String title, String body) {
        Stage stage = new Stage(StageStyle.UTILITY);
        stage.initModality(Modality.NONE);
        Window owner = ownerSupplier.get();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(title);
        stage.setResizable(true);
        stage.setMinWidth(320);
        stage.setMinHeight(120);

        Label bodyLabel = new Label(body);
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxWidth(420);
        bodyLabel.setPadding(new Insets(12));

        Button close = new Button("OK");
        close.setDefaultButton(true);
        close.setOnAction(event -> stage.hide());
        HBox buttons = new HBox(close);
        buttons.setPadding(new Insets(0, 12, 12, 12));

        BorderPane root = new BorderPane();
        root.setCenter(bodyLabel);
        root.setBottom(buttons);
        stage.setScene(new Scene(root));
        HostPopup popup = new HostPopup(stage, bodyLabel);
        stage.setOnHidden(event -> {
            HostPopup current = openByHost.get(host);
            if (current != null && current.stage() == stage) {
                current.markClosed();
                openByHost.remove(host);
            }
        });
        return popup;
    }

    private static final class HostPopup {
        private final Stage stage;
        private final Label bodyLabel;
        private final AtomicBoolean open = new AtomicBoolean();

        HostPopup(Stage stage, Label bodyLabel) {
            this.stage = stage;
            this.bodyLabel = bodyLabel;
        }

        Stage stage() {
            return stage;
        }

        Label bodyLabel() {
            return bodyLabel;
        }

        boolean isOpen() {
            return open.get();
        }

        void markOpen() {
            open.set(true);
        }

        void markClosed() {
            open.set(false);
        }
    }
}

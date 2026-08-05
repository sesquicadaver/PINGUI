package io.pingui.ui;

import io.pingui.AppInfo;
import io.pingui.i18n.UiI18n;
import io.pingui.platform.PlatformCapabilities;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** About and Help dialogs for the main window menu bar. */
public final class AppMenuDialogs {
    private static HostServices hostServices;

    private enum InfoDialogKind {
        ABOUT(460),
        HELP(560);

        private final double prefWidth;

        InfoDialogKind(double prefWidth) {
            this.prefWidth = prefWidth;
        }
    }

    private AppMenuDialogs() {}

    /** Called from {@link io.pingui.PinguiApplication#start} — do not use AWT Desktop. */
    public static void bindHostServices(HostServices services) {
        hostServices = services;
    }

    public static void showAbout(Window owner) {
        Alert alert = baseAlert(owner, UiI18n.get("about.title"), InfoDialogKind.ABOUT);
        alert.setHeaderText(UiI18n.get("about.header", AppInfo.NAME, AppInfo.EDITION));

        Label version = new Label(UiI18n.get("about.version", AppInfo.versionDetail()));
        Label runtime = new Label(UiI18n.get("about.runtime", AppInfo.runtimeJavaVersion(), AppInfo.runtimeOsName()));
        runtime.setStyle("-fx-text-fill: #555;");

        Label summary = new Label(aboutSummary());
        summary.setWrapText(true);

        HBox linkRow = new HBox(4, new Label(UiI18n.get("about.repository")), repositoryLink());
        linkRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, version, runtime, summary, linkRow);
        content.setPadding(new Insets(8, 0, 0, 0));
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    public static void showHelp(Window owner) {
        Alert alert = baseAlert(owner, UiI18n.get("help.title"), InfoDialogKind.HELP);
        alert.setHeaderText(UiI18n.get("help.header"));

        TextArea body = new TextArea(helpText());
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(18);

        HBox docRow = new HBox(4, new Label(UiI18n.get("help.documentation")), repositoryLink());
        docRow.setAlignment(Pos.CENTER_LEFT);
        docRow.setPadding(new Insets(8, 0, 0, 0));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);

        VBox content = new VBox(scroll, docRow);
        content.setPadding(new Insets(8, 0, 0, 0));
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    private static Alert baseAlert(Window owner, String title, InfoDialogKind kind) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        alert.setTitle(title);
        alert.initOwner(owner);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.getDialogPane().setPrefWidth(kind.prefWidth);
        if (alert.getDialogPane().lookupButton(ButtonType.OK) instanceof Button close) {
            close.setText(UiI18n.get("dialog.close"));
        }
        return alert;
    }

    private static Hyperlink repositoryLink() {
        Hyperlink link = new Hyperlink(AppInfo.REPOSITORY);
        link.setOnAction(event -> {
            event.consume();
            openRepository();
        });
        return link;
    }

    /** About body (unit-tested; P16-094 / P25). */
    static String aboutSummary() {
        return UiI18n.get("about.summary");
    }

    /** Help body (unit-tested; P16-094 / P25). */
    static String helpText() {
        String expert = PlatformCapabilities.expertPingSupported()
                ? UiI18n.get("help.expert_linux")
                : UiI18n.get("help.expert_unavailable");
        return UiI18n.get("help.body", expert, AppAccelerators.helpSection());
    }

    private static void openRepository() {
        String url = AppInfo.REPOSITORY;
        if (hostServices != null) {
            hostServices.showDocument(url);
            return;
        }
        Thread.ofVirtual().name("pingui-open-url").start(() -> launchBrowserProcess(url));
    }

    private static void launchBrowserProcess(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder launcher;
            if (os.contains("win")) {
                launcher = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                launcher = new ProcessBuilder("open", url);
            } else {
                launcher = new ProcessBuilder("xdg-open", url);
            }
            launcher.inheritIO()
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD);
            launcher.start();
        } catch (Exception ignored) {
            // URL remains visible in Hyperlink for manual copy.
        }
    }
}

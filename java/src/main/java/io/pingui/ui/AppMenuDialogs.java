package io.pingui.ui;

import io.pingui.AppInfo;
import io.pingui.platform.PlatformCapabilities;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** About and Help dialogs for the main window menu bar. */
public final class AppMenuDialogs {
    private AppMenuDialogs() {}

    public static void showAbout() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Про PINGUI");
        dialog.setHeaderText(AppInfo.NAME + " — сесійний монітор маршрутів (" + AppInfo.EDITION + ")");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(460);

        Label version = new Label("Версія " + AppInfo.version());
        Label runtime =
                new Label(
                        "Java "
                                + AppInfo.runtimeJavaVersion()
                                + " · "
                                + AppInfo.runtimeOsName());
        runtime.setStyle("-fx-text-fill: #555;");

        TextFlow linkFlow = new TextFlow();
        Text prefix = new Text("Репозиторій: ");
        Text link = new Text(AppInfo.REPOSITORY);
        link.setStyle("-fx-fill: #1565c0; -fx-underline: true;");
        link.setOnMouseClicked(e -> openRepository());
        linkFlow.getChildren().addAll(prefix, link);

        Label summary =
                new Label(
                        "Монітор RTT і маршрутів до 10 цілей одночасно. "
                                + "Дані сесії зберігаються лише в RAM.");
        summary.setWrapText(true);

        VBox content = new VBox(10, version, runtime, summary, linkFlow);
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    public static void showHelp() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Довідка PINGUI");
        dialog.setHeaderText("Коротка довідка");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(560);

        TextArea body = new TextArea(helpText());
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(18);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);
        scroll.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private static String helpText() {
        String expert =
                PlatformCapabilities.expertPingSupported()
                        ? "Експерт (Linux) — кнопка Exten. задає параметри ping(8) iputils."
                        : "Експерт недоступний на цій ОС (лише Linux, iputils ping).";
        return """
                Профілі та цілі
                • Кілька профілів трасування в одному YAML; перемикання — «Профіль».
                • До 10 IP або hostname; чекбокс увімкнює моніторинг хоста.
                • Ping only — лише RTT до цілі без traceroute (рекомендовано на Windows).

                Режими інтерфейсу
                • Простий — компактний список із метриками RTT і loss %%.
                • Розширений — граф hop-ів і журнал змін маршруту.
                • %s

                Кнопки
                • Додати / Змінити / Видалити — цілі в поточному профілі.
                • Зберегти — запис усіх профілів у YAML (--config).

                CLI (термінал)
                • ./pingui-java.sh [--config PATH] [--interval SEC] [--max-hops N] …
                • --help — повний список опцій.

                Платформа
                • Linux — рекомендована ОС (швидкий traceroute, Expert ping).
                • Windows — повний trace через tracert може тривати хвилини; Ping only або interval ≥ 30 с.

                Документація: %s
                """
                .formatted(expert, AppInfo.REPOSITORY);
    }

    private static void openRepository() {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(AppInfo.REPOSITORY));
            }
        } catch (Exception ignored) {
            // No browser handler — link remains visible in the dialog.
        }
    }
}

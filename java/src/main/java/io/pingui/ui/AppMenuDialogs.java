package io.pingui.ui;

import io.pingui.AppInfo;
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

    private AppMenuDialogs() {}

    /** Called from {@link io.pingui.PinguiApplication#start} — do not use AWT Desktop. */
    public static void bindHostServices(HostServices services) {
        hostServices = services;
    }

    public static void showAbout(Window owner) {
        Alert alert = baseAlert(owner, "Про PINGUI");
        alert.setHeaderText(AppInfo.NAME + " — сесійний монітор маршрутів (" + AppInfo.EDITION + ")");

        Label version = new Label("Версія " + AppInfo.versionDetail());
        Label runtime = new Label("Java " + AppInfo.runtimeJavaVersion() + " · " + AppInfo.runtimeOsName());
        runtime.setStyle("-fx-text-fill: #555;");

        Label summary = new Label(aboutSummary());
        summary.setWrapText(true);

        HBox linkRow = new HBox(4, new Label("Репозиторій:"), repositoryLink());
        linkRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, version, runtime, summary, linkRow);
        content.setPadding(new Insets(8, 0, 0, 0));
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    public static void showHelp(Window owner) {
        Alert alert = baseAlert(owner, "Довідка PINGUI");
        alert.setHeaderText("Коротка довідка");

        TextArea body = new TextArea(helpText());
        body.setEditable(false);
        body.setWrapText(true);
        body.setPrefRowCount(18);

        HBox docRow = new HBox(4, new Label("Документація:"), repositoryLink());
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

    private static Alert baseAlert(Window owner, String title) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        alert.setTitle(title);
        alert.initOwner(owner);
        alert.initModality(Modality.WINDOW_MODAL);
        alert.getDialogPane().setPrefWidth(title.startsWith("Довідка") ? 560 : 460);
        if (alert.getDialogPane().lookupButton(ButtonType.OK) instanceof Button close) {
            close.setText("Закрити");
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

    /** About body (unit-tested; P16-094). */
    static String aboutSummary() {
        return "Монітор RTT і маршрутів до 10 цілей (IPv4/IPv6 literal або hostname). "
                + "Сесія: RAM або SQLite (Налаштування → База даних…). "
                + "Телеметрія / LOG sinks: Налаштування → Телеметрія… (YAML telemetry:).";
    }

    /** Help body (unit-tested; P16-094). */
    static String helpText() {
        String expert = PlatformCapabilities.expertPingSupported()
                ? "Експерт (Linux) — кнопка Exten. задає параметри ping(8) iputils."
                : "Експерт недоступний на цій ОС (лише Linux, iputils ping).";
        return """
                Профілі та цілі
                • Кілька профілів трасування в одному YAML; перемикання — «Профіль».
                • До 10 IP (IPv4/IPv6 literal) або hostname; чекбокс увімкнює моніторинг хоста.
                • IPv6 literal — trace через traceroute -6; на Linux з raw ICMP — auto fallback на process.
                • Ping only — лише RTT до цілі без traceroute (рекомендовано на Windows).

                Режими інтерфейсу
                • Простий — компактний список із метриками RTT і loss %%.
                • Розширений — граф hop-ів і журнал змін маршруту.
                • %s

                Налаштування
                • База даних… — SQLite session (історія route_change), не telemetry archive.
                • Телеметрія… — sinks (sqlite/jsonl/syslog/GELF/Loki/OTLP), events_only; Apply + «Зберегти».
                • Експорт зараз… — CSV/HTML звіт сесії (як CLI --export-report); потрібен SQLite session.
                • persistence.session_db ≠ telemetry.sqlite (різні ролі).

                Expert ping (Linux)
                • Експерт → Exten. / MTU — пресети, MTU wizard і Self-check (DF/DSCP/Burst → Alert).
                • «MTU probe» пресет ≠ перебір MTU: кнопка MTU / «MTU wizard…» (sweep -s + -M do → Apply).
                • Self-check — короткий batch пресетів DF/DSCP/Burst; не змінює форму Expert.

                Кнопки
                • Додати / Змінити / Видалити — цілі в поточному профілі.
                • Зберегти — запис усіх профілів у YAML (--config), включно з telemetry:.

                %s
                CLI (термінал)
                • ./pingui-java.sh [--config PATH] [--interval SEC] …
                • --interval / --max-hops / --timeout / --probe перезаписують YAML лише якщо передані.
                • --telemetry-syslog / --telemetry-jsonl / --telemetry-otlp — override sinks.
                • --help — повний список опцій.

                Платформа
                • Linux — рекомендована ОС (швидкий traceroute, Expert ping).
                • Windows — повний trace через tracert може тривати хвилини; Ping only або interval ≥ 30 с.
                """
                .formatted(expert, AppAccelerators.helpSection());
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

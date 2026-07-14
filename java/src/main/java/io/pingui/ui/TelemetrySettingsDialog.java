package io.pingui.ui;

import io.pingui.CliTelemetryOverrides;
import io.pingui.config.ConfigError;
import io.pingui.config.TelemetryConfig;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Minimal GUI for profile {@code telemetry:} (P16-091): events_only, local sqlite/jsonl, syslog.
 *
 * <p>GELF/Loki/OTLP/`log_aggregates` are preserved from the baseline config until P16-092.
 */
public final class TelemetrySettingsDialog {
    private TelemetrySettingsDialog() {}

    /** Result from the telemetry settings dialog. */
    public record Result(TelemetryConfig telemetry) {}

    /**
     * Shows telemetry settings and invokes {@code onApply} on successful Apply.
     *
     * @param baseline current effective {@link TelemetryConfig} (YAML ± CLI already merged)
     * @param cliLocks CLI fields that must not be edited in the dialog
     */
    public static void show(
            Window owner, TelemetryConfig baseline, CliTelemetryOverrides cliLocks, Consumer<Result> onApply) {
        TelemetryConfig current = baseline != null ? baseline : TelemetryConfig.defaults();
        CliTelemetryOverrides locks = cliLocks != null ? cliLocks : CliTelemetryOverrides.none();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Телеметрія");
        dialog.setHeaderText("Локальні sinks і syslog (інші remote sinks — у YAML до P16-092)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        CheckBox eventsOnlyCheck = new CheckBox("Лише events у remote LOG (events_only)");
        eventsOnlyCheck.setSelected(current.eventsOnly());
        eventsOnlyCheck.setTooltip(new Tooltip("Уникає high-freq RTT у syslog/GELF/Loki/OTLP logs"));

        TextField sqliteField =
                new TextField(current.sqlitePath().map(Path::toString).orElse(""));
        sqliteField.setPromptText("вимкнено (порожньо)");
        Button sqliteBrowse = new Button("Обрати…");
        sqliteBrowse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Файл telemetry SQLite");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite (*.db)", "*.db"));
            java.io.File chosen = chooser.showSaveDialog(owner);
            if (chosen != null) {
                sqliteField.setText(chosen.getPath());
            }
        });

        TextField jsonlField =
                new TextField(current.jsonlDir().map(Path::toString).orElse(""));
        jsonlField.setPromptText("вимкнено (порожньо)");
        boolean jsonlLocked = locks.jsonlDir().isPresent();
        jsonlField.setDisable(jsonlLocked);
        Button jsonlBrowse = new Button("Обрати…");
        jsonlBrowse.setDisable(jsonlLocked);
        if (jsonlLocked) {
            jsonlField.setTooltip(new Tooltip("Заблоковано CLI (--telemetry-jsonl)"));
        }
        jsonlBrowse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Каталог JSONL");
            java.io.File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                jsonlField.setText(chosen.getPath());
            }
        });

        TextField syslogField = new TextField(formatSyslog(current.syslog()));
        syslogField.setPromptText("HOST:PORT або порожньо");
        CheckBox syslogTlsCheck = new CheckBox("TLS");
        syslogTlsCheck.setSelected(
                current.syslog().map(TelemetryConfig.SyslogSinkConfig::tls).orElse(false));
        boolean syslogLocked = locks.syslog().isPresent();
        syslogField.setDisable(syslogLocked);
        syslogTlsCheck.setDisable(syslogLocked);
        if (syslogLocked) {
            syslogField.setTooltip(new Tooltip("Заблоковано CLI (--telemetry-syslog)"));
        }

        Label hint = new Label("Застосувати оновлює активний профіль і перепідключає sinks. "
                + "«Зберегти» у головному вікні записує YAML на диск. "
                + "GELF/Loki/OTLP з профілю зберігаються.");
        hint.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("SQLite:"), 0, 0);
        HBox sqliteRow = new HBox(8, sqliteField, sqliteBrowse);
        HBox.setHgrow(sqliteField, Priority.ALWAYS);
        grid.add(sqliteRow, 1, 0);
        grid.add(new Label("JSONL dir:"), 0, 1);
        HBox jsonlRow = new HBox(8, jsonlField, jsonlBrowse);
        HBox.setHgrow(jsonlField, Priority.ALWAYS);
        grid.add(jsonlRow, 1, 1);
        grid.add(new Label("Syslog:"), 0, 2);
        HBox syslogRow = new HBox(8, syslogField, syslogTlsCheck);
        HBox.setHgrow(syslogField, Priority.ALWAYS);
        grid.add(syslogRow, 1, 2);

        VBox content = new VBox(10, eventsOnlyCheck, grid, hint);
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.APPLY) {
            return;
        }

        try {
            TelemetryConfig next = buildConfig(
                    current,
                    eventsOnlyCheck.isSelected(),
                    sqliteField.getText(),
                    jsonlField.getText(),
                    syslogField.getText(),
                    syslogTlsCheck.isSelected(),
                    locks);
            onApply.accept(new Result(next));
        } catch (ConfigError | IllegalArgumentException ex) {
            Alert error = new Alert(Alert.AlertType.WARNING);
            error.initOwner(owner);
            error.setTitle("Телеметрія");
            error.setHeaderText("Некоректні налаштування");
            error.setContentText(ex.getMessage());
            error.showAndWait();
        }
    }

    /**
     * Builds the next {@link TelemetryConfig} from dialog fields (unit-tested). Preserves
     * gelf/loki/otlp/`log_aggregates` from {@code baseline}. CLI-locked sinks keep baseline values.
     */
    static TelemetryConfig buildConfig(
            TelemetryConfig baseline,
            boolean eventsOnly,
            String sqliteText,
            String jsonlText,
            String syslogText,
            boolean syslogTls,
            CliTelemetryOverrides locks) {
        TelemetryConfig base = baseline != null ? baseline : TelemetryConfig.defaults();
        CliTelemetryOverrides cli = locks != null ? locks : CliTelemetryOverrides.none();

        Optional<Path> sqlite = parseOptionalPath(sqliteText);
        Optional<Path> jsonl = cli.jsonlDir().isPresent() ? base.jsonlDir() : parseOptionalPath(jsonlText);
        Optional<TelemetryConfig.SyslogSinkConfig> syslog =
                cli.syslog().isPresent() ? base.syslog() : parseSyslog(syslogText, syslogTls);

        return new TelemetryConfig(
                eventsOnly, base.logAggregates(), sqlite, jsonl, syslog, base.gelf(), base.loki(), base.otlp());
    }

    static Optional<Path> parseOptionalPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(raw.strip()));
    }

    static Optional<TelemetryConfig.SyslogSinkConfig> parseSyslog(String raw, boolean tls) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            TelemetryConfig.SyslogSinkConfig parsed = CliTelemetryOverrides.parseSyslogHostPort(raw.strip());
            return Optional.of(new TelemetryConfig.SyslogSinkConfig(parsed.host(), parsed.port(), tls));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Syslog: вкажіть HOST:PORT або [IPv6]:PORT", ex);
        }
    }

    static String formatSyslog(Optional<TelemetryConfig.SyslogSinkConfig> syslog) {
        if (syslog == null || syslog.isEmpty()) {
            return "";
        }
        TelemetryConfig.SyslogSinkConfig s = syslog.get();
        String host = s.host();
        if (host.contains(":")) {
            return "[" + host + "]:" + s.port();
        }
        return host + ":" + s.port();
    }
}

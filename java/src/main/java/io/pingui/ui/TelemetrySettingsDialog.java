package io.pingui.ui;

import io.pingui.CliTelemetryOverrides;
import io.pingui.config.ConfigError;
import io.pingui.config.TelemetryConfig;
import io.pingui.i18n.UiI18n;
import io.pingui.telemetry.GelfSink;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
 * GUI for profile {@code telemetry:} (P16-091/092): local sinks, remote LOG sinks, policy flags,
 * and a redacted status summary.
 */
public final class TelemetrySettingsDialog {
    private TelemetrySettingsDialog() {}

    /** Result from the telemetry settings dialog. */
    public record Result(TelemetryConfig telemetry) {}

    /** Dialog field snapshot used by {@link #buildConfig} (unit-tested). */
    public record FormInput(
            boolean eventsOnly,
            boolean logAggregates,
            String sqliteText,
            String jsonlText,
            String syslogText,
            boolean syslogTls,
            String gelfText,
            String gelfTransport,
            String lokiUrl,
            String lokiSite,
            String otlpEndpoint,
            String otlpServiceName) {}

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
        dialog.setTitle(UiI18n.get("telemetry.title"));
        dialog.setHeaderText(UiI18n.get("telemetry.header"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        CheckBox eventsOnlyCheck = new CheckBox(UiI18n.get("telemetry.events_only"));
        eventsOnlyCheck.setSelected(current.eventsOnly());
        eventsOnlyCheck.setTooltip(new Tooltip(UiI18n.get("telemetry.events_only_tooltip")));

        CheckBox logAggregatesCheck = new CheckBox(UiI18n.get("telemetry.log_aggregates"));
        logAggregatesCheck.setSelected(current.logAggregates());
        logAggregatesCheck.setTooltip(new Tooltip(UiI18n.get("telemetry.log_aggregates_tooltip")));

        TextField sqliteField =
                new TextField(current.sqlitePath().map(Path::toString).orElse(""));
        sqliteField.setPromptText(UiI18n.get("telemetry.disabled_prompt"));
        Button sqliteBrowse = browseFile(owner, sqliteField, UiI18n.get("telemetry.sqlite_chooser"));

        TextField jsonlField =
                new TextField(current.jsonlDir().map(Path::toString).orElse(""));
        jsonlField.setPromptText(UiI18n.get("telemetry.disabled_prompt"));
        boolean jsonlLocked = locks.jsonlDir().isPresent();
        jsonlField.setDisable(jsonlLocked);
        Button jsonlBrowse = browseDirectory(owner, jsonlField, UiI18n.get("telemetry.jsonl_chooser"));
        jsonlBrowse.setDisable(jsonlLocked);
        if (jsonlLocked) {
            jsonlField.setTooltip(new Tooltip(UiI18n.get("telemetry.jsonl_locked")));
        }

        TextField syslogField = new TextField(formatSyslog(current.syslog()));
        syslogField.setPromptText(UiI18n.get("telemetry.host_port_prompt"));
        CheckBox syslogTlsCheck = new CheckBox(UiI18n.get("telemetry.tls"));
        syslogTlsCheck.setSelected(
                current.syslog().map(TelemetryConfig.SyslogSinkConfig::tls).orElse(false));
        boolean syslogLocked = locks.syslog().isPresent();
        syslogField.setDisable(syslogLocked);
        syslogTlsCheck.setDisable(syslogLocked);
        if (syslogLocked) {
            syslogField.setTooltip(new Tooltip(UiI18n.get("telemetry.syslog_locked")));
        }

        TextField gelfField = new TextField(formatGelfHostPort(current.gelf()));
        gelfField.setPromptText(UiI18n.get("telemetry.host_port_prompt"));
        ComboBox<String> gelfTransport = new ComboBox<>(FXCollections.observableArrayList("tcp", "udp"));
        gelfTransport.setValue(current.gelf()
                .map(g -> g.transport().name().toLowerCase(Locale.ROOT))
                .orElse("tcp"));

        TextField lokiUrlField = new TextField(
                current.loki().map(TelemetryConfig.LokiSinkConfig::url).orElse(""));
        lokiUrlField.setPromptText(UiI18n.get("telemetry.url_prompt"));
        TextField lokiSiteField = new TextField(
                current.loki().map(TelemetryConfig.LokiSinkConfig::site).orElse(""));
        lokiSiteField.setPromptText(UiI18n.get("telemetry.site_prompt"));

        TextField otlpEndpointField = new TextField(
                current.otlp().map(TelemetryConfig.OtlpSinkConfig::endpoint).orElse(""));
        otlpEndpointField.setPromptText(UiI18n.get("telemetry.otlp_prompt"));
        TextField otlpServiceField = new TextField(
                current.otlp().map(TelemetryConfig.OtlpSinkConfig::serviceName).orElse("pingui"));
        boolean otlpLocked = locks.otlp().isPresent();
        otlpEndpointField.setDisable(otlpLocked);
        otlpServiceField.setDisable(otlpLocked);
        if (otlpLocked) {
            otlpEndpointField.setTooltip(new Tooltip(UiI18n.get("telemetry.otlp_locked")));
        }

        TextArea statusArea = new TextArea(current.toRedactedString());
        statusArea.setEditable(false);
        statusArea.setWrapText(true);
        statusArea.setPrefRowCount(3);
        statusArea.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label(UiI18n.get("telemetry.hint"));
        hint.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        grid.add(new Label(UiI18n.get("telemetry.sqlite")), 0, row);
        grid.add(rowWithBrowse(sqliteField, sqliteBrowse), 1, row++);
        grid.add(new Label(UiI18n.get("telemetry.jsonl")), 0, row);
        grid.add(rowWithBrowse(jsonlField, jsonlBrowse), 1, row++);
        grid.add(new Label(UiI18n.get("telemetry.syslog")), 0, row);
        HBox syslogRow = new HBox(8, syslogField, syslogTlsCheck);
        HBox.setHgrow(syslogField, Priority.ALWAYS);
        grid.add(syslogRow, 1, row++);
        grid.add(new Label(UiI18n.get("telemetry.gelf")), 0, row);
        HBox gelfRow = new HBox(8, gelfField, gelfTransport);
        HBox.setHgrow(gelfField, Priority.ALWAYS);
        grid.add(gelfRow, 1, row++);
        grid.add(new Label(UiI18n.get("telemetry.loki_url")), 0, row);
        grid.add(lokiUrlField, 1, row++);
        grid.add(new Label(UiI18n.get("telemetry.loki_site")), 0, row);
        grid.add(lokiSiteField, 1, row++);
        grid.add(new Label(UiI18n.get("telemetry.otlp_endpoint")), 0, row);
        grid.add(otlpEndpointField, 1, row++);
        grid.add(new Label(UiI18n.get("telemetry.otlp_service")), 0, row);
        grid.add(otlpServiceField, 1, row++);
        grid.add(new Label(UiI18n.get("dialog.status")), 0, row);
        grid.add(statusArea, 1, row);

        VBox content = new VBox(10, eventsOnlyCheck, logAggregatesCheck, grid, hint);
        content.setPrefWidth(640);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.APPLY) {
            return;
        }

        try {
            FormInput form = new FormInput(
                    eventsOnlyCheck.isSelected(),
                    logAggregatesCheck.isSelected(),
                    sqliteField.getText(),
                    jsonlField.getText(),
                    syslogField.getText(),
                    syslogTlsCheck.isSelected(),
                    gelfField.getText(),
                    gelfTransport.getValue(),
                    lokiUrlField.getText(),
                    lokiSiteField.getText(),
                    otlpEndpointField.getText(),
                    otlpServiceField.getText());
            TelemetryConfig next = buildConfig(current, form, locks);
            onApply.accept(new Result(next));
        } catch (ConfigError | IllegalArgumentException ex) {
            Alert error = new Alert(Alert.AlertType.WARNING);
            error.initOwner(owner);
            error.setTitle(UiI18n.get("telemetry.title"));
            error.setHeaderText(UiI18n.get("telemetry.invalid"));
            error.setContentText(ex.getMessage());
            error.showAndWait();
        }
    }

    /** Builds the next {@link TelemetryConfig} from dialog fields (unit-tested). */
    static TelemetryConfig buildConfig(TelemetryConfig baseline, FormInput form, CliTelemetryOverrides locks) {
        CliTelemetryOverrides cli = locks != null ? locks : CliTelemetryOverrides.none();
        FormInput input =
                form != null ? form : new FormInput(true, false, "", "", "", false, "", "tcp", "", "", "", "pingui");

        Optional<Path> sqlite = parseOptionalPath(input.sqliteText());
        Optional<Path> jsonl = cli.jsonlDir().isPresent() ? cli.jsonlDir() : parseOptionalPath(input.jsonlText());
        Optional<TelemetryConfig.SyslogSinkConfig> syslog =
                cli.syslog().isPresent() ? cli.syslog() : parseSyslog(input.syslogText(), input.syslogTls());
        Optional<TelemetryConfig.GelfSinkConfig> gelf = parseGelf(input.gelfText(), input.gelfTransport());
        Optional<TelemetryConfig.LokiSinkConfig> loki = parseLoki(input.lokiUrl(), input.lokiSite());
        Optional<TelemetryConfig.OtlpSinkConfig> otlp =
                cli.otlp().isPresent() ? cli.otlp() : parseOtlp(input.otlpEndpoint(), input.otlpServiceName());

        return new TelemetryConfig(input.eventsOnly(), input.logAggregates(), sqlite, jsonl, syslog, gelf, loki, otlp);
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
            throw new IllegalArgumentException(UiI18n.get("telemetry.err.syslog"), ex);
        }
    }

    static Optional<TelemetryConfig.GelfSinkConfig> parseGelf(String raw, String transportRaw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            TelemetryConfig.SyslogSinkConfig hostPort = CliTelemetryOverrides.parseSyslogHostPort(raw.strip());
            GelfSink.Transport transport = TelemetryConfig.GelfSinkConfig.parseTransport(transportRaw);
            return Optional.of(new TelemetryConfig.GelfSinkConfig(hostPort.host(), hostPort.port(), transport));
        } catch (IllegalArgumentException | ConfigError ex) {
            throw new IllegalArgumentException(UiI18n.get("telemetry.err.gelf"), ex);
        }
    }

    static Optional<TelemetryConfig.LokiSinkConfig> parseLoki(String url, String site) {
        boolean urlBlank = url == null || url.isBlank();
        boolean siteBlank = site == null || site.isBlank();
        if (urlBlank && siteBlank) {
            return Optional.empty();
        }
        if (urlBlank || siteBlank) {
            throw new IllegalArgumentException(UiI18n.get("telemetry.err.loki"));
        }
        try {
            return Optional.of(new TelemetryConfig.LokiSinkConfig(url.strip(), site.strip()));
        } catch (ConfigError ex) {
            throw new IllegalArgumentException(UiI18n.get("telemetry.err.loki_detail", ex.getMessage()), ex);
        }
    }

    static Optional<TelemetryConfig.OtlpSinkConfig> parseOtlp(String endpoint, String serviceName) {
        if (endpoint == null || endpoint.isBlank()) {
            return Optional.empty();
        }
        try {
            String service = serviceName == null || serviceName.isBlank() ? "pingui" : serviceName.strip();
            return Optional.of(new TelemetryConfig.OtlpSinkConfig(endpoint.strip(), service));
        } catch (ConfigError ex) {
            throw new IllegalArgumentException(UiI18n.get("telemetry.err.otlp", ex.getMessage()), ex);
        }
    }

    static String formatSyslog(Optional<TelemetryConfig.SyslogSinkConfig> syslog) {
        if (syslog == null || syslog.isEmpty()) {
            return "";
        }
        return formatHostPort(syslog.get().host(), syslog.get().port());
    }

    static String formatGelfHostPort(Optional<TelemetryConfig.GelfSinkConfig> gelf) {
        if (gelf == null || gelf.isEmpty()) {
            return "";
        }
        return formatHostPort(gelf.get().host(), gelf.get().port());
    }

    private static String formatHostPort(String host, int port) {
        if (host == null || host.isBlank() || port < 1) {
            return "";
        }
        if (host.contains(":")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    private static HBox rowWithBrowse(TextField field, Button browse) {
        HBox row = new HBox(8, field, browse);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private static Button browseFile(Window owner, TextField field, String title) {
        Button browse = new Button(UiI18n.get("dialog.browse"));
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(UiI18n.get("dialog.filter_sqlite"), "*.db"));
            java.io.File chosen = chooser.showSaveDialog(owner);
            if (chosen != null) {
                field.setText(chosen.getPath());
            }
        });
        return browse;
    }

    private static Button browseDirectory(Window owner, TextField field, String title) {
        Button browse = new Button(UiI18n.get("dialog.browse"));
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(title);
            java.io.File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                field.setText(chosen.getPath());
            }
        });
        return browse;
    }
}

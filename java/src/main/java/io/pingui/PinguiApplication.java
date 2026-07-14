package io.pingui;

import io.pingui.config.ConfigError;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.daemon.DaemonPidFile;
import io.pingui.daemon.DaemonRunner;
import io.pingui.export.ExportSchedulePeriod;
import io.pingui.export.ScheduledExport;
import io.pingui.export.SessionReportExporter;
import io.pingui.export.TelemetryDump;
import io.pingui.persistence.SessionDatabase;
import io.pingui.probe.ProbeMode;
import io.pingui.telemetry.TelemetryRetentionJob;
import io.pingui.ui.AppMenuDialogs;
import io.pingui.ui.MainController;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Cross-platform JavaFX entry point for PINGUI. */
public final class PinguiApplication extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) {
        try {
            AppOptions options = parseOptions(getParameters().getNamed());
            LoggingSetup.configure(options.verbose());
            ProfileDocument document = ProfilesConfig.load(options.configPath());
            controller = new MainController(options, document);
            AppMenuDialogs.bindHostServices(getHostServices());
            Scene scene = controller.createScene();
            stage.setTitle("PINGUI — Сесійний монітор маршрутів (Java)");
            stage.setScene(scene);
            stage.show();
            controller.onSceneShown();
        } catch (ConfigError | IllegalArgumentException ex) {
            failCli(ex.getMessage());
        } catch (IOException ex) {
            failCli(ex.getMessage());
        }
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    static AppOptions parseOptions(Map<String, String> params) {
        AppOptions defaults = AppOptions.defaults();
        Path config = params.containsKey("config") ? Path.of(params.get("config")) : defaults.configPath();
        CliProfileOverrides profileOverrides = parseProfileOverrides(params);
        CliAlertOverrides alertOverrides = parseAlertOverrides(params);
        CliPersistenceOverrides persistenceOverrides = parsePersistenceOverrides(params);
        CliTelemetryOverrides telemetryOverrides = parseTelemetryOverrides(params);
        CliTimeSeriesOverrides timeSeriesOverrides = parseTimeSeriesOverrides(params);
        boolean verbose = params.containsKey("verbose");
        boolean geoipEnabled = !params.containsKey("no-geoip");
        Path geoipHints =
                params.containsKey("geoip-hints") ? Path.of(params.get("geoip-hints")) : defaults.geoipHintsPath();
        boolean asnEnabled = !params.containsKey("no-asn");
        Path asnHints = params.containsKey("asn-hints") ? Path.of(params.get("asn-hints")) : defaults.asnHintsPath();
        int asnTimeoutMs = defaults.asnTimeoutMs();
        if (params.containsKey("asn-timeout-ms")) {
            asnTimeoutMs = parseRequiredInt(params.get("asn-timeout-ms"), "--asn-timeout-ms");
            if (asnTimeoutMs < 1) {
                throw new IllegalArgumentException("--asn-timeout-ms must be >= 1");
            }
        }
        Optional<Path> sessionDb = Optional.empty();
        if (params.containsKey("session-db")) {
            String value = params.get("session-db");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --session-db");
            }
            sessionDb = Optional.of(Path.of(value.strip()));
        }
        Optional<Path> exportReport = Optional.empty();
        if (params.containsKey("export-report")) {
            String value = params.get("export-report");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --export-report");
            }
            exportReport = Optional.of(Path.of(value.strip()));
        }
        Optional<ExportSchedulePeriod> exportSchedule = Optional.empty();
        if (params.containsKey("export-schedule")) {
            exportSchedule = Optional.of(ExportSchedulePeriod.parse(params.get("export-schedule")));
        }
        Optional<Path> exportDir = Optional.empty();
        if (params.containsKey("export-dir")) {
            String value = params.get("export-dir");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --export-dir");
            }
            exportDir = Optional.of(Path.of(value.strip()));
        }
        if (exportReport.isPresent() && exportSchedule.isPresent()) {
            throw new IllegalArgumentException("Use either --export-report or --export-schedule, not both");
        }
        if (exportSchedule.isPresent() && exportDir.isEmpty()) {
            throw new IllegalArgumentException("--export-schedule requires --export-dir PATH");
        }
        if (exportDir.isPresent() && exportSchedule.isEmpty()) {
            throw new IllegalArgumentException("--export-dir requires --export-schedule");
        }
        Optional<Integer> metricsPort = Optional.empty();
        if (params.containsKey("metrics-port")) {
            int port = parseRequiredInt(params.get("metrics-port"), "--metrics-port");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("--metrics-port must be 1..65535");
            }
            metricsPort = Optional.of(port);
        }
        Optional<Integer> apiPort = Optional.empty();
        if (params.containsKey("api-port")) {
            int port = parseRequiredInt(params.get("api-port"), "--api-port");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("--api-port must be 1..65535");
            }
            apiPort = Optional.of(port);
        }
        if (metricsPort.isPresent() && apiPort.isPresent() && metricsPort.get().equals(apiPort.get())) {
            throw new IllegalArgumentException("--api-port and --metrics-port must differ");
        }
        OptionalInt telemetryRetention = OptionalInt.empty();
        if (params.containsKey("telemetry-retention")) {
            int days = parseRequiredInt(params.get("telemetry-retention"), "--telemetry-retention");
            if (days < 1) {
                throw new IllegalArgumentException("--telemetry-retention must be >= 1");
            }
            telemetryRetention = OptionalInt.of(days);
        }
        Optional<Path> telemetryJsonlDir = Optional.empty();
        if (params.containsKey("telemetry-jsonl-dir")) {
            String value = params.get("telemetry-jsonl-dir");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --telemetry-jsonl-dir");
            }
            telemetryJsonlDir = Optional.of(Path.of(value.strip()));
        }
        if (telemetryJsonlDir.isPresent() && telemetryRetention.isEmpty()) {
            throw new IllegalArgumentException("--telemetry-jsonl-dir requires --telemetry-retention N");
        }
        if (telemetryRetention.isPresent() && sessionDb.isEmpty() && telemetryJsonlDir.isEmpty()) {
            throw new IllegalArgumentException(
                    "--telemetry-retention requires --session-db PATH and/or --telemetry-jsonl-dir DIR");
        }
        if (telemetryRetention.isPresent() && (exportReport.isPresent() || exportSchedule.isPresent())) {
            throw new IllegalArgumentException("Use either --telemetry-retention or export flags, not both");
        }
        Optional<Path> telemetryDump = Optional.empty();
        if (params.containsKey("telemetry-dump")) {
            String value = params.get("telemetry-dump");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --telemetry-dump");
            }
            telemetryDump = Optional.of(Path.of(value.strip()));
        }
        if (telemetryDump.isPresent() && sessionDb.isEmpty()) {
            throw new IllegalArgumentException("--telemetry-dump requires --session-db PATH");
        }
        if (telemetryDump.isPresent()
                && (telemetryRetention.isPresent() || exportReport.isPresent() || exportSchedule.isPresent())) {
            throw new IllegalArgumentException("Use either --telemetry-dump or retention/export flags, not both");
        }
        CliRunMode runMode = CliRunMode.GUI;
        if (params.containsKey("daemon")) {
            runMode = CliRunMode.DAEMON;
        } else if (params.containsKey("stop")) {
            runMode = CliRunMode.STOP;
        } else if (params.containsKey("status")) {
            runMode = CliRunMode.STATUS;
        } else if (telemetryRetention.isPresent()) {
            runMode = CliRunMode.TELEMETRY_RETENTION;
        } else if (telemetryDump.isPresent()) {
            runMode = CliRunMode.TELEMETRY_DUMP;
        } else if (exportReport.isPresent() || exportSchedule.isPresent()) {
            runMode = CliRunMode.EXPORT;
        }
        Path pidFile = AppOptions.defaultPidFile();
        if (params.containsKey("pid-file")) {
            String value = params.get("pid-file");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --pid-file");
            }
            pidFile = Path.of(value.strip());
        }
        return new AppOptions(
                config,
                profileOverrides,
                alertOverrides,
                persistenceOverrides,
                telemetryOverrides,
                timeSeriesOverrides,
                verbose,
                geoipEnabled,
                geoipHints,
                asnEnabled,
                asnHints,
                asnTimeoutMs,
                sessionDb,
                exportReport,
                exportSchedule,
                exportDir,
                runMode,
                pidFile,
                metricsPort,
                apiPort,
                telemetryRetention,
                telemetryJsonlDir,
                telemetryDump);
    }

    private static CliTelemetryOverrides parseTelemetryOverrides(Map<String, String> params) {
        Optional<io.pingui.config.TelemetryConfig.SyslogSinkConfig> syslog = Optional.empty();
        if (params.containsKey("telemetry-syslog")) {
            syslog = Optional.of(CliTelemetryOverrides.parseSyslogHostPort(params.get("telemetry-syslog")));
        }
        Optional<Path> jsonlDir = Optional.empty();
        if (params.containsKey("telemetry-jsonl")) {
            String value = params.get("telemetry-jsonl");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --telemetry-jsonl");
            }
            jsonlDir = Optional.of(Path.of(value.strip()));
        }
        Optional<io.pingui.config.TelemetryConfig.OtlpSinkConfig> otlp = Optional.empty();
        if (params.containsKey("telemetry-otlp")) {
            otlp = Optional.of(CliTelemetryOverrides.parseOtlpEndpoint(params.get("telemetry-otlp")));
        }
        return new CliTelemetryOverrides(syslog, jsonlDir, otlp);
    }

    private static CliTimeSeriesOverrides parseTimeSeriesOverrides(Map<String, String> params) {
        return new CliTimeSeriesOverrides(
                optionalParam(params, "ts-backend"),
                optionalParam(params, "influx-url"),
                optionalParam(params, "influx-token"),
                optionalParam(params, "influx-org"),
                optionalParam(params, "influx-bucket"),
                optionalParam(params, "timescale-dsn"));
    }

    private static Optional<String> optionalParam(Map<String, String> params, String key) {
        if (!params.containsKey(key)) {
            return Optional.empty();
        }
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing value for --" + key);
        }
        return Optional.of(value.strip());
    }

    private static CliPersistenceOverrides parsePersistenceOverrides(Map<String, String> params) {
        Optional<Boolean> routeChange = Optional.empty();
        if (params.containsKey("no-persist-route-change")) {
            routeChange = Optional.of(false);
        }
        Optional<Boolean> probeError = Optional.empty();
        if (params.containsKey("no-persist-probe-error")) {
            probeError = Optional.of(false);
        }
        return new CliPersistenceOverrides(routeChange, probeError);
    }

    private static CliAlertOverrides parseAlertOverrides(Map<String, String> params) {
        Optional<String> webhook = Optional.empty();
        if (params.containsKey("alert-webhook")) {
            String value = params.get("alert-webhook");
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for --alert-webhook");
            }
            webhook = Optional.of(value.strip());
        }
        Optional<Boolean> desktop = Optional.empty();
        if (params.containsKey("desktop-alerts")) {
            String value = params.get("desktop-alerts");
            desktop = Optional.of(value == null || value.isBlank() || !"false".equalsIgnoreCase(value));
        }
        OptionalInt rateLimit = OptionalInt.empty();
        if (params.containsKey("alert-rate-limit")) {
            int value = parseRequiredInt(params.get("alert-rate-limit"), "--alert-rate-limit");
            if (value < 1) {
                throw new IllegalArgumentException("--alert-rate-limit must be >= 1");
            }
            rateLimit = OptionalInt.of(value);
        }
        return new CliAlertOverrides(webhook, desktop, rateLimit);
    }

    private static CliProfileOverrides parseProfileOverrides(Map<String, String> params) {
        OptionalDouble interval = OptionalDouble.empty();
        if (params.containsKey("interval")) {
            double value = parseRequiredDouble(params.get("interval"), "--interval");
            if (value <= 0) {
                throw new IllegalArgumentException("--interval must be positive");
            }
            interval = OptionalDouble.of(value);
        }
        OptionalInt maxHops = OptionalInt.empty();
        if (params.containsKey("max-hops")) {
            int value = parseRequiredInt(params.get("max-hops"), "--max-hops");
            if (value < 1) {
                throw new IllegalArgumentException("--max-hops must be >= 1");
            }
            maxHops = OptionalInt.of(value);
        }
        OptionalDouble timeout = OptionalDouble.empty();
        if (params.containsKey("timeout")) {
            double value = parseRequiredDouble(params.get("timeout"), "--timeout");
            if (value <= 0) {
                throw new IllegalArgumentException("--timeout must be positive");
            }
            timeout = OptionalDouble.of(value);
        }
        Optional<ProbeMode> probeMode = Optional.empty();
        if (params.containsKey("probe")) {
            probeMode = Optional.of(ProbeMode.parse(params.get("probe")));
        }
        return new CliProfileOverrides(interval, maxHops, timeout, probeMode);
    }

    private static double parseRequiredDouble(String value, String flag) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid value for " + flag + ": " + value);
        }
    }

    private static int parseRequiredInt(String value, String flag) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid value for " + flag + ": " + value);
        }
    }

    public static void main(String[] args) {
        List<String> raw = List.of(args);
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                return;
            }
        }
        boolean verbose = false;
        for (String arg : args) {
            if ("--verbose".equals(arg)) {
                verbose = true;
                break;
            }
        }
        LoggingSetup.configure(verbose);
        Map<String, String> params = parseRawArgs(raw);
        AppOptions options = parseOptions(params);
        switch (options.runMode()) {
            case EXPORT -> {
                runExport(options);
                return;
            }
            case TELEMETRY_RETENTION -> {
                runTelemetryRetention(options);
                return;
            }
            case TELEMETRY_DUMP -> {
                runTelemetryDump(options);
                return;
            }
            case DAEMON -> {
                runDaemon(options);
                return;
            }
            case STOP -> {
                runStop(options);
                return;
            }
            case STATUS -> {
                runStatus(options);
                return;
            }
            default -> {}
        }
        List<String> fxArgs = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            fxArgs.add("--" + entry.getKey() + "=" + entry.getValue());
        }
        launch(fxArgs.toArray(String[]::new));
    }

    private static Map<String, String> parseRawArgs(List<String> raw) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            String arg = raw.get(i);
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            if ("verbose".equals(key)) {
                params.put(key, "true");
                continue;
            }
            if (i + 1 < raw.size() && !raw.get(i + 1).startsWith("--")) {
                params.put(key, raw.get(++i));
            } else {
                params.put(key, "true");
            }
        }
        return params;
    }

    private static void runExport(AppOptions options) {
        if (options.exportSchedule().isPresent()) {
            runScheduledExport(options);
        } else {
            runExportReport(options);
        }
    }

    private static void runExportReport(AppOptions options) {
        if (options.sessionDbPath().isEmpty()) {
            failCli("--export-report requires --session-db PATH");
        }
        Path reportPath = options.exportReportPath().orElseThrow();
        try (SessionDatabase database =
                new SessionDatabase(options.sessionDbPath().orElseThrow())) {
            if (isHtmlReport(reportPath)) {
                SessionReportExporter.exportHtml(database, reportPath);
            } else {
                SessionReportExporter.exportCsv(database, reportPath);
            }
            System.out.println("Session report written: " + reportPath.toAbsolutePath());
        } catch (IOException | RuntimeException ex) {
            failCli("Export failed: " + ex.getMessage());
        }
    }

    private static void runScheduledExport(AppOptions options) {
        if (options.sessionDbPath().isEmpty()) {
            failCli("--export-schedule requires --session-db PATH");
        }
        Path exportDir = options.exportDir().orElseThrow();
        ExportSchedulePeriod period = options.exportSchedule().orElseThrow();
        try (SessionDatabase database =
                new SessionDatabase(options.sessionDbPath().orElseThrow())) {
            ScheduledExport.Result result = ScheduledExport.run(database, exportDir, period, Clock.systemUTC());
            System.out.println("Scheduled CSV written: " + result.csvPath().toAbsolutePath());
            System.out.println("Scheduled HTML written: " + result.htmlPath().toAbsolutePath());
        } catch (IOException | RuntimeException ex) {
            failCli("Scheduled export failed: " + ex.getMessage());
        }
    }

    private static void runTelemetryRetention(AppOptions options) {
        int days = options.telemetryRetentionDays().orElseThrow();
        Path jsonlDir = options.telemetryJsonlDir().orElse(null);
        try {
            if (options.sessionDbPath().isPresent()) {
                try (SessionDatabase database =
                        new SessionDatabase(options.sessionDbPath().orElseThrow())) {
                    TelemetryRetentionJob.Result result =
                            TelemetryRetentionJob.run(database, jsonlDir, days, Clock.systemUTC());
                    printRetentionResult(result);
                }
            } else {
                TelemetryRetentionJob.Result result =
                        TelemetryRetentionJob.run(null, jsonlDir, days, Clock.systemUTC());
                printRetentionResult(result);
            }
        } catch (RuntimeException ex) {
            failCli("Telemetry retention failed: " + ex.getMessage());
        }
    }

    private static void printRetentionResult(TelemetryRetentionJob.Result result) {
        System.out.println("Telemetry retention: samples="
                + result.samplesDeleted()
                + " events="
                + result.eventsDeleted()
                + " jsonl_files="
                + result.jsonlFilesDeleted());
    }

    private static void runTelemetryDump(AppOptions options) {
        if (options.sessionDbPath().isEmpty() || options.telemetryDumpPath().isEmpty()) {
            failCli("--telemetry-dump requires --session-db PATH");
        }
        Path dumpPath = options.telemetryDumpPath().orElseThrow();
        try (SessionDatabase database =
                new SessionDatabase(options.sessionDbPath().orElseThrow())) {
            TelemetryDump.export(database, dumpPath);
            System.out.println("Telemetry dump written: " + dumpPath.toAbsolutePath());
        } catch (IOException | RuntimeException ex) {
            failCli("Telemetry dump failed: " + ex.getMessage());
        }
    }

    private static boolean isHtmlReport(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    private static void runDaemon(AppOptions options) {
        try (DaemonRunner runner = new DaemonRunner(options, options.pidFilePath())) {
            runner.start();
            runner.await();
        } catch (IllegalStateException | ConfigError | IllegalArgumentException ex) {
            failCli(ex.getMessage());
        } catch (IOException ex) {
            failCli(ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runStop(AppOptions options) {
        try {
            boolean stopped = DaemonPidFile.stop(options.pidFilePath(), 5000);
            if (!stopped) {
                failCli("No running daemon (PID file: " + options.pidFilePath().toAbsolutePath() + ")");
            }
            System.out.println("Daemon stopped");
        } catch (IOException | InterruptedException ex) {
            failCli("Stop failed: " + ex.getMessage());
        }
    }

    private static void runStatus(AppOptions options) {
        try {
            if (DaemonPidFile.isRunning(options.pidFilePath())) {
                long pid = DaemonPidFile.read(options.pidFilePath()).orElse(-1);
                System.out.println("running pid=" + pid + " pid-file="
                        + options.pidFilePath().toAbsolutePath());
            } else {
                System.out.println("stopped pid-file=" + options.pidFilePath().toAbsolutePath());
            }
        } catch (IOException ex) {
            failCli("Status failed: " + ex.getMessage());
        }
    }

    private static void failCli(String message) {
        System.err.println("Config error: " + message);
        System.exit(1);
    }

    private static void printHelp() {
        System.out.println(
                """
                PINGUI Java — cross-platform route monitor

                  ./pingui-java.sh
                  ./pingui-java.sh --config config/hosts.example.yaml --interval 2

                Options:
                  --config PATH     YAML profiles (default: config/hosts.example.yaml)
                  --interval SEC    Override active profile poll interval (if omitted, YAML value kept)
                  --max-hops N      Override max TTL hops for this session
                  --timeout SEC     Override probe timeout for this session
                  --probe MODE      Override probe: auto | process | raw
                  --alert-webhook URL  POST route-change JSON (secrets not logged)
                  --desktop-alerts     Linux desktop notifications (notify-send)
                  --alert-rate-limit N Max alerts per host per hour (default: 10)
                  --session-db PATH  SQLite session metrics + events (optional)
                  --telemetry-syslog HOST:PORT  Override telemetry syslog sink (profile)
                  --telemetry-jsonl DIR         Override telemetry JSONL directory (profile)
                  --telemetry-otlp URL          Override telemetry OTLP/HTTP endpoint (profile)
                  --telemetry-retention N  Purge telemetry older than N days and exit (cron)
                  --telemetry-jsonl-dir DIR  Optional JSONL dir for --telemetry-retention
                  --telemetry-dump PATH     Dump SQLite telemetry to .csv/.json and exit
                  --export-report PATH  Export CSV/HTML from --session-db and exit (no GUI)
                  --export-schedule P   Cron one-shot: hourly|daily|weekly (with --export-dir)
                  --export-dir DIR      Output directory for --export-schedule (CSV+HTML stamped)
                  --daemon            Headless monitor loop (no JavaFX)
                  --pid-file PATH     PID file for daemon/stop/status (default: $TMP/pingui-java.pid)
                  --metrics-port N    Prometheus /metrics on 127.0.0.1:N (daemon; off if omitted)
                  --api-port N        Read-only REST API on 127.0.0.1:N (daemon; /hosts, /routes)
                  --ts-backend NAME   Time-series: influx | timescale (off if omitted)
                  --influx-url URL    InfluxDB URL (or INFLUXDB_URL)
                  --influx-token TOK  InfluxDB token (or INFLUXDB_TOKEN; never logged)
                  --influx-org ORG    InfluxDB org (or INFLUXDB_ORG)
                  --influx-bucket B   InfluxDB bucket (or INFLUXDB_BUCKET)
                  --timescale-dsn DSN PostgreSQL/Timescale DSN (or PINGUI_TIMESCALE_DSN)
                  --stop              Stop daemon using --pid-file
                  --status            Print daemon running/stopped
                  --no-persist-route-change  Disable route_change events in session DB
                  --no-persist-probe-error     Disable probe_error events in session DB
                  --geoip-hints PATH  CIDR→country YAML (default: config/geoip_hints.yaml)
                  --no-geoip        Disable country hints in hop labels
                  --asn-hints PATH    CIDR→ASN YAML (default: config/asn_hints.yaml)
                  --no-asn          Disable ASN hints in hop labels
                  --asn-timeout-ms N Reserved for future whois fallback (default: 2000)
                  --verbose         Debug logging
                """);
    }
}

package io.pingui;

import io.pingui.config.ConfigError;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
import io.pingui.daemon.DaemonPidFile;
import io.pingui.daemon.DaemonRunner;
import io.pingui.export.SessionReportExporter;
import io.pingui.persistence.SessionDatabase;
import io.pingui.probe.ProbeMode;
import io.pingui.ui.AppMenuDialogs;
import io.pingui.ui.MainController;
import java.io.IOException;
import java.nio.file.Path;
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
        CliRunMode runMode = CliRunMode.GUI;
        if (params.containsKey("daemon")) {
            runMode = CliRunMode.DAEMON;
        } else if (params.containsKey("stop")) {
            runMode = CliRunMode.STOP;
        } else if (params.containsKey("status")) {
            runMode = CliRunMode.STATUS;
        } else if (exportReport.isPresent()) {
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
                verbose,
                geoipEnabled,
                geoipHints,
                asnEnabled,
                asnHints,
                asnTimeoutMs,
                sessionDb,
                exportReport,
                runMode,
                pidFile,
                Optional.empty());
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
                runExportReport(options);
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
                  --export-report PATH  Export CSV/HTML from --session-db and exit (no GUI)
                  --daemon            Headless monitor loop (no JavaFX)
                  --pid-file PATH     PID file for daemon/stop/status (default: $TMP/pingui-java.pid)
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

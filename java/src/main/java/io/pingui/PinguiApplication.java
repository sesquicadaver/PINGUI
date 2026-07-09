package io.pingui;

import io.pingui.config.ConfigError;
import io.pingui.config.ProfileDocument;
import io.pingui.config.ProfilesConfig;
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
        boolean verbose = params.containsKey("verbose");
        boolean geoipEnabled = !params.containsKey("no-geoip");
        Path geoipHints =
                params.containsKey("geoip-hints") ? Path.of(params.get("geoip-hints")) : defaults.geoipHintsPath();
        return new AppOptions(config, profileOverrides, alertOverrides, verbose, geoipEnabled, geoipHints);
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
        boolean verbose = false;
        for (String arg : args) {
            if ("--verbose".equals(arg)) {
                verbose = true;
                break;
            }
        }
        LoggingSetup.configure(verbose);
        List<String> raw = new ArrayList<>(List.of(args));
        List<String> fxArgs = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String arg = raw.get(i);
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                return;
            }
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if ("verbose".equals(key)) {
                    fxArgs.add("--verbose=true");
                    continue;
                }
                if (i + 1 < raw.size() && !raw.get(i + 1).startsWith("--")) {
                    fxArgs.add("--" + key + "=" + raw.get(++i));
                } else {
                    fxArgs.add("--" + key + "=true");
                }
            } else {
                fxArgs.add(arg);
            }
        }
        launch(fxArgs.toArray(String[]::new));
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
                  --geoip-hints PATH  CIDR→country YAML (default: config/geoip_hints.yaml)
                  --no-geoip        Disable country hints in hop labels
                  --verbose         Debug logging
                """);
    }
}

package io.pingui;

import io.pingui.config.HostsConfig;
import io.pingui.probe.ProbeMode;
import io.pingui.ui.MainController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Cross-platform JavaFX entry point for PINGUI. */
public final class PinguiApplication extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws Exception {
        AppOptions options = parseOptions(getParameters().getNamed());
        LoggingSetup.configure(options.verbose());
        List<String> hosts = HostsConfig.load(options.configPath());
        controller = new MainController(options, hosts);
        Scene scene = controller.createScene();
        stage.setTitle("PINGUI — Сесійний монітор маршрутів (Java)");
        stage.setScene(scene);
        stage.show();
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
        double interval = parseDouble(params.get("interval"), defaults.intervalSeconds());
        int maxHops = parseInt(params.get("max-hops"), defaults.maxHops());
        double timeout = parseDouble(params.get("timeout"), defaults.timeoutSeconds());
        boolean verbose = params.containsKey("verbose");
        ProbeMode probeMode =
                params.containsKey("probe") ? ProbeMode.parse(params.get("probe")) : defaults.probeMode();
        if (interval <= 0 || timeout <= 0 || maxHops < 1) {
            throw new IllegalArgumentException("Invalid CLI numeric options");
        }
        return new AppOptions(config, interval, maxHops, timeout, verbose, probeMode);
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Double.parseDouble(value);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
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

    private static void printHelp() {
        System.out.println(
                """
                PINGUI Java — cross-platform route monitor

                  ./pingui-java.sh
                  ./pingui-java.sh --config config/hosts.example.yaml --interval 2

                Options:
                  --config PATH     YAML host list (default: config/hosts.example.yaml)
                  --interval SEC    Poll interval (default: 1.0)
                  --max-hops N      Max TTL hops (default: 20)
                  --timeout SEC     Probe timeout (default: 0.5)
                  --probe MODE      auto | process | raw (default: auto)
                  --verbose         Debug logging
                """);
    }
}

package io.pingui.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.AppOptions;
import io.pingui.CliAlertOverrides;
import io.pingui.CliPersistenceOverrides;
import io.pingui.CliProfileOverrides;
import io.pingui.CliRunMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void startWritesPidAndCloseRemoves() throws Exception {
        Path config = tempDir.resolve("hosts.yaml");
        Files.writeString(
                config,
                """
                active_profile: default
                profiles:
                  default:
                    interval: 30.0
                    max_hops: 5
                    timeout: 1.0
                    probe: process
                    hosts:
                      - address: 127.0.0.1
                        enabled: false
                """);
        Path pidFile = tempDir.resolve("daemon.pid");
        AppOptions options = new AppOptions(
                config,
                CliProfileOverrides.none(),
                CliAlertOverrides.none(),
                CliPersistenceOverrides.none(),
                io.pingui.CliTimeSeriesOverrides.none(),
                false,
                false,
                Path.of("config/geoip_hints.yaml"),
                false,
                Path.of("config/asn_hints.yaml"),
                2000,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CliRunMode.DAEMON,
                pidFile,
                Optional.empty(),
                Optional.empty());

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            runner.start();
            assertTrue(DaemonPidFile.isRunning(pidFile));
            assertTrue(runner.metricsServer().isEmpty());
            assertTrue(runner.apiServer().isEmpty());
        }
        assertTrue(Files.notExists(pidFile));
    }

    @Test
    void startWithMetricsPortServesPrometheus() throws Exception {
        Path config = tempDir.resolve("hosts-metrics.yaml");
        Files.writeString(
                config,
                """
                active_profile: default
                profiles:
                  default:
                    interval: 30.0
                    max_hops: 5
                    timeout: 1.0
                    probe: process
                    hosts:
                      - address: 127.0.0.1
                        enabled: false
                """);
        Path pidFile = tempDir.resolve("metrics.pid");
        java.net.ServerSocket probe = new java.net.ServerSocket(0);
        int port = probe.getLocalPort();
        probe.close();
        AppOptions options = new AppOptions(
                config,
                CliProfileOverrides.none(),
                CliAlertOverrides.none(),
                CliPersistenceOverrides.none(),
                io.pingui.CliTimeSeriesOverrides.none(),
                false,
                false,
                Path.of("config/geoip_hints.yaml"),
                false,
                Path.of("config/asn_hints.yaml"),
                2000,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CliRunMode.DAEMON,
                pidFile,
                Optional.of(port),
                Optional.empty());

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            runner.start();
            assertTrue(runner.metricsServer().isPresent());
            assertTrue(runner.apiServer().isEmpty());
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpResponse<String> response = client.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/metrics"))
                            .GET()
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("# TYPE pingui_rtt_ms gauge"));
        }
    }

    @Test
    void startRejectsDuplicatePidFile() throws Exception {
        Path config = tempDir.resolve("hosts.yaml");
        Files.writeString(
                config,
                """
                active_profile: default
                profiles:
                  default:
                    hosts: []
                """);
        Path pidFile = tempDir.resolve("dup.pid");
        DaemonPidFile.write(pidFile, ProcessHandle.current().pid());
        AppOptions options = new AppOptions(
                config,
                CliProfileOverrides.none(),
                CliAlertOverrides.none(),
                CliPersistenceOverrides.none(),
                io.pingui.CliTimeSeriesOverrides.none(),
                false,
                false,
                Path.of("config/geoip_hints.yaml"),
                false,
                Path.of("config/asn_hints.yaml"),
                2000,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CliRunMode.DAEMON,
                pidFile,
                Optional.empty(),
                Optional.empty());

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            assertThrows(IllegalStateException.class, runner::start);
        }
    }
}

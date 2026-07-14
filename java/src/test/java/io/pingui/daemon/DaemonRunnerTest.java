package io.pingui.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.AppOptions;
import io.pingui.CliAlertOverrides;
import io.pingui.CliPersistenceOverrides;
import io.pingui.CliProfileOverrides;
import io.pingui.CliRunMode;
import io.pingui.CliTelemetryOverrides;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
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
                CliTelemetryOverrides.none(),
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
                Optional.empty(),
                OptionalInt.empty(),
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
                CliTelemetryOverrides.none(),
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
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
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
    void startWithApiPortServesHosts() throws Exception {
        Path config = tempDir.resolve("hosts-api.yaml");
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
                      - address: 8.8.8.8
                        enabled: true
                """);
        Path pidFile = tempDir.resolve("api.pid");
        java.net.ServerSocket probe = new java.net.ServerSocket(0);
        int port = probe.getLocalPort();
        probe.close();
        AppOptions options = new AppOptions(
                config,
                CliProfileOverrides.none(),
                CliAlertOverrides.none(),
                CliPersistenceOverrides.none(),
                CliTelemetryOverrides.none(),
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
                Optional.of(port),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty());

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            runner.start();
            assertTrue(runner.apiServer().isPresent());
            assertTrue(runner.metricsServer().isEmpty());
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpResponse<String> response = client.send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/hosts"))
                            .GET()
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"address\":\"8.8.8.8\""));
            assertTrue(response.body().contains("\"enabled\":true"));
        }
    }

    @Test
    void startRegistersSqliteAndSyslogFromTelemetryConfig() throws Exception {
        Path sessionDb = tempDir.resolve("session.db");
        Path telemetryDb = tempDir.resolve("telemetry.db");
        Path config = tempDir.resolve("hosts-telemetry.yaml");
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
                    telemetry:
                      events_only: true
                      sqlite: %s
                      syslog:
                        host: 127.0.0.1
                        port: 15999
                        tls: false
                """
                        .formatted(telemetryDb.toString().replace('\\', '/')));
        Path pidFile = tempDir.resolve("telemetry.pid");
        AppOptions options = new AppOptions(
                config,
                CliProfileOverrides.none(),
                CliAlertOverrides.none(),
                CliPersistenceOverrides.none(),
                CliTelemetryOverrides.none(),
                io.pingui.CliTimeSeriesOverrides.none(),
                false,
                false,
                Path.of("config/geoip_hints.yaml"),
                false,
                Path.of("config/asn_hints.yaml"),
                2000,
                Optional.of(sessionDb),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CliRunMode.DAEMON,
                pidFile,
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty());

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            runner.start();
            assertTrue(runner.telemetryRegistry().isPresent());
            assertTrue(runner.telemetryRegistry().get().contains("sqlite"));
            assertTrue(runner.telemetryRegistry().get().contains("syslog"));
            runner.telemetryRegistry()
                    .get()
                    .emitEvent(io.pingui.telemetry.TelemetryEvent.routeChange(
                            "8.8.8.8",
                            java.util.List.of("10.0.0.1"),
                            java.util.List.of("8.8.8.8"),
                            java.util.Map.of("profile", "default"),
                            java.time.Instant.parse("2026-07-14T09:00:00Z")));
        }
        try (io.pingui.persistence.SessionDatabase db = new io.pingui.persistence.SessionDatabase(telemetryDb)) {
            assertEquals(1, db.countTelemetryEvents());
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
                CliTelemetryOverrides.none(),
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
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty());

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            assertThrows(IllegalStateException.class, runner::start);
        }
    }
}

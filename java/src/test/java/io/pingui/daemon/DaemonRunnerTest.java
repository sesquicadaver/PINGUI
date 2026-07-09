package io.pingui.daemon;

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
                false,
                false,
                Path.of("config/geoip_hints.yaml"),
                Optional.empty(),
                Optional.empty(),
                CliRunMode.DAEMON,
                pidFile);

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            runner.start();
            assertTrue(DaemonPidFile.isRunning(pidFile));
        }
        assertTrue(Files.notExists(pidFile));
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
                false,
                false,
                Path.of("config/geoip_hints.yaml"),
                Optional.empty(),
                Optional.empty(),
                CliRunMode.DAEMON,
                pidFile);

        try (DaemonRunner runner = new DaemonRunner(options, pidFile)) {
            assertThrows(IllegalStateException.class, runner::start);
        }
    }
}

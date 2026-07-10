package io.pingui;

import java.nio.file.Path;
import java.util.Optional;

/** Runtime CLI options for the Java edition. */
public record AppOptions(
        Path configPath,
        CliProfileOverrides profileOverrides,
        CliAlertOverrides alertOverrides,
        CliPersistenceOverrides persistenceOverrides,
        boolean verbose,
        boolean geoipEnabled,
        Path geoipHintsPath,
        boolean asnEnabled,
        Path asnHintsPath,
        int asnTimeoutMs,
        Optional<Path> sessionDbPath,
        Optional<Path> exportReportPath,
        CliRunMode runMode,
        Path pidFilePath) {
    public static AppOptions defaults() {
        return new AppOptions(
                Path.of("config/hosts.example.yaml"),
                CliProfileOverrides.none(),
                CliAlertOverrides.none(),
                CliPersistenceOverrides.none(),
                false,
                true,
                Path.of("config/geoip_hints.yaml"),
                true,
                Path.of("config/asn_hints.yaml"),
                2000,
                Optional.empty(),
                Optional.empty(),
                CliRunMode.GUI,
                defaultPidFile());
    }

    public static Path defaultPidFile() {
        return Path.of(System.getProperty("java.io.tmpdir")).resolve("pingui-java.pid");
    }
}

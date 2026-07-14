package io.pingui;

import io.pingui.export.ExportSchedulePeriod;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/** Runtime CLI options for the Java edition. */
public record AppOptions(
        Path configPath,
        CliProfileOverrides profileOverrides,
        CliAlertOverrides alertOverrides,
        CliPersistenceOverrides persistenceOverrides,
        CliTimeSeriesOverrides timeSeriesOverrides,
        boolean verbose,
        boolean geoipEnabled,
        Path geoipHintsPath,
        boolean asnEnabled,
        Path asnHintsPath,
        int asnTimeoutMs,
        Optional<Path> sessionDbPath,
        Optional<Path> exportReportPath,
        Optional<ExportSchedulePeriod> exportSchedule,
        Optional<Path> exportDir,
        CliRunMode runMode,
        Path pidFilePath,
        Optional<Integer> metricsPort,
        Optional<Integer> apiPort,
        OptionalInt telemetryRetentionDays,
        Optional<Path> telemetryJsonlDir,
        Optional<Path> telemetryDumpPath) {
    public static AppOptions defaults() {
        return new AppOptions(
                Path.of("config/hosts.example.yaml"),
                CliProfileOverrides.none(),
                CliAlertOverrides.none(),
                CliPersistenceOverrides.none(),
                CliTimeSeriesOverrides.none(),
                false,
                true,
                Path.of("config/geoip_hints.yaml"),
                true,
                Path.of("config/asn_hints.yaml"),
                2000,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CliRunMode.GUI,
                defaultPidFile(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public static Path defaultPidFile() {
        return Path.of(System.getProperty("java.io.tmpdir")).resolve("pingui-java.pid");
    }
}

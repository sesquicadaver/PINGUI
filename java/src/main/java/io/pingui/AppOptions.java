package io.pingui;

import java.nio.file.Path;

/** Runtime CLI options for the Java edition. */
public record AppOptions(
        Path configPath,
        CliProfileOverrides profileOverrides,
        boolean verbose,
        boolean geoipEnabled,
        Path geoipHintsPath) {
    public static AppOptions defaults() {
        return new AppOptions(
                Path.of("config/hosts.example.yaml"),
                CliProfileOverrides.none(),
                false,
                true,
                Path.of("config/geoip_hints.yaml"));
    }
}

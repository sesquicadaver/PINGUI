package io.pingui;

import java.nio.file.Path;

/** Runtime CLI options for the Java edition. */
public record AppOptions(Path configPath, double intervalSeconds, int maxHops, double timeoutSeconds, boolean verbose) {
    public static AppOptions defaults() {
        return new AppOptions(Path.of("config/hosts.example.yaml"), 1.0, 20, 0.5, false);
    }
}

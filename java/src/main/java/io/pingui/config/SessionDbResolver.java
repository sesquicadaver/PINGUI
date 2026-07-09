package io.pingui.config;

import java.nio.file.Path;
import java.util.Optional;

/** Resolves effective SQLite path: CLI > YAML > GUI session override (P11-016). */
public final class SessionDbResolver {

    private SessionDbResolver() {}

    public static Optional<Path> resolve(Optional<Path> cliPath, Optional<Path> yamlPath, Optional<Path> guiOverride) {
        if (cliPath != null && cliPath.isPresent()) {
            return cliPath;
        }
        if (yamlPath != null && yamlPath.isPresent()) {
            return yamlPath;
        }
        return guiOverride != null ? guiOverride : Optional.empty();
    }

    public static boolean isCliLocked(Optional<Path> cliPath) {
        return cliPath != null && cliPath.isPresent();
    }

    public static boolean canPickGuiPath(Optional<Path> cliPath, Optional<Path> yamlPath) {
        return !isCliLocked(cliPath) && (yamlPath == null || yamlPath.isEmpty());
    }
}

package io.pingui.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;

/** Resolves traceroute/tracert executable paths for subprocess probes. */
final class TracerouteExecutables {

    private TracerouteExecutables() {}

    /** Resolves traceroute binary; macOS GUI apps often lack {@code /usr/sbin} in PATH. */
    static String resolveTracerouteExecutable() {
        return resolveTracerouteExecutable(
                System.getProperty("os.name", ""), path -> Files.isExecutable(Path.of(path)));
    }

    static String resolveTracerouteExecutable(String osName, Predicate<String> absolutePathExists) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            String macPath = "/usr/sbin/traceroute";
            if (absolutePathExists.test(macPath)) {
                return macPath;
            }
        }
        return "traceroute";
    }

    /** Resolves tracert; JavaFX/GUI apps on Windows often have a minimal PATH without System32. */
    static String resolveTracertExecutable(Predicate<Path> executable) {
        return resolveTracertExecutable(System.getenv("SystemRoot"), executable);
    }

    static String resolveTracertExecutable(String systemRoot, Predicate<Path> executable) {
        if (systemRoot != null && !systemRoot.isBlank()) {
            Path tracert = Path.of(systemRoot, "System32", "tracert.exe");
            if (executable.test(tracert)) {
                return tracert.toString();
            }
        }
        return "tracert";
    }
}

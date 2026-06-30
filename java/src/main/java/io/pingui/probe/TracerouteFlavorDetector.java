package io.pingui.probe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Detects GNU inetutils vs BSD traceroute flavor (cached). */
final class TracerouteFlavorDetector {

    private static volatile TracerouteFlavor cachedFlavor;

    private TracerouteFlavorDetector() {}

    static TracerouteFlavor resolveFlavor() {
        TracerouteFlavor local = cachedFlavor;
        if (local != null) {
            return local;
        }
        synchronized (TracerouteFlavorDetector.class) {
            if (cachedFlavor == null) {
                cachedFlavor = detectFlavor(System.getProperty("os.name", ""), readTracerouteVersion());
            }
            return cachedFlavor;
        }
    }

    static TracerouteFlavor detectFlavor(String osName, String versionOutput) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win") || os.contains("mac")) {
            return TracerouteFlavor.BSD;
        }
        if (versionOutput != null && versionOutput.contains("GNU inetutils")) {
            return TracerouteFlavor.GNU_INETUTILS;
        }
        return TracerouteFlavor.BSD;
    }

    private static String readTracerouteVersion() {
        String traceroute = TracerouteExecutables.resolveTracerouteExecutable();
        ProcessBuilder builder = new ProcessBuilder(traceroute, "--version");
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
                return output.toString();
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }
}

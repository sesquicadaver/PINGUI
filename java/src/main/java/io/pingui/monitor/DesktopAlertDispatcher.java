package io.pingui.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Desktop notification channel via Linux {@code notify-send} (P10-020 / ADR_ALERTS). */
public final class DesktopAlertDispatcher implements AlertDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(DesktopAlertDispatcher.class);

    private final String notifySendPath;

    public DesktopAlertDispatcher() {
        this(resolveNotifySend());
    }

    DesktopAlertDispatcher(String notifySendPath) {
        this.notifySendPath = notifySendPath;
    }

    @Override
    public void dispatch(RouteChangeEvent event) {
        if (notifySendPath == null) {
            LOG.debug("notify-send not found; skipping desktop alert");
            return;
        }
        if (!isLinux()) {
            LOG.debug("Desktop alerts unsupported on this OS; skipping");
            return;
        }
        String oldStr = event.oldIps().isEmpty() ? "(none)" : String.join(" -> ", event.oldIps());
        String newStr = event.newIps().isEmpty() ? "(none)" : String.join(" -> ", event.newIps());
        String body = event.host() + ": " + oldStr + " → " + newStr;
        ProcessBuilder builder = new ProcessBuilder(notifySendPath, "PINGUI route change", body);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warn("Desktop notification timed out");
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Desktop notification failed: {}", ex.getMessage());
        }
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().startsWith("linux");
    }

    private static String resolveNotifySend() {
        if (!isLinux()) {
            return null;
        }
        for (String dir : List.of("/usr/bin", "/bin", "/usr/local/bin")) {
            Path candidate = Path.of(dir, "notify-send");
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }
}

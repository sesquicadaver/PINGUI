package io.pingui.probe;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Uses the OS traceroute/tracert binary for cross-platform tracing without raw ICMP sockets.
 */
public final class ProcessRouteProbe implements RouteProbe {

    private final TraceCommandBuilder commandBuilder;
    private final boolean windows;

    ProcessRouteProbe() {
        this(TraceCommandFactory.forCurrentPlatform());
    }

    ProcessRouteProbe(TraceCommandBuilder commandBuilder) {
        this.commandBuilder = commandBuilder;
        this.windows = commandBuilder instanceof WindowsTracertCommand;
    }

    /** Package-private for tests: Linux probe with a fixed traceroute flavor. */
    ProcessRouteProbe(TracerouteFlavor flavor) {
        this(new LinuxTracerouteCommand(flavor));
    }

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) throws IOException {
        List<String> command = commandBuilder.buildCommand(targetHost, maxHops, timeoutSeconds);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> lines = new ArrayList<>();
        Charset outputCharset = windows ? Charset.defaultCharset() : StandardCharsets.UTF_8;
        Thread drainer = Thread.ofVirtual()
                .name("trace-output-" + targetHost)
                .start(() -> drainLines(process, lines, outputCharset));
        long waitMs = TraceProcessTiming.computeProcessWaitMs(windows, maxHops, timeoutSeconds);
        try {
            if (!process.waitFor(waitMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("traceroute timed out for " + targetHost);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("traceroute interrupted for " + targetHost, ex);
        } finally {
            joinDrainer(drainer);
        }
        if (process.exitValue() != 0 && process.exitValue() != 1) {
            throw new IOException("traceroute exited with code " + process.exitValue() + " for " + targetHost);
        }
        List<HopNode> nodes = windows ? WindowsTraceOutputParser.parse(lines) : UnixTraceOutputParser.parse(lines);
        if (nodes.isEmpty()) {
            throw new IOException(
                    "No hops parsed for " + targetHost + " (" + lines.size() + " tracert lines); check tracert output");
        }
        String targetIp = nodes.stream()
                .filter(HopNode::isReachable)
                .map(HopNode::ip)
                .reduce((first, second) -> second)
                .orElse(targetHost);
        return new RouteSnapshot(targetHost, targetIp, nodes);
    }

    /** Read subprocess stdout so tracert/traceroute cannot block on a full pipe buffer. */
    private static void drainLines(Process process, List<String> lines, Charset charset) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException ignored) {
            // process may be destroyed on timeout
        }
    }

    private static void joinDrainer(Thread drainer) {
        try {
            drainer.join(5000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // --- test / legacy delegates (package-private) ---

    static TracerouteFlavor detectFlavor(String osName, String versionOutput) {
        return TracerouteFlavorDetector.detectFlavor(osName, versionOutput);
    }

    static String resolveTracerouteExecutable(String osName, java.util.function.Predicate<String> absolutePathExists) {
        return TracerouteExecutables.resolveTracerouteExecutable(osName, absolutePathExists);
    }

    static String resolveTracertExecutable(
            String systemRoot, java.util.function.Predicate<java.nio.file.Path> executable) {
        return TracerouteExecutables.resolveTracertExecutable(systemRoot, executable);
    }

    static long computeProcessWaitMs(boolean windowsOs, int maxHops, double timeoutSeconds) {
        return TraceProcessTiming.computeProcessWaitMs(windowsOs, maxHops, timeoutSeconds);
    }

    static int windowsTracertWaitMs(double timeoutSeconds) {
        return TraceProcessTiming.windowsTracertWaitMs(timeoutSeconds);
    }

    List<String> buildCommand(String targetHost, int maxHops, double timeoutSeconds) {
        return commandBuilder.buildCommand(targetHost, maxHops, timeoutSeconds);
    }

    static List<HopNode> parseUnix(List<String> lines) {
        return UnixTraceOutputParser.parse(lines);
    }

    static List<HopNode> parseWindows(List<String> lines) {
        return WindowsTraceOutputParser.parse(lines);
    }

    static Double parseWindowsRtt(String line) {
        return WindowsTraceOutputParser.parseRtt(line);
    }
}

package io.pingui.probe;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses the OS traceroute/tracert binary for cross-platform tracing without raw ICMP sockets.
 */
public final class ProcessRouteProbe implements RouteProbe {
    /** BSD/macOS and classic Linux traceroute; GNU inetutils uses different flags (no {@code -n}). */
    enum TracerouteFlavor {
        GNU_INETUTILS,
        BSD
    }

    private static final Pattern UNIX_LINE =
            Pattern.compile("^\\s*(\\d+)\\s+([^\\s]+)(?:\\s+([0-9.]+)\\s*ms)?.*");
    private static final Pattern WINDOWS_LINE =
            Pattern.compile("^\\s*(\\d+)\\s+(?:\\d+\\s*ms\\s+)*([*]|\\d+\\.\\d+\\.\\d+\\.\\d+)\\s*.*");
    private static final Pattern WINDOWS_RTT_MS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*ms");

    private static volatile TracerouteFlavor cachedFlavor;

    private final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private final TracerouteFlavor flavor;

    ProcessRouteProbe() {
        this(resolveFlavor());
    }

    ProcessRouteProbe(TracerouteFlavor flavor) {
        this.flavor = flavor;
    }

    static TracerouteFlavor resolveFlavor() {
        TracerouteFlavor local = cachedFlavor;
        if (local != null) {
            return local;
        }
        synchronized (ProcessRouteProbe.class) {
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

    private static String readTracerouteVersion() {
        String traceroute = resolveTracerouteExecutable();
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

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) throws IOException {
        List<String> command = buildCommand(targetHost, maxHops, timeoutSeconds);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> lines = new ArrayList<>();
        Thread drainer =
                Thread.ofVirtual().name("trace-output-" + targetHost).start(() -> drainLines(process, lines));
        long waitMs = computeProcessWaitMs(windows, maxHops, timeoutSeconds);
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
        List<HopNode> nodes = windows ? parseWindows(lines) : parseUnix(lines);
        if (nodes.isEmpty()) {
            throw new IOException("No hops parsed for " + targetHost + "; is traceroute installed?");
        }
        String targetIp = nodes.stream()
                .filter(HopNode::isReachable)
                .map(HopNode::ip)
                .reduce((first, second) -> second)
                .orElse(targetHost);
        return new RouteSnapshot(targetHost, targetIp, nodes);
    }

    /** Read subprocess stdout so tracert/traceroute cannot block on a full pipe buffer. */
    private static void drainLines(Process process, List<String> lines) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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

    /** Max wait for the trace subprocess (tracert on Windows needs much longer than 0.5s * hops). */
    static long computeProcessWaitMs(boolean windows, int maxHops, double timeoutSeconds) {
        if (windows) {
            int perReplyMs = windowsTracertWaitMs(timeoutSeconds);
            // tracert sends 3 probes per hop by default
            return (long) maxHops * 3L * perReplyMs + 15_000L;
        }
        return (long) (timeoutSeconds * 1000 * (maxHops + 2) + 5000);
    }

    /** Per-reply timeout for {@code tracert -w} (Windows default is 4000 ms). */
    static int windowsTracertWaitMs(double timeoutSeconds) {
        return Math.max(4000, (int) Math.ceil(timeoutSeconds * 1000));
    }

    List<String> buildCommand(String targetHost, int maxHops, double timeoutSeconds) {
        if (windows) {
            int waitMs = windowsTracertWaitMs(timeoutSeconds);
            return List.of("tracert", "-h", String.valueOf(maxHops), "-w", String.valueOf(waitMs), targetHost);
        }
        String traceroute = resolveTracerouteExecutable();
        int waitSec = Math.max(1, (int) Math.ceil(timeoutSeconds));
        if (flavor == TracerouteFlavor.GNU_INETUTILS) {
            // GNU inetutils: no -n (DNS off by default); -n triggers exit 64 (EX_USAGE).
            return List.of(
                    traceroute,
                    "-m",
                    String.valueOf(maxHops),
                    "-w",
                    String.valueOf(waitSec),
                    "-q",
                    "1",
                    targetHost);
        }
        return List.of(
                traceroute,
                "-n",
                "-w",
                String.valueOf(waitSec),
                "-m",
                String.valueOf(maxHops),
                "-q",
                "1",
                targetHost);
    }

    static List<HopNode> parseUnix(List<String> lines) {
        List<HopNode> nodes = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = UNIX_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int hop = Integer.parseInt(matcher.group(1));
            String token = matcher.group(2);
            if ("*".equals(token)) {
                nodes.add(Models.timeout(hop));
                continue;
            }
            Double pingMs = matcher.group(3) != null ? Double.parseDouble(matcher.group(3)) : null;
            nodes.add(new HopNode(hop, token, pingMs, false));
        }
        return nodes;
    }

    static List<HopNode> parseWindows(List<String> lines) {
        List<HopNode> nodes = new ArrayList<>();
        for (String line : lines) {
            if (line.contains("Tracing") || line.contains("over a maximum")) {
                continue;
            }
            Matcher matcher = WINDOWS_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int hop = Integer.parseInt(matcher.group(1));
            String token = matcher.group(2);
            if ("*".equals(token)) {
                nodes.add(Models.timeout(hop));
                continue;
            }
            Double pingMs = parseWindowsRtt(line);
            nodes.add(new HopNode(hop, token, pingMs, false));
        }
        return nodes;
    }

    static Double parseWindowsRtt(String line) {
        Matcher matcher = WINDOWS_RTT_MS.matcher(line);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }
}

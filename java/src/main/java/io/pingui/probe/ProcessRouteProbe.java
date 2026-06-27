package io.pingui.probe;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses the OS traceroute/tracert binary for cross-platform tracing without raw ICMP sockets.
 */
public final class ProcessRouteProbe implements RouteProbe {
    private static final Pattern UNIX_LINE =
            Pattern.compile("^\\s*(\\d+)\\s+([^\\s]+)(?:\\s+([0-9.]+)\\s*ms)?.*");
    private static final Pattern WINDOWS_LINE =
            Pattern.compile("^\\s*(\\d+)\\s+(?:\\d+\\s*ms\\s+)*([*]|\\d+\\.\\d+\\.\\d+\\.\\d+)\\s*.*");

    private final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) throws IOException {
        List<String> command = buildCommand(targetHost, maxHops, timeoutSeconds);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        long waitMs = (long) (timeoutSeconds * 1000 * (maxHops + 2) + 5000);
        try {
            if (!process.waitFor(waitMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("traceroute timed out for " + targetHost);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("traceroute interrupted for " + targetHost, ex);
        }
        if (process.exitValue() != 0 && process.exitValue() != 1) {
            throw new IOException("traceroute exited with code " + process.exitValue() + " for " + targetHost);
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
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

    List<String> buildCommand(String targetHost, int maxHops, double timeoutSeconds) {
        if (windows) {
            int waitMs = Math.max(500, (int) (timeoutSeconds * 1000));
            return List.of("tracert", "-h", String.valueOf(maxHops), "-w", String.valueOf(waitMs), targetHost);
        }
        int waitSec = Math.max(1, (int) Math.ceil(timeoutSeconds));
        return List.of("traceroute", "-n", "-w", String.valueOf(waitSec), "-m", String.valueOf(maxHops), "-q", "1", targetHost);
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
            nodes.add(new HopNode(hop, token, null, false));
        }
        return nodes;
    }
}

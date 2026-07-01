package io.pingui.probe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cross-platform one-shot ping to a host (ping-only mode, no traceroute). */
public final class ProcessHostPing {
    private static final Pattern RTT_EXACT_MS =
            Pattern.compile("time=(\\d+(?:\\.\\d+)?)\\s*(?:ms|мс)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RTT_SUB_MS = Pattern.compile("time<\\s*1\\s*(?:ms|мс)", Pattern.CASE_INSENSITIVE);

    private final boolean windows =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    public OptionalDouble pingOnce(String target, double timeoutSeconds) throws IOException {
        return pingOnce(target, null, timeoutSeconds);
    }

    public OptionalDouble pingOnce(String target, io.pingui.config.PingExpertEntry expert, double timeoutSeconds)
            throws IOException {
        List<String> command = buildCommand(target, timeoutSeconds, windows, expert);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> lines = new ArrayList<>();
        Charset charset = windows ? Charset.defaultCharset() : StandardCharsets.UTF_8;
        Thread drainer =
                Thread.ofVirtual().name("ping-output-" + target).start(() -> drainLines(process, lines, charset));
        long waitMs = Math.max(500, (long) Math.ceil(timeoutSeconds * 1000) + 500);
        try {
            if (!process.waitFor(waitMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return OptionalDouble.empty();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return OptionalDouble.empty();
        } finally {
            joinDrainer(drainer);
        }
        if (process.exitValue() != 0) {
            return OptionalDouble.empty();
        }
        return parseRtt(lines);
    }

    static List<String> buildCommand(
            String target, double timeoutSeconds, boolean windows, io.pingui.config.PingExpertEntry expert) {
        if (windows) {
            String ping = resolvePingExecutable();
            int waitMs = Math.max(1000, (int) Math.ceil(timeoutSeconds * 1000));
            return List.of(ping, "-n", "1", "-w", String.valueOf(waitMs), target);
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            int waitMs = Math.max(1000, (int) Math.ceil(timeoutSeconds * 1000));
            List<String> command = new ArrayList<>(List.of("ping", "-c", "1", "-W", String.valueOf(waitMs)));
            appendExpertArgs(command, expert, target);
            command.add(target);
            return List.copyOf(command);
        }
        int waitSec = Math.max(1, (int) Math.ceil(timeoutSeconds));
        List<String> command = new ArrayList<>(List.of("ping", "-n", "-c", "1", "-W", String.valueOf(waitSec)));
        appendExpertArgs(command, expert, target);
        command.add(target);
        return List.copyOf(command);
    }

    static List<String> buildCommand(String target, double timeoutSeconds, boolean windows) {
        return buildCommand(target, timeoutSeconds, windows, null);
    }

    private static void appendExpertArgs(List<String> command, io.pingui.config.PingExpertEntry expert, String target) {
        List<String> args = ExpertPingArgs.forTarget(target, expert);
        if (!args.isEmpty()) {
            command.addAll(args);
        }
    }

    static String resolvePingExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null && !systemRoot.isBlank()) {
            Path ping = Path.of(systemRoot, "System32", "ping.exe");
            if (Files.isExecutable(ping)) {
                return ping.toString();
            }
        }
        return "ping";
    }

    static OptionalDouble parseRtt(List<String> lines) {
        for (String line : lines) {
            Matcher subMs = RTT_SUB_MS.matcher(line);
            if (subMs.find()) {
                return OptionalDouble.of(0.5);
            }
            Matcher exact = RTT_EXACT_MS.matcher(line);
            if (exact.find()) {
                return OptionalDouble.of(Double.parseDouble(exact.group(1)));
            }
        }
        return OptionalDouble.empty();
    }

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
}

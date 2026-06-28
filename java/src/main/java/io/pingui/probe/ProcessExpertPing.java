package io.pingui.probe;

import io.pingui.config.PingExpertEntry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs one-shot {@code ping -c 1} with validated expert flags. */
public final class ProcessExpertPing {
    private static final Pattern RTT_PATTERN =
            Pattern.compile("time[=<]([0-9]+(?:\\.[0-9]+)?)\\s*ms", Pattern.CASE_INSENSITIVE);

    public OptionalDouble pingOnce(String target, PingExpertEntry expert, double timeoutSeconds) throws IOException {
        List<String> command = buildCommand(target, expert, timeoutSeconds);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
        }
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
        }
        if (process.exitValue() != 0) {
            return OptionalDouble.empty();
        }
        Matcher matcher = RTT_PATTERN.matcher(output);
        if (matcher.find()) {
            return OptionalDouble.of(Double.parseDouble(matcher.group(1)));
        }
        return OptionalDouble.empty();
    }

    static List<String> buildCommand(String target, PingExpertEntry expert, double timeoutSeconds) {
        List<String> command = new ArrayList<>();
        command.add("ping");
        command.add("-c");
        command.add("1");
        command.add("-n");
        command.add("-W");
        command.add(String.valueOf(Math.max(1, (int) Math.ceil(timeoutSeconds))));
        if (expert != null && expert.args() != null) {
            command.addAll(expert.args());
        }
        command.add(target);
        return List.copyOf(command);
    }
}

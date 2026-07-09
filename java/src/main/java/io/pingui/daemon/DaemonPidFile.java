package io.pingui.daemon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** PID file contract for Java headless daemon (P12-011). */
public final class DaemonPidFile {
    private DaemonPidFile() {}

    public static void write(Path path, long pid) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, Long.toString(pid) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public static OptionalLong read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            return OptionalLong.empty();
        }
        String raw = Files.readString(path, StandardCharsets.UTF_8).strip();
        if (raw.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(raw.split("\\s+")[0]));
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid PID file content: " + path, ex);
        }
    }

    public static boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    public static boolean isRunning(Path path) throws IOException {
        OptionalLong pid = read(path);
        return pid.isPresent() && isProcessAlive(pid.getAsLong());
    }

    public static boolean stop(Path path, long gracefulWaitMs) throws IOException, InterruptedException {
        OptionalLong pid = read(path);
        if (pid.isEmpty()) {
            return false;
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid.getAsLong());
        if (handle.isEmpty() || !handle.get().isAlive()) {
            Files.deleteIfExists(path);
            return false;
        }
        handle.get().destroy();
        long deadline = System.nanoTime() + gracefulWaitMs * 1_000_000L;
        while (System.nanoTime() < deadline && handle.get().isAlive()) {
            Thread.sleep(50);
        }
        if (handle.get().isAlive()) {
            handle.get().destroyForcibly();
        }
        Files.deleteIfExists(path);
        return true;
    }

    public static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}

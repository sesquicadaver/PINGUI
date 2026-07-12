package io.pingui.telemetry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local JSONL archive sink with UTC day and size rotation (P16-021 / ADR_TELEMETRY).
 *
 * <p>Default off — callers register explicitly. File pattern: {@code telemetry.jsonl.YYYY-MM-DD}
 * with size overflow parts {@code telemetry.jsonl.YYYY-MM-DD.N}. Failures are logged; methods never
 * throw into the poll / bus path.
 */
public final class JsonlRotateSink implements TelemetrySink {
    public static final String ID = "jsonl";
    public static final String FILE_PREFIX = "telemetry.jsonl.";
    /** Default max bytes per active part before size rotation (10 MiB). */
    public static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;

    private static final Logger LOG = Logger.getLogger(JsonlRotateSink.class.getName());

    private final Path directory;
    private final long maxBytes;
    private final Clock clock;

    private final Object lock = new Object();
    private LocalDate openDate;
    private int partIndex;
    private Path openPath;
    private BufferedWriter writer;
    private long bytesWritten;

    public JsonlRotateSink(Path directory) {
        this(directory, DEFAULT_MAX_BYTES, Clock.systemUTC());
    }

    public JsonlRotateSink(Path directory, long maxBytes) {
        this(directory, maxBytes, Clock.systemUTC());
    }

    /** Package-visible for tests (injectable clock for day boundaries). */
    JsonlRotateSink(Path directory, long maxBytes, Clock clock) {
        this.directory =
                Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (maxBytes < 1L) {
            throw new IllegalArgumentException("maxBytes must be >= 1");
        }
        this.maxBytes = maxBytes;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onSample(MetricSample sample) {
        if (sample == null) {
            return;
        }
        writeLine(sample.toJson());
    }

    @Override
    public void onEvent(TelemetryEvent event) {
        if (event == null) {
            return;
        }
        writeLine(event.toJson());
    }

    @Override
    public void close() {
        synchronized (lock) {
            closeWriterQuietly();
        }
    }

    /** Active file path for tests / diagnostics (may be null before first write). */
    Path currentPath() {
        synchronized (lock) {
            return openPath;
        }
    }

    private void writeLine(String json) {
        String line = json + "\n";
        byte[] encoded = line.getBytes(StandardCharsets.UTF_8);
        synchronized (lock) {
            try {
                ensureWriter(encoded.length);
                writer.write(line);
                writer.flush();
                bytesWritten += encoded.length;
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "JsonlRotateSink write failed: " + openPath, ex);
                closeWriterQuietly();
            }
        }
    }

    private void ensureWriter(int upcomingBytes) throws IOException {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        boolean dayChanged = writer == null || openDate == null || !openDate.equals(today);
        boolean sizeExceeded = writer != null && bytesWritten > 0 && bytesWritten + upcomingBytes > maxBytes;
        if (dayChanged) {
            openPart(today, 0);
            // Existing oversized base part: advance to a free part for this day.
            while (bytesWritten > 0 && bytesWritten + upcomingBytes > maxBytes) {
                openPart(today, partIndex + 1);
            }
            return;
        }
        if (sizeExceeded) {
            openPart(today, partIndex + 1);
        }
    }

    private void openPart(LocalDate day, int part) throws IOException {
        closeWriterQuietly();
        Files.createDirectories(directory);
        openDate = day;
        partIndex = part;
        openPath = pathFor(directory, day, part);
        long existing = Files.exists(openPath) ? Files.size(openPath) : 0L;
        writer = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(openPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8));
        bytesWritten = existing;
    }

    static Path pathFor(Path directory, LocalDate day, int partIndex) {
        String base = FILE_PREFIX + day;
        if (partIndex <= 0) {
            return directory.resolve(base);
        }
        return directory.resolve(base + "." + partIndex);
    }

    private void closeWriterQuietly() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "JsonlRotateSink close failed: " + openPath, ex);
        } finally {
            writer = null;
            bytesWritten = 0L;
        }
    }
}

package io.pingui.telemetry;

import io.pingui.persistence.SessionDatabase;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-shot purge of local telemetry archives older than {@code retentionDays} (P16-022).
 *
 * <p>SQLite: {@code telemetry_sample}/{@code telemetry_event} by {@code observed_at}. JSONL: files
 * matching {@code telemetry.jsonl.yyyy-MM-dd[.N]} whose day is older than the retention window.
 */
public final class TelemetryRetentionJob {
    private static final Pattern JSONL_DAY =
            Pattern.compile("^telemetry\\.jsonl\\.(\\d{4}-\\d{2}-\\d{2})(?:\\.\\d+)?$");

    private TelemetryRetentionJob() {}

    /**
     * @param database optional session DB (may be {@code null})
     * @param jsonlDirectory optional JSONL directory (may be {@code null})
     * @param retentionDays keep this many days (must be &gt;= 1)
     */
    public static Result run(SessionDatabase database, Path jsonlDirectory, int retentionDays, Clock clock) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be >= 1");
        }
        Objects.requireNonNull(clock, "clock");
        if (database == null && jsonlDirectory == null) {
            throw new IllegalArgumentException("session database and/or jsonl directory required");
        }
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        LocalDate keepFrom = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(retentionDays);

        int samples = 0;
        int events = 0;
        if (database != null) {
            samples = database.deleteTelemetrySamplesBefore(cutoff);
            events = database.deleteTelemetryEventsBefore(cutoff);
        }
        int jsonl = 0;
        if (jsonlDirectory != null) {
            jsonl = purgeJsonlFiles(jsonlDirectory, keepFrom);
        }
        return new Result(samples, events, jsonl);
    }

    static int purgeJsonlFiles(Path directory, LocalDate keepFromInclusive) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(keepFromInclusive, "keepFromInclusive");
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String name = path.getFileName().toString();
                Matcher matcher = JSONL_DAY.matcher(name);
                if (!matcher.matches()) {
                    continue;
                }
                LocalDate day;
                try {
                    day = LocalDate.parse(matcher.group(1));
                } catch (DateTimeParseException ex) {
                    continue;
                }
                if (day.isBefore(keepFromInclusive)) {
                    Files.deleteIfExists(path);
                    deleted++;
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to purge JSONL in " + directory, ex);
        }
        return deleted;
    }

    /** Counts of purged artifacts. */
    public record Result(int samplesDeleted, int eventsDeleted, int jsonlFilesDeleted) {}
}

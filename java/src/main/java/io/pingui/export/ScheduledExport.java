package io.pingui.export;

import io.pingui.persistence.SessionDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;

/**
 * Cron-friendly one-shot CSV+HTML export with schedule-stamped filenames (P15-030).
 *
 * <p>Does not sleep or loop — intended for system cron invoking the CLI periodically.
 */
public final class ScheduledExport {
    private static final DateTimeFormatter HOURLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAILY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private ScheduledExport() {}

    /** Paths written by a successful scheduled export. */
    public record Result(Path csvPath, Path htmlPath) {}

    /**
     * Write both CSV and HTML reports under {@code exportDir}.
     *
     * @param clock UTC-based clock for deterministic stamps in tests
     */
    public static Result run(SessionDatabase database, Path exportDir, ExportSchedulePeriod period, Clock clock)
            throws IOException {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(exportDir, "exportDir");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(clock, "clock");
        Files.createDirectories(exportDir);
        String stamp = stamp(period, Instant.now(clock));
        String base = "pingui-" + period.fileToken() + "-" + stamp;
        Path csvPath = exportDir.resolve(base + ".csv");
        Path htmlPath = exportDir.resolve(base + ".html");
        SessionReportExporter.exportCsv(database, csvPath);
        SessionReportExporter.exportHtml(database, htmlPath);
        return new Result(csvPath, htmlPath);
    }

    /** UTC filename stamp for the given schedule period. */
    public static String stamp(ExportSchedulePeriod period, Instant instant) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(instant, "instant");
        return switch (period) {
            case HOURLY -> HOURLY.format(instant);
            case DAILY -> DAILY.format(instant);
            case WEEKLY -> {
                var zoned = instant.atZone(ZoneOffset.UTC);
                WeekFields iso = WeekFields.ISO;
                int week = zoned.get(iso.weekOfWeekBasedYear());
                int year = zoned.get(iso.weekBasedYear());
                yield String.format(Locale.ROOT, "%04d-W%02d", year, week);
            }
        };
    }
}

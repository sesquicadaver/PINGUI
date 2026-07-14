package io.pingui.export;

import java.util.Locale;

/** Cron-friendly export stamp granularity (P15-030). */
public enum ExportSchedulePeriod {
    HOURLY,
    DAILY,
    WEEKLY;

    /**
     * Parse CLI {@code --export-schedule} value.
     *
     * @throws IllegalArgumentException when value is unknown
     */
    public static ExportSchedulePeriod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing value for --export-schedule");
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "hourly", "hour" -> HOURLY;
            case "daily", "day" -> DAILY;
            case "weekly", "week" -> WEEKLY;
            default -> throw new IllegalArgumentException(
                    "Unknown --export-schedule '" + raw + "' (use hourly, daily, or weekly)");
        };
    }

    /** Lowercase token used in report filenames. */
    public String fileToken() {
        return name().toLowerCase(Locale.ROOT);
    }
}

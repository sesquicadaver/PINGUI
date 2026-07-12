package io.pingui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.HostSessionData;
import io.pingui.persistence.SessionDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScheduledExportTest {

    @TempDir
    Path tempDir;

    @Test
    void stampFormatsUtcPeriods() {
        Instant instant = Instant.parse("2026-07-12T15:30:00Z");
        assertEquals("2026-07-12T15", ScheduledExport.stamp(ExportSchedulePeriod.HOURLY, instant));
        assertEquals("2026-07-12", ScheduledExport.stamp(ExportSchedulePeriod.DAILY, instant));
        assertEquals("2026-W28", ScheduledExport.stamp(ExportSchedulePeriod.WEEKLY, instant));
    }

    @Test
    void parsePeriodAcceptsAliases() {
        assertEquals(ExportSchedulePeriod.DAILY, ExportSchedulePeriod.parse("daily"));
        assertEquals(ExportSchedulePeriod.HOURLY, ExportSchedulePeriod.parse("hour"));
        assertEquals(ExportSchedulePeriod.WEEKLY, ExportSchedulePeriod.parse("WEEK"));
        assertEquals("daily", ExportSchedulePeriod.DAILY.fileToken());
        assertThrows(IllegalArgumentException.class, () -> ExportSchedulePeriod.parse("monthly"));
    }

    @Test
    void runWritesCsvAndHtmlWithDailyStamp() throws Exception {
        Path dbPath = tempDir.resolve("session.db");
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            HostSessionData data = new HostSessionData();
            data.setEnabled(true);
            data.setCurrentRoute(List.of(new HopNode(1, "10.0.0.1", 5.0, false)));
            database.save("8.8.8.8", data);
        }

        Path out = tempDir.resolve("reports");
        Clock clock = Clock.fixed(Instant.parse("2026-07-12T09:00:00Z"), ZoneOffset.UTC);
        ScheduledExport.Result result;
        try (SessionDatabase database = new SessionDatabase(dbPath)) {
            result = ScheduledExport.run(database, out, ExportSchedulePeriod.DAILY, clock);
        }

        assertEquals(out.resolve("pingui-daily-2026-07-12.csv"), result.csvPath());
        assertEquals(out.resolve("pingui-daily-2026-07-12.html"), result.htmlPath());
        assertTrue(Files.isRegularFile(result.csvPath()));
        assertTrue(Files.isRegularFile(result.htmlPath()));
        assertTrue(Files.readString(result.csvPath()).contains("10.0.0.1"));
        assertTrue(Files.readString(result.htmlPath()).contains("8.8.8.8"));
    }
}

package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostProblemSummary;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProblemDetailsDialogTest {
    @Test
    void formatDurationHumanReadable() {
        assertEquals("0 с", ProblemDetailsDialog.formatDuration(Duration.ZERO));
        assertEquals("45 с", ProblemDetailsDialog.formatDuration(Duration.ofSeconds(45)));
        assertEquals("1 хв 39 с", ProblemDetailsDialog.formatDuration(Duration.ofSeconds(99)));
        assertEquals("2 год 3 хв 4 с", ProblemDetailsDialog.formatDuration(Duration.ofSeconds(2 * 3600 + 3 * 60 + 4)));
    }

    @Test
    void formatBodyIncludesRequiredFields() {
        HostProblemSummary summary = new HostProblemSummary(
                "8.8.8.8",
                "endpoint_down",
                true,
                2,
                Duration.ofSeconds(99),
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:01:39Z"),
                HostProblemSummary.STATE_RESOLVED,
                HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN);
        String body = ProblemDetailsDialog.formatBody(summary);
        assertTrue(body.contains("Опис: "));
        assertTrue(body.contains("endpoint_down"));
        assertTrue(body.contains("Повтори (FIRING): 2"));
        assertTrue(body.contains("Макс. тривалість: 1 хв 39 с"));
        assertTrue(body.contains("Стан: resolved"));
    }
}

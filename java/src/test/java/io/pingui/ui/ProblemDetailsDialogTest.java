package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostProblemSummary;
import io.pingui.monitor.ProblemCorrelation;
import io.pingui.monitor.ProblemCorrelationScope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    @Test
    void formatBodyIncludesCorrelationNarrative() {
        HostProblemSummary summary = new HostProblemSummary(
                "8.8.8.8",
                "endpoint_down",
                true,
                1,
                Duration.ofSeconds(10),
                Instant.parse("2026-09-03T10:00:00Z"),
                null,
                HostProblemSummary.STATE_FIRING,
                HostProblemSummary.DESCRIPTION_ENDPOINT_DOWN);
        ProblemCorrelation correlation = new ProblemCorrelation(
                3,
                8,
                List.of("a", "b", "c"),
                Optional.of("198.51.100.10"),
                Optional.of(2),
                Optional.empty(),
                Optional.empty(),
                ProblemCorrelationScope.ISP,
                true,
                Duration.ofSeconds(45));
        String body = ProblemDetailsDialog.formatBody(summary, Optional.of(correlation));
        assertTrue(body.contains("Кореляція між хостами"));
        assertTrue(body.contains("3 із 8"));
        assertTrue(body.contains("198.51.100.10"));
        assertTrue(body.contains("провайдер"));
    }
}

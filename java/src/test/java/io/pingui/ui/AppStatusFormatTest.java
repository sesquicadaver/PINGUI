package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.i18n.UiI18n;
import io.pingui.i18n.UiLocale;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppStatusFormatTest {
    @BeforeEach
    void ukLocale() {
        UiI18n.setLocale(UiLocale.UK);
    }

    @Test
    void monitoringIncludesActiveCountsAndCycleAge() {
        Instant now = Instant.parse("2026-09-05T10:00:05Z");
        Instant cycle = Instant.parse("2026-09-05T10:00:03Z");
        String text = AppStatusFormat.monitoring(true, 8, 10, cycle, now);
        assertTrue(text.contains("8"));
        assertTrue(text.contains("10"));
        assertTrue(text.contains("2"));
        assertTrue(text.toLowerCase().contains("monitoring") || text.contains("Monitoring"));
    }

    @Test
    void missingCycleUsesPlaceholder() {
        String text = AppStatusFormat.monitoring(false, 0, 0, null, Instant.now());
        assertEquals(UiI18n.get("status.mon.cycle_na"), AppStatusFormat.formatCycleAge(null, Instant.now()));
        assertTrue(text.contains(UiI18n.get("status.mon.inactive")));
    }
}

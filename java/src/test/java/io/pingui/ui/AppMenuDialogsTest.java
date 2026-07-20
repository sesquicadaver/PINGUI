package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppMenuDialogsTest {
    @Test
    void aboutSummaryMentionsPersistenceAndTelemetry() {
        String about = AppMenuDialogs.aboutSummary();
        assertFalse(about.contains("лише в RAM"));
        assertTrue(about.contains("База даних"));
        assertTrue(about.contains("Телеметрія"));
    }

    @Test
    void helpTextDocumentsTelemetryMenuAndSessionVsTelemetrySqlite() {
        String help = AppMenuDialogs.helpText();
        assertTrue(help.contains("Телеметрія…"));
        assertTrue(help.contains("Експорт зараз…"));
        assertTrue(help.contains("--export-report"));
        assertTrue(help.contains("Ctrl/Cmd+S"));
        assertTrue(help.contains("Ctrl/Cmd+N"));
        assertTrue(help.contains("Гарячі клавіші"));
        assertTrue(help.contains("persistence.session_db"));
        assertTrue(help.contains("telemetry.sqlite"));
        assertTrue(help.contains("--telemetry-syslog"));
        assertTrue(help.contains("MTU wizard"));
        assertTrue(help.contains("Self-check"));
        assertFalse(help.contains("у розробці"));
    }
}

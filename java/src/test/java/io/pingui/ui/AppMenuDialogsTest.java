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
        assertTrue(help.contains("persistence.session_db"));
        assertTrue(help.contains("telemetry.sqlite"));
        assertTrue(help.contains("--telemetry-syslog"));
    }
}

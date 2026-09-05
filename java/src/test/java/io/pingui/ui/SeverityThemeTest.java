package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.pingui.monitor.Severity;
import org.junit.jupiter.api.Test;

class SeverityThemeTest {
    @Test
    void criticalUsesRedPastelOnly() {
        assertEquals("#ffcdd2", SeverityTheme.rowColor(Severity.CRITICAL));
        assertFalse(SeverityTheme.rowColor(Severity.WARNING).equals("#ffcdd2"));
        assertFalse(SeverityTheme.rowColor(Severity.NOTICE).equals("#ffcdd2"));
        assertFalse(SeverityTheme.rowColor(Severity.INFO).equals("#ffcdd2"));
        assertFalse(SeverityTheme.rowColor(Severity.MUTED).equals("#ffcdd2"));
    }

    @Test
    void styleClassesAreStable() {
        assertEquals("pingui-severity-critical", SeverityTheme.styleClass(Severity.CRITICAL));
        assertEquals("pingui-severity-warning", SeverityTheme.styleClass(Severity.WARNING));
        assertEquals("pingui-severity-notice", SeverityTheme.styleClass(Severity.NOTICE));
        assertEquals("pingui-severity-info", SeverityTheme.styleClass(Severity.INFO));
        assertEquals("pingui-severity-muted", SeverityTheme.styleClass(Severity.MUTED));
    }

    @Test
    void accentColorsPreferContrastOverPastelYellow() {
        assertEquals("#b71c1c", SeverityTheme.accentColor(Severity.CRITICAL));
        assertEquals("#bf360c", SeverityTheme.accentColor(Severity.WARNING));
        assertEquals("#7a5c00", SeverityTheme.accentColor(Severity.NOTICE));
        assertFalse(SeverityTheme.accentColor(Severity.NOTICE).equalsIgnoreCase("#f9a825"));
    }
}

package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.i18n.UiI18n;
import io.pingui.i18n.UiLocale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppAcceleratorsTest {
    @BeforeEach
    void ukLocale() {
        UiI18n.setLocale(UiLocale.UK);
    }
    @Test
    void bindingsUseShortcutOrFunctionKeyNotBareLetters() {
        assertEquals("Shortcut+S", AppAccelerators.SAVE);
        assertEquals("Shortcut+N", AppAccelerators.ADD_HOST);
        assertEquals("F1", AppAccelerators.HELP);
        assertFalse(AppAccelerators.SAVE.matches("^[A-Za-z]$"));
        assertFalse(AppAccelerators.ADD_HOST.matches("^[A-Za-z]$"));
    }

    @Test
    void helpSectionDocumentsSaveAddAndHelp() {
        String section = AppAccelerators.helpSection();
        assertTrue(section.contains("F1"));
        assertTrue(section.contains("Ctrl/Cmd+S"));
        assertTrue(section.contains("Ctrl/Cmd+N"));
        assertTrue(section.contains("Зберегти"));
        assertTrue(section.contains("Додати"));
    }
}

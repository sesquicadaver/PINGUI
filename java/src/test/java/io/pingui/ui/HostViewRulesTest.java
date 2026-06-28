package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.HostEntry;
import org.junit.jupiter.api.Test;

class HostViewRulesTest {
    @Test
    void matchesConfiguredHostCaseInsensitive() {
        assertTrue(HostViewRules.matches("fuck.you"));
        assertTrue(HostViewRules.matches("FUCK.YOU"));
        assertTrue(HostViewRules.matches("  fuck.you  "));
        assertFalse(HostViewRules.matches("8.8.8.8"));
    }

    @Test
    void messageOnlyForConfiguredHost() {
        assertEquals("fuck yourself, mazafaka", HostViewRules.messageFor("fuck.you"));
        assertNull(HostViewRules.messageFor("1.1.1.1"));
    }

    @Test
    void entriesForConfigOmitsEasterEggHost() {
        var entries =
                HostViewRules.entriesForConfig(
                        java.util.List.of(
                                HostEntry.basic("8.8.8.8", true), HostEntry.basic("fuck.you", false)));
        assertEquals(1, entries.size());
        assertEquals("8.8.8.8", entries.get(0).address());
    }
}

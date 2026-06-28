package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HostEasterEggTest {
    @Test
    void matchesEasterEggHostCaseInsensitive() {
        assertTrue(HostEasterEgg.matches("fuck.you"));
        assertTrue(HostEasterEgg.matches("FUCK.YOU"));
        assertTrue(HostEasterEgg.matches("  fuck.you  "));
        assertFalse(HostEasterEgg.matches("8.8.8.8"));
    }

    @Test
    void messageOnlyForEasterEggHost() {
        assertEquals("fuck yourself, mazafaka", HostEasterEgg.messageFor("fuck.you"));
        assertNull(HostEasterEgg.messageFor("1.1.1.1"));
    }
}

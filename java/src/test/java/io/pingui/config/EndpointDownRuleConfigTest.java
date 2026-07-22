package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EndpointDownRuleConfigTest {
    @Test
    void presetsAndUkrainianAliases() {
        assertEquals(5, EndpointDownRuleConfig.fromPreset("спокійно", true).failAfter());
        assertEquals(3, EndpointDownRuleConfig.fromPreset("збалансовано", true).failAfter());
        assertEquals(2, EndpointDownRuleConfig.fromPreset("чутливо", true).failAfter());
        assertEquals(3, EndpointDownRuleConfig.fromPreset("  ", false).failAfter());
        assertEquals(3, EndpointDownRuleConfig.fromPreset(null, false).failAfter());
        assertThrows(IllegalArgumentException.class, () -> EndpointDownRuleConfig.fromPreset("aggressive", true));
    }

    @Test
    void matchingPresetAndDefaultDisabled() {
        assertEquals("calm", EndpointDownRuleConfig.calm(true).matchingPreset());
        assertEquals("balanced", EndpointDownRuleConfig.balanced(false).matchingPreset());
        assertEquals("sensitive", EndpointDownRuleConfig.sensitive(true).matchingPreset());
        assertEquals("", new EndpointDownRuleConfig(true, 4, 2, 20).matchingPreset());
        assertTrue(EndpointDownRuleConfig.disabled().isDefaultDisabled());
        assertFalse(EndpointDownRuleConfig.balanced(true).isDefaultDisabled());
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new EndpointDownRuleConfig(true, 0, 2, 15));
        assertThrows(IllegalArgumentException.class, () -> new EndpointDownRuleConfig(true, 3, 0, 15));
        assertThrows(IllegalArgumentException.class, () -> new EndpointDownRuleConfig(true, 3, 2, -1));
    }
}

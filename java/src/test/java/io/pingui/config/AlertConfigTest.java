package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AlertConfigTest {
    @Test
    void channelCtorDefaultsRulesOff() {
        AlertConfig cfg = new AlertConfig(true, " https://hooks.example/x ", 7);
        assertTrue(cfg.desktopAlerts());
        assertEquals("https://hooks.example/x", cfg.normalizedWebhook());
        assertEquals(7, cfg.maxAlertsPerHour());
        assertFalse(cfg.notifyResolved());
        assertFalse(cfg.endpointDown().enabled());
        assertTrue(cfg.hasYamlContent());
    }

    @Test
    void hasYamlContentForNotifyOrRulesOnly() {
        assertFalse(AlertConfig.disabled().hasYamlContent());
        assertTrue(new AlertConfig(false, null, 10, true, EndpointDownRuleConfig.disabled()).hasYamlContent());
        assertTrue(new AlertConfig(false, null, 10, false, EndpointDownRuleConfig.balanced(true)).hasYamlContent());
        assertTrue(new AlertConfig(false, null, 11, false, EndpointDownRuleConfig.disabled()).hasYamlContent());
    }

    @Test
    void nullEndpointDownBecomesDisabled() {
        AlertConfig cfg = new AlertConfig(false, "  ", 10, false, null);
        assertNull(cfg.normalizedWebhook());
        assertTrue(cfg.endpointDown().isDefaultDisabled());
        assertFalse(cfg.isEnabled());
    }

    @Test
    void rejectsInvalidRateLimit() {
        assertThrows(IllegalArgumentException.class, () -> new AlertConfig(false, null, 0));
    }

    @Test
    void toRedactedStringIncludesRulesFlags() {
        String text = new AlertConfig(false, null, 10, true, EndpointDownRuleConfig.sensitive(true)).toRedactedString();
        assertTrue(text.contains("notify_resolved=true"));
        assertTrue(text.contains("endpoint_down=on"));
    }
}

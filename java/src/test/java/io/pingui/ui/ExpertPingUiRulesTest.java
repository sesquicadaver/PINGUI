package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpertPingUiRulesTest {
    @Test
    void flowLabelAllowedForIpv6FamilyChoice() {
        assertTrue(ExpertPingUiRules.flowLabelAllowed("8.8.8.8", ExpertPingUiRules.AF_IPV6));
    }

    @Test
    void flowLabelDisallowedForDefaultIpv4FamilyChoice() {
        assertFalse(ExpertPingUiRules.flowLabelAllowed("example.com", ExpertPingUiRules.AF_IPV4));
    }

    @Test
    void flowLabelAllowedForIpv6LiteralWithoutExplicitFamily() {
        assertTrue(ExpertPingUiRules.flowLabelAllowed("2001:db8::1", null));
    }
}

package io.pingui.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpertPingUiRulesTest {
    @Test
    void flowLabelAllowedForIpv6LiteralWithoutExplicitAf() {
        assertTrue(ExpertPingUiRules.flowLabelAllowed("2001:db8::1", ExpertPingUiRules.AF_IPV6));
        assertTrue(ExpertPingUiRules.flowLabelAllowed("2001:db8::1", "—"));
        assertFalse(ExpertPingUiRules.flowLabelAllowed("2001:db8::1", ExpertPingUiRules.AF_IPV4));
    }

    @Test
    void flowLabelDisallowedForIpv4Target() {
        assertFalse(ExpertPingUiRules.flowLabelAllowed("8.8.8.8", "—"));
        assertFalse(ExpertPingUiRules.flowLabelAllowed("8.8.8.8", ExpertPingUiRules.AF_IPV4));
        assertTrue(ExpertPingUiRules.flowLabelAllowed("8.8.8.8", ExpertPingUiRules.AF_IPV6));
    }

    @Test
    void flowLabelDisallowedForHostnameUntilIpv6Af() {
        assertFalse(ExpertPingUiRules.flowLabelAllowed("example.com", "—"));
        assertTrue(ExpertPingUiRules.flowLabelAllowed("example.com", ExpertPingUiRules.AF_IPV6));
    }
}

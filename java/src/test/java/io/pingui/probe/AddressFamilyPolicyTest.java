package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.config.HostAddressKind;
import io.pingui.config.PingExpertEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AddressFamilyPolicyTest {

    @Test
    void kindOfRecognizesLiteralsAndHostname() {
        assertEquals(HostAddressKind.IPV4, AddressFamilyPolicy.kindOf("8.8.8.8"));
        assertEquals(HostAddressKind.IPV6, AddressFamilyPolicy.kindOf("2001:db8::1"));
        assertEquals(HostAddressKind.HOSTNAME, AddressFamilyPolicy.kindOf("example.com"));
    }

    @Test
    void requiresProcessTraceOnlyForIpv6Literal() {
        assertTrue(AddressFamilyPolicy.requiresProcessTrace("2001:db8::1"));
        assertFalse(AddressFamilyPolicy.requiresProcessTrace("8.8.8.8"));
        assertFalse(AddressFamilyPolicy.requiresProcessTrace("example.com"));
        assertThrows(ConfigError.class, () -> AddressFamilyPolicy.requiresProcessTrace(" "));
    }

    @Test
    void alignExpertReplacesUiAfWithHopLiteral() {
        PingExpertEntry expert = new PingExpertEntry(true, List.of("-6", "-s", "64"));
        PingExpertEntry v4 = AddressFamilyPolicy.alignExpertToTarget(expert, "10.0.0.1");
        assertEquals(List.of("-4", "-s", "64"), v4.args());
        PingExpertEntry v6 = AddressFamilyPolicy.alignExpertToTarget(expert, "2001:db8::2");
        assertEquals(List.of("-6", "-s", "64"), v6.args());
    }

    @Test
    void alignExpertLeavesHostnameWithoutForcedAf() {
        PingExpertEntry expert = new PingExpertEntry(true, List.of("-6", "-n"));
        PingExpertEntry aligned = AddressFamilyPolicy.alignExpertToTarget(expert, "router.local");
        assertEquals(List.of("-n"), aligned.args());
    }
}

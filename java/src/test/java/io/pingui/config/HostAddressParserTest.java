package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HostAddressParserTest {

    @Test
    void normalizeIpv4Unchanged() {
        assertEquals("8.8.8.8", HostAddressParser.normalize("8.8.8.8"));
        assertEquals(HostAddressKind.IPV4, HostAddressParser.kindOf("8.8.8.8"));
    }

    @Test
    void normalizeHostnameUnchanged() {
        assertEquals("example.com", HostAddressParser.normalize("example.com"));
        assertEquals(HostAddressKind.HOSTNAME, HostAddressParser.kindOf("example.com"));
    }

    @Test
    void normalizeIpv6ToCanonicalForm() {
        assertEquals("2001:db8::1", HostAddressParser.normalize("2001:DB8:0:0:0:0:0:1"));
        assertEquals("2001:db8::1", HostAddressParser.normalize("[2001:db8::1]"));
        assertEquals(HostAddressKind.IPV6, HostAddressParser.kindOf("2001:db8::1"));
    }

    @Test
    void normalizeLoopbackIpv6() {
        assertEquals("::1", HostAddressParser.normalize("::1"));
    }

    @Test
    void duplicateKeyTreatsIpv6FormsEqual() {
        String a = HostAddressParser.normalize("2001:db8::1");
        String b = HostAddressParser.normalize("2001:DB8:0:0:0:0:0:1");
        assertEquals(HostAddressParser.duplicateKey(a), HostAddressParser.duplicateKey(b));
    }

    @Test
    void rejectsInvalidIpv6() {
        assertThrows(ConfigError.class, () -> HostAddressParser.normalize("gggg::1"));
    }

    @Test
    void rejectsZoneId() {
        assertThrows(ConfigError.class, () -> HostAddressParser.normalize("fe80::1%eth0"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(ConfigError.class, () -> HostAddressParser.normalize("  "));
    }
}

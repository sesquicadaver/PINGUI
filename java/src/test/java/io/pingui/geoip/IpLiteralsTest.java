package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet4Address;
import java.net.Inet6Address;
import org.junit.jupiter.api.Test;

class IpLiteralsTest {
    @Test
    void parsesIpv4AndIpv6Literals() {
        assertTrue(IpLiterals.parseLiteralOrNull("8.8.8.8") instanceof Inet4Address);
        assertTrue(IpLiterals.parseLiteralOrNull("[2001:4860:4860::8888]") instanceof Inet6Address);
        assertNotNull(IpLiterals.parseLiteralOrNull("2001:4860:4860::8888"));
    }

    @Test
    void rejectsHostnamesWithoutDns() {
        assertNull(IpLiterals.parseLiteralOrNull("dns.google"));
        assertNull(IpLiterals.parseLiteralOrNull("example.com"));
        assertNull(IpLiterals.parseLiteralOrNull(""));
        assertNull(IpLiterals.parseLiteralOrNull(null));
    }
}

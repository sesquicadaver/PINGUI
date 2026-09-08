package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IpAddressClassifierTest {
    @Test
    void classifiesRfc1918AndUlaAsPrivate() {
        assertEquals(IpAddressScope.PRIVATE, IpAddressClassifier.scopeOfLiteral("10.0.0.1"));
        assertEquals(IpAddressScope.PRIVATE, IpAddressClassifier.scopeOfLiteral("172.16.5.1"));
        assertEquals(IpAddressScope.PRIVATE, IpAddressClassifier.scopeOfLiteral("192.168.1.1"));
        assertEquals(IpAddressScope.PRIVATE, IpAddressClassifier.scopeOfLiteral("fd12:3456::1"));
        assertEquals(IpAddressScope.PRIVATE, IpAddressClassifier.scopeOfLiteral("fc00::1"));
    }

    @Test
    void classifiesLoopbackLinkLocalCgnatDocumentationAsSpecial() {
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("127.0.0.1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("169.254.10.1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("100.64.0.1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("100.127.255.255"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("192.0.2.1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("198.51.100.1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("203.0.113.1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("::1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("fe80::1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("2001:db8::1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("ff02::1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("0.0.0.0"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("239.255.255.255"));
    }

    @Test
    void classifiesPublicUnicast() {
        assertEquals(IpAddressScope.PUBLIC, IpAddressClassifier.scopeOfLiteral("8.8.8.8"));
        assertEquals(IpAddressScope.PUBLIC, IpAddressClassifier.scopeOfLiteral("1.1.1.1"));
        assertEquals(IpAddressScope.PUBLIC, IpAddressClassifier.scopeOfLiteral("2001:4860:4860::8888"));
    }

    @Test
    void ipv4MappedUsesEmbeddedAddressScope() {
        // JDK may collapse ::ffff:x to Inet4Address; classifier still sees private/special/public.
        assertEquals(IpAddressScope.PRIVATE, IpAddressClassifier.scopeOfLiteral("::ffff:10.0.0.1"));
        assertEquals(IpAddressScope.SPECIAL, IpAddressClassifier.scopeOfLiteral("::ffff:127.0.0.1"));
        assertEquals(IpAddressScope.PUBLIC, IpAddressClassifier.scopeOfLiteral("::ffff:8.8.8.8"));
        assertEquals(IpAddressScope.PRIVATE, IpAddressClassifier.scopeOfLiteral("::ffff:a00:1"));
    }

    @Test
    void hostnameReturnsNull() {
        assertNull(IpAddressClassifier.scopeOfLiteral("dns.google"));
        assertNull(IpAddressClassifier.scopeOfLiteral(null));
    }
}

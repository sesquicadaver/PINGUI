package io.pingui.geoip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class IpMetadataProviderTest {
    private final IpMetadataProvider provider = EmptyIpMetadataProvider.INSTANCE;

    @Test
    void ipv4AndIpv6LiteralsReturnNoneEnrichment() {
        IpMetadata v4 = provider.lookup("8.8.8.8");
        assertEquals("8.8.8.8", v4.ip());
        assertEquals(IpMetadataSource.NONE, v4.source());
        assertEquals(IpAddressScope.PUBLIC, v4.scope());

        IpMetadata v6 = provider.lookup("[2001:4860:4860::8888]");
        assertEquals(IpMetadataSource.NONE, v6.source());
        assertEquals(IpAddressScope.PUBLIC, v6.scope());
        assertEquals("2001:4860:4860:0:0:0:0:8888", v6.ip());
    }

    @Test
    void privateAndSpecialScopesClassified() {
        assertEquals(IpAddressScope.PRIVATE, provider.lookup("10.0.0.1").scope());
        assertEquals(IpAddressScope.SPECIAL, provider.lookup("127.0.0.1").scope());
        assertEquals(IpAddressScope.SPECIAL, provider.lookup("2001:db8::1").scope());
        assertEquals(IpAddressScope.PRIVATE, provider.lookup("fd00::1").scope());
    }

    @Test
    void hostnamesAndBlankReturnNullWithoutDns() {
        assertNull(provider.lookup("dns.google"));
        assertNull(provider.lookup(" "));
        assertNull(provider.lookup(null));
    }

    @Test
    void emptyProviderIsSingleton() {
        assertSame(EmptyIpMetadataProvider.INSTANCE, EmptyIpMetadataProvider.INSTANCE);
    }
}

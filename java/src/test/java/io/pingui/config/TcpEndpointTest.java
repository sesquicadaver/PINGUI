package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link TcpEndpoint} (P29-005). */
class TcpEndpointTest {
    @Test
    void parsesHostnameAndIpv4Port() {
        assertEquals("example.com:443", TcpEndpoint.parse("example.com:443").display());
        assertEquals("1.1.1.1:853", TcpEndpoint.parse("1.1.1.1:853").display());
        assertTrue(TcpEndpoint.looksLike("dns.google:53"));
        assertFalse(TcpEndpoint.looksLike("8.8.8.8"));
        assertFalse(TcpEndpoint.looksLike("2001:db8::1"));
    }

    @Test
    void parsesBracketedIpv6() {
        TcpEndpoint endpoint = TcpEndpoint.parse("[2001:db8::1]:443");
        assertEquals("[2001:db8::1]:443", endpoint.display());
        assertEquals(443, endpoint.port());
    }

    @Test
    void rejectsBareIpv6WithPortAndBadPorts() {
        assertThrows(ConfigError.class, () -> TcpEndpoint.parse("2001:db8::1:443"));
        assertThrows(ConfigError.class, () -> TcpEndpoint.parse("example.com:0"));
        assertThrows(ConfigError.class, () -> TcpEndpoint.parse("example.com:70000"));
    }

    @Test
    void hostsConfigNormalizesTcpEndpoints() {
        assertEquals("example.com:443", HostsConfig.normalizeHostEntry("Example.COM:443"));
        assertEquals("example.com:443", HostsConfig.duplicateKey("Example.COM:443"));
    }
}

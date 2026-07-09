package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HostAddressResolverTest {
    @Test
    void returnsIpv4LiteralForV4() {
        assertEquals("8.8.8.8", HostAddressResolver.resolveForPing("8.8.8.8", false));
    }

    @Test
    void returnsIpv6LiteralForV6() {
        assertEquals("2001:db8::1", HostAddressResolver.resolveForPing("2001:db8::1", true));
    }

    @Test
    void rejectsIpv6RequestForIpv4Literal() {
        assertThrows(ConfigError.class, () -> HostAddressResolver.resolveForPing("8.8.8.8", true));
    }

    @Test
    void rejectsIpv4RequestForIpv6Literal() {
        assertThrows(ConfigError.class, () -> HostAddressResolver.resolveForPing("2001:db8::1", false));
    }

    @Test
    void rejectsUnknownHostname() {
        ConfigError error = assertThrows(
                ConfigError.class,
                () -> HostAddressResolver.resolveForPing("this-host-should-not-exist.invalid.", true));
        assertTrue(error.getMessage().contains("Cannot resolve host"));
    }

    @Test
    void resolvesLocalhostIpv4() {
        String resolved = HostAddressResolver.resolveForPing("localhost", false);
        assertTrue(resolved.contains("."), "expected dotted IPv4 for localhost");
    }

    @Test
    void resolvesLocalhostIpv6WhenAvailable() {
        try {
            String resolved = HostAddressResolver.resolveForPing("localhost", true);
            assertTrue(resolved.contains(":"), "expected IPv6 literal for localhost");
        } catch (ConfigError ex) {
            // Some minimal environments expose only A record for localhost.
            assertTrue(ex.getMessage().contains("No IPv6 address"));
        }
    }
}

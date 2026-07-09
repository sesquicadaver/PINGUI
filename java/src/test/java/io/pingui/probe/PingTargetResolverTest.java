package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.PingExpertEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PingTargetResolverTest {
    @Test
    void keepsIpv4LiteralForDefaultExpert() {
        String resolved = PingTargetResolver.resolve("8.8.8.8", PingExpertEntry.empty());
        assertTrue(resolved.equals("8.8.8.8"));
    }

    @Test
    void resolvesHostnameToIpv4ByDefault() {
        String resolved = PingTargetResolver.resolve("localhost", PingExpertEntry.empty());
        assertFalse(resolved.isBlank());
        assertTrue(resolved.contains("."));
    }

    @Test
    void resolvesHostnameToIpv6WhenExpertSelectsV6() {
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-6"));
        try {
            String resolved = PingTargetResolver.resolve("localhost", expert);
            assertTrue(resolved.contains(":"));
        } catch (io.pingui.config.ConfigError ex) {
            assertTrue(ex.getMessage().contains("No IPv6 address"));
        }
    }
}

package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class HostsConfigTest {

    @Test
    void validateSessionHost_acceptsIpv4() {
        String host = HostsConfig.validateSessionHost("8.8.8.8", List.of());
        assertEquals("8.8.8.8", host);
    }

    @Test
    void validateSessionHost_acceptsHostname() {
        String host = HostsConfig.validateSessionHost("example.com", List.of());
        assertEquals("example.com", host);
    }

    @Test
    void validateSessionHost_rejectsIpv6() {
        assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("2001:db8::1", List.of()));
    }

    @Test
    void validateSessionHost_rejectsDuplicate() {
        assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("8.8.8.8", List.of("8.8.8.8")));
    }

    @Test
    void validateSessionHost_rejectsEleventhHost() {
        List<String> ten = List.of(
                "h1.example",
                "h2.example",
                "h3.example",
                "h4.example",
                "h5.example",
                "h6.example",
                "h7.example",
                "h8.example",
                "h9.example",
                "h10.example");
        assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("h11.example", ten));
    }
}

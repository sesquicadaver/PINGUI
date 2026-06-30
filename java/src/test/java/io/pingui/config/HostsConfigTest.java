package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostsConfigTest {

    @TempDir
    Path tempDir;

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
    void validateSessionHost_rejectsIpv6_withExplicitMessage() {
        ConfigError error =
                assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("2001:db8::1", List.of()));
        assertEquals("IPv6 addresses are not supported (IPv4-only): '2001:db8::1'", error.getMessage());
    }

    @Test
    void validateSessionHost_rejectsDuplicate() {
        assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("8.8.8.8", List.of("8.8.8.8")));
    }

    @Test
    void validateSessionHost_rejectsCaseInsensitiveDuplicate() {
        assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("Example.COM", List.of("example.com")));
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

    @Test
    void normalizeHostEntry_rejectsInvalidCharacters() {
        assertThrows(ConfigError.class, () -> HostsConfig.normalizeHostEntry("bad host!"));
    }

    @Test
    void loadHostsList_parsesLegacyYamlList() {
        List<String> hosts = HostsConfig.loadHostsList(List.of("1.1.1.1", "8.8.8.8"));
        assertEquals(List.of("1.1.1.1", "8.8.8.8"), hosts);
    }

    @Test
    void loadHostsList_rejectsNonList() {
        assertThrows(ConfigError.class, () -> HostsConfig.loadHostsList("not-a-list"));
    }

    @Test
    void loadHostsList_rejectsDuplicateHosts() {
        assertThrows(ConfigError.class, () -> HostsConfig.loadHostsList(List.of("8.8.8.8", "8.8.8.8")));
    }

    @Test
    void loadHostsList_rejectsNonStringEntry() {
        assertThrows(ConfigError.class, () -> HostsConfig.loadHostsList(List.of(42)));
    }

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Path path = tempDir.resolve("hosts.yaml");
        HostsConfig.save(path, List.of("8.8.8.8", "1.1.1.1"));
        List<String> loaded = HostsConfig.load(path);
        assertEquals(List.of("8.8.8.8", "1.1.1.1"), loaded);
    }

    @Test
    void save_rejectsDuplicateHosts() {
        Path path = tempDir.resolve("dup.yaml");
        assertThrows(ConfigError.class, () -> HostsConfig.save(path, List.of("8.8.8.8", "8.8.8.8")));
    }

    @Test
    void loadFromProfilesYaml() throws Exception {
        Path path = tempDir.resolve("profiles.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "9.9.9.9"
                """);
        assertEquals(List.of("9.9.9.9"), HostsConfig.load(path));
    }
}

package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.probe.ProbeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfilesConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadSaveRoundTrip_v2Profiles() throws Exception {
        Path path = tempDir.resolve("hosts.yaml");
        Files.writeString(
                path,
                """
                active_profile: lab
                profiles:
                  lab:
                    interval: 30.0
                    max_hops: 15
                    timeout: 1.0
                    probe: process
                    hosts:
                      - address: "8.8.8.8"
                        enabled: true
                        ping_only: false
                """);

        ProfileDocument loaded = ProfilesConfig.load(path);
        assertEquals("lab", loaded.activeProfile());
        TracingProfile lab = loaded.active();
        assertEquals(30.0, lab.intervalSeconds());
        assertEquals(15, lab.maxHops());
        assertEquals(ProbeMode.PROCESS, lab.probeMode());
        assertEquals(1, lab.hosts().size());

        ProfilesConfig.save(path, loaded);
        ProfileDocument reloaded = ProfilesConfig.load(path);
        assertEquals(30.0, reloaded.active().intervalSeconds());
    }

    @Test
    void loadLegacyHostsList() throws Exception {
        Path path = tempDir.resolve("legacy.yaml");
        Files.writeString(
                path,
                """
                hosts:
                  - "1.1.1.1"
                  - "8.8.8.8"
                """);

        ProfileDocument doc = ProfilesConfig.load(path);
        assertEquals(ProfileDocument.DEFAULT_PROFILE, doc.activeProfile());
        assertEquals(2, doc.active().hosts().size());
        assertTrue(doc.active().hostAddresses().contains("1.1.1.1"));
    }

    @Test
    void loadFileNotFound() {
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(tempDir.resolve("missing.yaml")));
    }

    @Test
    void loadInvalidRoot() throws Exception {
        Path path = tempDir.resolve("bad.yaml");
        Files.writeString(path, "- not-a-map\n");
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadEmptyProfiles() throws Exception {
        Path path = tempDir.resolve("empty.yaml");
        Files.writeString(
                path, """
                active_profile: default
                profiles: {}
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadDuplicateHostRejected() throws Exception {
        Path path = tempDir.resolve("dup.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "8.8.8.8"
                      - "8.8.8.8"
                """);
        ConfigError error = assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
        assertTrue(error.getMessage().contains("Duplicate host"));
    }

    @Test
    void loadPingExpertWithChainAndArgs() throws Exception {
        Path path = tempDir.resolve("expert.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - address: "8.8.8.8"
                        enabled: true
                        ping_only: true
                        ping_expert:
                          chain: true
                          args: ["-4", "-s", "128"]
                """);
        ProfileDocument doc = ProfilesConfig.load(path);
        HostEntry host = doc.active().hosts().get(0);
        assertTrue(host.pingOnly());
        assertTrue(host.pingExpert().applyToChain());
        assertEquals(3, host.pingExpert().args().size());

        ProfilesConfig.save(path, doc);
        ProfileDocument reloaded = ProfilesConfig.load(path);
        assertEquals("-4", reloaded.active().hosts().get(0).pingExpert().args().get(0));
    }

    @Test
    void loadNegativeIntervalRejected() throws Exception {
        Path path = tempDir.resolve("neg.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    interval: -1
                    hosts:
                      - "8.8.8.8"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadMissingHostsKey() throws Exception {
        Path path = tempDir.resolve("nohosts.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    interval: 1.0
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadInvalidProbeMode() throws Exception {
        Path path = tempDir.resolve("probe.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    probe: not-a-mode
                    hosts:
                      - "8.8.8.8"
                """);
        assertThrows(Exception.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadTooManyHostsRejected() throws Exception {
        Path path = tempDir.resolve("many.yaml");
        StringBuilder hosts = new StringBuilder();
        for (int i = 1; i <= 11; i++) {
            hosts.append("      - \"10.0.0.").append(i).append("\"\n");
        }
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                """
                        + hosts);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadStringHostEntryWithFlags() throws Exception {
        Path path = tempDir.resolve("host-flags.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - "1.1.1.1"
                      - address: "8.8.8.8"
                        enabled: true
                        ping_only: true
                """);
        ProfileDocument doc = ProfilesConfig.load(path);
        assertEquals(2, doc.active().hosts().size());
        HostEntry mapped = doc.active().hosts().get(1);
        assertTrue(mapped.enabled());
        assertTrue(mapped.pingOnly());
    }

    @Test
    void loadInvalidBooleanInHostEntry() throws Exception {
        Path path = tempDir.resolve("bad-bool.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - address: "8.8.8.8"
                        enabled: "yes"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void saveAndReloadMinimalProfile() throws Exception {
        Path path = tempDir.resolve("out.yaml");
        ProfileDocument doc =
                new ProfileDocument("default", java.util.Map.of("default", TracingProfile.defaults(List.of())));
        ProfilesConfig.save(path, doc);
        assertEquals("default", ProfilesConfig.load(path).activeProfile());
    }

    @Test
    void loadMissingProfilesAndHosts() throws Exception {
        Path path = tempDir.resolve("empty-root.yaml");
        Files.writeString(path, "active_profile: default\n");
        ConfigError error = assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
        assertTrue(error.getMessage().contains("profiles") || error.getMessage().contains("hosts"));
    }

    @Test
    void loadProfilesNotMapping() throws Exception {
        Path path = tempDir.resolve("profiles-list.yaml");
        Files.writeString(
                path, """
                active_profile: default
                profiles: []
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadHostEntryWrongType() throws Exception {
        Path path = tempDir.resolve("host-int.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - 42
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadHostMissingAddress() throws Exception {
        Path path = tempDir.resolve("no-address.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - enabled: true
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void saveRejectsProfileExceedingMaxHosts() {
        List<HostEntry> tooMany = new java.util.ArrayList<>();
        for (int i = 1; i <= HostsConfig.MAX_HOSTS + 1; i++) {
            tooMany.add(HostEntry.basic("10.0.0." + i, false));
        }
        ProfileDocument doc = ProfileDocument.singleDefault(TracingProfile.defaults(tooMany));
        assertThrows(ConfigError.class, () -> ProfilesConfig.save(tempDir.resolve("many.yaml"), doc));
    }

    @Test
    void loadNonNumberTimeout() throws Exception {
        Path path = tempDir.resolve("maxhops.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    max_hops: fast
                    hosts:
                      - "8.8.8.8"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }

    @Test
    void loadPingExpertArgsNotList() throws Exception {
        Path path = tempDir.resolve("args.yaml");
        Files.writeString(
                path,
                """
                active_profile: default
                profiles:
                  default:
                    hosts:
                      - address: "8.8.8.8"
                        ping_expert:
                          args: "-4"
                """);
        assertThrows(ConfigError.class, () -> ProfilesConfig.load(path));
    }
}

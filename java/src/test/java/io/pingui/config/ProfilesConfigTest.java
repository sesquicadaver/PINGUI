package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.probe.ProbeMode;
import java.nio.file.Files;
import java.nio.file.Path;
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
}

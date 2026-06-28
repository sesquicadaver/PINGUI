package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void loadsLegacyHostsAsDefaultProfile() throws Exception {
        Path path = tempDir.resolve("legacy.yaml");
        Files.writeString(
                path,
                """
                hosts:
                  - "8.8.8.8"
                  - "1.1.1.1"
                """);

        ProfileDocument doc = ProfilesConfig.load(path);
        assertEquals("default", doc.activeProfile());
        assertEquals(2, doc.active().hosts().size());
        assertEquals("8.8.8.8", doc.active().hosts().get(0).address());
        assertFalse(doc.active().hosts().get(0).enabled());
    }

    @Test
    void roundTripsV2WithExpertPing() throws Exception {
        Path path = tempDir.resolve("v2.yaml");
        HostEntry host =
                new HostEntry(
                        "8.8.8.8",
                        true,
                        new PingExpertEntry(false, List.of("-4", "-s", "128")));
        ProfileDocument original =
                ProfileDocument.singleDefault(
                        new TracingProfile(2.0, 15, 1.0, ProbeMode.PROCESS, List.of(host)));

        ProfilesConfig.save(path, original);
        ProfileDocument loaded = ProfilesConfig.load(path);

        assertEquals("default", loaded.activeProfile());
        HostEntry loadedHost = loaded.active().hosts().get(0);
        assertTrue(loadedHost.enabled());
        assertTrue(loadedHost.pingExpert().isConfigured());
        assertEquals(List.of("-4", "-s", "128"), loadedHost.pingExpert().args());
        assertEquals(2.0, loaded.active().intervalSeconds());
        assertEquals(15, loaded.active().maxHops());
        assertEquals(ProbeMode.PROCESS, loaded.active().probeMode());
    }
}

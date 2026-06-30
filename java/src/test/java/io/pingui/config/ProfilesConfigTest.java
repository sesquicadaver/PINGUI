package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.probe.ProbeMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
                        false,
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

    @Test
    void savesAndLoadsMultipleProfiles() throws Exception {
        Path path = tempDir.resolve("multi.yaml");
        TracingProfile office =
                new TracingProfile(1.0, 20, 0.5, ProbeMode.AUTO, List.of(HostEntry.basic("10.0.0.1", true)));
        TracingProfile home =
                new TracingProfile(2.0, 15, 1.0, ProbeMode.PROCESS, List.of(HostEntry.basic("8.8.8.8", false)));
        ProfileDocument original = new ProfileDocument("office", Map.of("office", office, "home", home));

        ProfilesConfig.save(path, original);
        ProfileDocument loaded = ProfilesConfig.load(path);

        assertEquals("office", loaded.activeProfile());
        assertEquals(2, loaded.profiles().size());
        assertEquals(2.0, loaded.profiles().get("home").intervalSeconds());
        assertTrue(loaded.profiles().get("office").hosts().get(0).enabled());
    }

    @Test
    void roundTripsPingOnlyFlag() throws Exception {
        Path path = tempDir.resolve("ping-only.yaml");
        HostEntry host = new HostEntry("8.8.8.8", true, true, PingExpertEntry.empty());
        ProfileDocument original =
                ProfileDocument.singleDefault(new TracingProfile(1.0, 20, 0.5, ProbeMode.AUTO, List.of(host)));

        ProfilesConfig.save(path, original);
        ProfileDocument loaded = ProfilesConfig.load(path);

        assertTrue(loaded.active().hosts().get(0).pingOnly());
    }
}

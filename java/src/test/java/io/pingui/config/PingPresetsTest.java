package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PingPresetsTest {
    @BeforeEach
    @AfterEach
    void reset() {
        PingPresets.configureDefault();
    }

    @Test
    void bundledResourceHasFourNamedPresets() {
        List<PingPreset> presets = PingPresets.all();
        assertEquals(PingPresets.EXPECTED_COUNT, presets.size());
        assertEquals(
                List.of("mtu_probe", "df", "dscp", "burst"),
                presets.stream().map(PingPreset::id).collect(Collectors.toList()));
        assertEquals(List.of("-M", "probe", "-s", "1472"), presets.get(0).args());
        assertEquals(List.of("-M", "do"), presets.get(1).args());
        assertEquals(List.of("-Q", "46"), presets.get(2).args());
        assertEquals(List.of("-s", "1024", "-O"), presets.get(3).args());
    }

    @Test
    void mergeKeepsIpv6AddressFamily() {
        List<String> merged = PingPresets.mergeKeepingAddressFamily(List.of("-6", "-n"), List.of("-M", "do"));
        assertEquals(List.of("-6", "-M", "do"), merged);
    }

    @Test
    void mergeDefaultsToIpv4() {
        List<String> merged = PingPresets.mergeKeepingAddressFamily(List.of(), List.of("-Q", "46"));
        assertEquals(List.of("-4", "-Q", "46"), merged);
    }

    @Test
    void configureFromFileOverridesResource() throws Exception {
        Path file = Files.createTempFile("ping-presets-", ".yaml");
        Files.writeString(
                file,
                """
                presets:
                  - id: mtu_probe
                    label: MTU
                    args: ["-M", "want"]
                  - id: df
                    label: DF
                    args: ["-M", "do"]
                  - id: dscp
                    label: DSCP
                    args: ["-Q", "0x2e"]
                  - id: burst
                    label: Burst
                    args: ["-O"]
                """);
        try {
            PingPresets.configure(file);
            assertEquals(List.of("-M", "want"), PingPresets.all().get(0).args());
            assertEquals("0x2e", PingPresets.all().get(2).args().get(1));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void invalidPresetArgsRejected() {
        assertThrows(
                ConfigError.class,
                () -> PingPresets.parseYaml(
                        """
                        presets:
                          - id: mtu_probe
                            label: MTU
                            args: ["-f"]
                          - id: df
                            label: DF
                            args: ["-M", "do"]
                          - id: dscp
                            label: DSCP
                            args: ["-Q", "46"]
                          - id: burst
                            label: Burst
                            args: ["-O"]
                        """,
                        "test"));
    }

    @Test
    void resolvePathPrefersSiblingOfHostsConfig() throws Exception {
        Path dir = Files.createTempDirectory("pingui-presets-");
        Path hosts = dir.resolve("hosts.yaml");
        Path presets = dir.resolve("ping_presets.yaml");
        Files.writeString(hosts, "profiles: {}\n");
        Files.writeString(
                presets,
                """
                presets:
                  - id: mtu_probe
                    label: MTU
                    args: ["-M", "want"]
                  - id: df
                    label: DF
                    args: ["-M", "do"]
                  - id: dscp
                    label: DSCP
                    args: ["-Q", "46"]
                  - id: burst
                    label: Burst
                    args: ["-O"]
                """);
        try {
            assertEquals(presets, PingPresets.resolvePath(hosts));
            PingPresets.configure(PingPresets.resolvePath(hosts));
            assertEquals(List.of("-M", "want"), PingPresets.all().get(0).args());
        } finally {
            Files.deleteIfExists(presets);
            Files.deleteIfExists(hosts);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void wrongPresetCountRejected() {
        ConfigError error = assertThrows(
                ConfigError.class,
                () -> PingPresets.parseYaml(
                        """
                        presets:
                          - id: only
                            label: Only
                            args: []
                        """,
                        "test"));
        assertTrue(error.getMessage().contains("expected 4"));
    }
}

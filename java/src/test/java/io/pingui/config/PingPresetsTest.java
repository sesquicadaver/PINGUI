package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final String FOUR_PRESETS =
            """
            presets:
              - id: mtu_probe
                label: MTU
                args: ["-M", "want"]
                summary: "s mtu"
                expect: "e mtu"
                caution: "c mtu"
              - id: df
                label: DF
                args: ["-M", "do"]
                summary: "s df"
                expect: "e df"
              - id: dscp
                label: DSCP
                args: ["-Q", "0x2e"]
                summary: "s dscp"
                expect: "e dscp"
              - id: burst
                label: Burst
                args: ["-O"]
                summary: "s burst"
                expect: "e burst"
            """;

    @BeforeEach
    @AfterEach
    void reset() {
        PingPresets.configureDefault();
    }

    @Test
    void bundledResourceHasFourNamedPresetsWithUxCopy() {
        List<PingPreset> presets = PingPresets.all();
        assertEquals(PingPresets.EXPECTED_COUNT, presets.size());
        assertEquals(
                List.of("mtu_probe", "df", "dscp", "burst"),
                presets.stream().map(PingPreset::id).collect(Collectors.toList()));
        assertEquals(List.of("-M", "probe", "-s", "1472"), presets.get(0).args());
        assertEquals(List.of("-M", "do"), presets.get(1).args());
        assertEquals(List.of("-Q", "46"), presets.get(2).args());
        assertEquals(List.of("-s", "1024", "-O"), presets.get(3).args());
        assertFalse(presets.get(0).summary().isBlank());
        assertFalse(presets.get(0).expect().isBlank());
        assertTrue(presets.get(0).statusLine().contains("MTU probe"));
        assertTrue(presets.get(0).tooltipText().contains("-M"));
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
        Files.writeString(file, FOUR_PRESETS);
        try {
            PingPresets.configure(file);
            assertEquals(List.of("-M", "want"), PingPresets.all().get(0).args());
            assertEquals("0x2e", PingPresets.all().get(2).args().get(1));
            assertEquals("s mtu", PingPresets.all().get(0).summary());
            assertEquals("c mtu", PingPresets.all().get(0).caution());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void missingSummaryRejected() {
        ConfigError error = assertThrows(
                ConfigError.class,
                () -> PingPresets.parseYaml(
                        """
                        presets:
                          - id: mtu_probe
                            label: MTU
                            args: ["-M", "do"]
                            expect: "e"
                          - id: df
                            label: DF
                            args: ["-M", "do"]
                            summary: "s"
                            expect: "e"
                          - id: dscp
                            label: DSCP
                            args: ["-Q", "46"]
                            summary: "s"
                            expect: "e"
                          - id: burst
                            label: Burst
                            args: ["-O"]
                            summary: "s"
                            expect: "e"
                        """,
                        "test"));
        assertTrue(error.getMessage().contains("summary"));
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
                            summary: "s"
                            expect: "e"
                          - id: df
                            label: DF
                            args: ["-M", "do"]
                            summary: "s"
                            expect: "e"
                          - id: dscp
                            label: DSCP
                            args: ["-Q", "46"]
                            summary: "s"
                            expect: "e"
                          - id: burst
                            label: Burst
                            args: ["-O"]
                            summary: "s"
                            expect: "e"
                        """,
                        "test"));
    }

    @Test
    void resolvePathPrefersSiblingOfHostsConfig() throws Exception {
        Path dir = Files.createTempDirectory("pingui-presets-");
        Path hosts = dir.resolve("hosts.yaml");
        Path presets = dir.resolve("ping_presets.yaml");
        Files.writeString(hosts, "profiles: {}\n");
        Files.writeString(presets, FOUR_PRESETS);
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
                            summary: "s"
                            expect: "e"
                        """,
                        "test"));
        assertTrue(error.getMessage().contains("expected 4"));
    }
}

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
    void loadAndSaveRoundtrip() throws Exception {
        Path cfg = tempDir.resolve("hosts.yaml");
        Files.writeString(cfg, "hosts:\n  - 8.8.8.8\n  - 1.1.1.1\n");
        assertEquals(List.of("8.8.8.8", "1.1.1.1"), HostsConfig.load(cfg));
        HostsConfig.save(cfg, List.of("8.8.4.4"));
        assertEquals(List.of("8.8.4.4"), HostsConfig.load(cfg));
    }

    @Test
    void rejectsDuplicate() {
        assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("8.8.8.8", List.of("8.8.8.8")));
    }

    @Test
    void rejectsTooMany() {
        List<String> ten = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> "10.0.0." + i)
                .toList();
        assertThrows(ConfigError.class, () -> HostsConfig.validateSessionHost("9.9.9.9", ten));
    }

    @Test
    void rejectsMissingConfigFile() {
        assertThrows(ConfigError.class, () -> HostsConfig.load(tempDir.resolve("missing.yaml")));
    }

    @Test
    void rejectsInvalidHostEntry() {
        assertThrows(ConfigError.class, () -> HostsConfig.normalizeHostEntry("bad host!"));
    }
}

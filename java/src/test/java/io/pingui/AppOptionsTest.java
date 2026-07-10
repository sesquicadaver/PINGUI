package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppOptionsTest {
    @Test
    void defaultsMatchSpec() {
        AppOptions options = AppOptions.defaults();
        assertEquals(Path.of("config/hosts.example.yaml"), options.configPath());
        assertTrue(options.profileOverrides().isEmpty());
        assertFalse(options.verbose());
        assertTrue(options.geoipEnabled());
        assertEquals(Path.of("config/geoip_hints.yaml"), options.geoipHintsPath());
        assertEquals(CliRunMode.GUI, options.runMode());
        assertEquals(AppOptions.defaultPidFile(), options.pidFilePath());
    }
}

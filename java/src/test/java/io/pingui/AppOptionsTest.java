package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.pingui.probe.ProbeMode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppOptionsTest {
    @Test
    void defaultsMatchSpec() {
        AppOptions options = AppOptions.defaults();
        assertEquals(Path.of("config/hosts.example.yaml"), options.configPath());
        assertEquals(1.0, options.intervalSeconds());
        assertEquals(20, options.maxHops());
        assertEquals(0.5, options.timeoutSeconds());
        assertFalse(options.verbose());
        assertEquals(ProbeMode.AUTO, options.probeMode());
    }
}

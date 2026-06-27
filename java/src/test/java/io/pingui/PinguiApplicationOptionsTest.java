package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PinguiApplicationOptionsTest {
    @Test
    void parseOptionsUsesDefaults() {
        AppOptions options = PinguiApplication.parseOptions(Map.of());
        assertEquals(Path.of("config/hosts.example.yaml"), options.configPath());
        assertEquals(1.0, options.intervalSeconds());
        assertEquals(20, options.maxHops());
        assertEquals(0.5, options.timeoutSeconds());
        assertFalse(options.verbose());
    }

    @Test
    void parseOptionsOverrides() {
        AppOptions options =
                PinguiApplication.parseOptions(
                        Map.of(
                                "config", "custom.yaml",
                                "interval", "2.5",
                                "max-hops", "10",
                                "timeout", "1.0",
                                "verbose", "true"));
        assertEquals(Path.of("custom.yaml"), options.configPath());
        assertEquals(2.5, options.intervalSeconds());
        assertEquals(10, options.maxHops());
        assertEquals(1.0, options.timeoutSeconds());
        assertTrue(options.verbose());
    }

    @Test
    void parseOptionsRejectsInvalidNumbers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PinguiApplication.parseOptions(Map.of("interval", "0")));
    }
}

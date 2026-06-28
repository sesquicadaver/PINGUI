package io.pingui.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformCapabilitiesTest {
    @Test
    void expertPingSupportedOnLinux() {
        assertTrue(PlatformCapabilities.expertPingSupported()
                || !System.getProperty("os.name", "").toLowerCase().contains("linux"));
    }

    @Test
    void expertPingNotSupportedOnWindowsOrMac() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") || os.contains("mac")) {
            assertFalse(PlatformCapabilities.expertPingSupported());
        }
    }
}

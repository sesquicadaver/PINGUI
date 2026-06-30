package io.pingui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppInfoTest {

    @Test
    void version_isNonBlank() {
        assertFalse(AppInfo.version().isBlank());
    }

    @Test
    void gitSha_isPresent() {
        assertNotNull(AppInfo.gitSha());
        assertFalse(AppInfo.gitSha().isBlank());
    }

    @Test
    void versionDetail_includesVersion() {
        assertTrue(AppInfo.versionDetail().contains(AppInfo.version()));
    }

    @Test
    void buildNumber_isPresent() {
        assertNotNull(AppInfo.buildNumber());
        assertFalse(AppInfo.buildNumber().isBlank());
    }
}

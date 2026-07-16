package io.pingui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Properties;
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

    @Test
    void version_matchesBuildPropertiesWhenGenerated() throws Exception {
        try (InputStream in = AppInfo.class.getResourceAsStream("/pingui/build.properties")) {
            if (in == null) {
                return;
            }
            Properties properties = new Properties();
            properties.load(in);
            String fromBuild = properties.getProperty("version");
            if (fromBuild != null && !fromBuild.isBlank()) {
                assertEquals(fromBuild, AppInfo.version());
            }
        }
    }
}

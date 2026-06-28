package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.probe.ProbeMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileDocumentTest {
    @Test
    void switchesActiveProfile() {
        TracingProfile office = TracingProfile.defaults(List.of(HostEntry.basic("10.0.0.1", false)));
        TracingProfile home = TracingProfile.defaults(List.of(HostEntry.basic("8.8.8.8", false)));
        ProfileDocument doc = new ProfileDocument("office", Map.of("office", office, "home", home));

        doc.setActiveProfile("home");
        assertEquals("home", doc.activeProfile());
        assertEquals("8.8.8.8", doc.active().hosts().get(0).address());
    }

    @Test
    void cannotDeleteLastProfile() {
        ProfileDocument doc = ProfileDocument.singleDefault(TracingProfile.defaults(List.of()));
        assertThrows(ConfigError.class, () -> doc.removeProfile("default"));
    }

    @Test
    void removeProfileSwitchesActiveWhenNeeded() {
        TracingProfile a = TracingProfile.defaults(List.of());
        TracingProfile b = TracingProfile.defaults(List.of(HostEntry.basic("1.1.1.1", true)));
        ProfileDocument doc = new ProfileDocument("a", Map.of("a", a, "b", b));

        doc.removeProfile("a");
        assertEquals("b", doc.activeProfile());
        assertTrue(doc.hasProfile("b"));
    }
}

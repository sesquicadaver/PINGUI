package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.ConfigError;
import io.pingui.config.HostsConfig;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HostRegistry} (P19-005). */
class HostRegistryTest {

    @Test
    void addRemoveAndEnabledHosts() {
        HostRegistry registry = new HostRegistry();
        registry.add("a", true, HostProbeMode.TRACE);
        registry.add("b", false, HostProbeMode.PING_ONLY);

        assertEquals(List.of("a", "b"), registry.hosts());
        assertEquals(List.of("a"), registry.enabledHosts());
        assertTrue(registry.contains("a"));
        assertFalse(registry.contains("missing"));

        registry.setEnabled("b", true);
        assertEquals(List.of("a", "b"), registry.enabledHosts());

        registry.remove("a");
        assertEquals(List.of("b"), registry.hosts());
        assertThrows(ConfigError.class, () -> registry.remove("a"));
    }

    @Test
    void addDuplicateThrows() {
        HostRegistry registry = new HostRegistry();
        registry.add("a", true, HostProbeMode.TRACE);
        assertThrows(ConfigError.class, () -> registry.add("a", false, HostProbeMode.PING_ONLY));
    }

    @Test
    void canAddRespectsMaxHosts() {
        HostRegistry registry = new HostRegistry();
        for (int i = 0; i < HostsConfig.MAX_HOSTS; i++) {
            assertTrue(registry.canAdd());
            registry.add("h" + i, true, HostProbeMode.TRACE);
        }
        assertFalse(registry.canAdd());
    }

    @Test
    void renameMovesModeEnabledAndPollState() {
        HostRegistry registry = new HostRegistry();
        registry.add("old", true, HostProbeMode.PING_ONLY);
        registry.putLastRoute("old", List.of("10.0.0.1"));
        registry.markPolled("old", Instant.parse("2026-07-16T08:00:00Z"));
        AtomicBoolean flag = registry.inFlightFlag("old");

        registry.rename("old", "new");

        assertFalse(registry.contains("old"));
        assertTrue(registry.contains("new"));
        assertEquals(HostProbeMode.PING_ONLY, registry.mappedMode("new", HostProbeMode.TRACE));
        assertEquals(List.of("10.0.0.1"), registry.copyLastRoute("new"));
        assertEquals(Instant.parse("2026-07-16T08:00:00Z"), registry.lastPollAt("new"));
        assertEquals(flag, registry.inFlightFlag("new"));
        assertThrows(ConfigError.class, () -> registry.rename("missing", "x"));
    }

    @Test
    void setProbeModeClearsRouteAndLastPoll() {
        HostRegistry registry = new HostRegistry();
        registry.add("h", true, HostProbeMode.TRACE);
        registry.putLastRoute("h", List.of("1.1.1.1"));
        registry.markPolled("h", Instant.now());

        registry.setProbeMode("h", HostProbeMode.PING_ONLY);

        assertEquals(HostProbeMode.PING_ONLY, registry.mappedMode("h", HostProbeMode.TRACE));
        assertEquals(List.of(), registry.copyLastRoute("h"));
        assertNull(registry.lastPollAt("h"));
        assertThrows(ConfigError.class, () -> registry.setProbeMode("missing", HostProbeMode.TRACE));
    }

    @Test
    void beginPollReturnsNullForUnknownHost() {
        HostRegistry registry = new HostRegistry();
        assertNull(registry.beginPoll("missing", HostProbeMode.TRACE, Instant.now()));
    }

    @Test
    void beginPollSnapshotsRouteAndMarksPolled() {
        HostRegistry registry = new HostRegistry();
        registry.add("h", true, HostProbeMode.MTR);
        registry.putLastRoute("h", List.of("10.0.0.1", "8.8.8.8"));
        Instant now = Instant.parse("2026-07-16T09:00:00Z");

        HostRegistry.PollStart start = registry.beginPoll("h", HostProbeMode.TRACE, now);

        assertEquals(List.of("10.0.0.1", "8.8.8.8"), start.previousIps());
        assertEquals(HostProbeMode.MTR, start.mappedMode());
        assertEquals(now, registry.lastPollAt("h"));
    }

    @Test
    void mappedModeUnchangedDetectsFlip() {
        HostRegistry registry = new HostRegistry();
        registry.add("h", true, HostProbeMode.TRACE);
        assertTrue(registry.mappedModeUnchanged("h", HostProbeMode.TRACE, HostProbeMode.TRACE));
        registry.setProbeMode("h", HostProbeMode.PING_ONLY);
        assertFalse(registry.mappedModeUnchanged("h", HostProbeMode.TRACE, HostProbeMode.TRACE));
        assertFalse(registry.mappedModeUnchanged("gone", HostProbeMode.TRACE, HostProbeMode.TRACE));
    }
}

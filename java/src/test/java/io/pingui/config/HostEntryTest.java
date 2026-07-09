package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.monitor.HostProbeMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostEntryTest {

    @Test
    void basicCreatesDisabledHostWithoutPingOnly() {
        HostEntry entry = HostEntry.basic("8.8.8.8", true);
        assertEquals("8.8.8.8", entry.address());
        assertTrue(entry.enabled());
        assertFalse(entry.pingOnly());
        assertFalse(entry.pingExpert().isConfigured());
    }

    @Test
    void blankAddressRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HostEntry(" ", true, false, PingExpertEntry.empty()));
        assertThrows(IllegalArgumentException.class, () -> new HostEntry(null, true, false, PingExpertEntry.empty()));
    }

    @Test
    void nullPingExpertNormalizedToEmpty() {
        HostEntry entry = new HostEntry("1.1.1.1", false, false, null);
        assertFalse(entry.pingExpert().isConfigured());
    }

    @Test
    void withPingExpertReplacesExpertFlags() {
        PingExpertEntry expert = new PingExpertEntry(true, List.of("-4", "-s", "64"));
        HostEntry entry = HostEntry.basic("8.8.8.8", false).withPingExpert(expert);
        assertTrue(entry.pingExpert().applyToChain());
        assertEquals(3, entry.pingExpert().args().size());
    }

    @Test
    void withPingExpertNullUsesEmpty() {
        HostEntry entry = HostEntry.basic("8.8.8.8", false).withPingExpert(null);
        assertFalse(entry.pingExpert().isConfigured());
    }

    @Test
    void effectiveIntervalUsesModeDefaultsAndOverride() {
        HostEntry trace = HostEntry.basic("8.8.8.8", true);
        assertEquals(30.0, trace.effectiveIntervalSeconds(HostProbeMode.TRACE, 30.0));

        HostEntry pingOnly = trace.withPingOnly(true);
        assertEquals(1.5, pingOnly.effectiveIntervalSeconds(HostProbeMode.TRACE, 30.0));

        HostEntry override = pingOnly.withIntervalSecondsOverride(0.75);
        assertEquals(0.75, override.effectiveIntervalSeconds(HostProbeMode.TRACE, 30.0));
    }

    @Test
    void withPingOnlyPreservesOtherFields() {
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-4"));
        HostEntry entry = new HostEntry("8.8.8.8", true, false, expert).withPingOnly(true);
        assertTrue(entry.enabled());
        assertTrue(entry.pingOnly());
        assertEquals("-4", entry.pingExpert().args().get(0));
    }
}

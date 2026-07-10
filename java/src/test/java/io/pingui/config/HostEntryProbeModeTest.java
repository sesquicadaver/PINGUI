package io.pingui.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.pingui.monitor.HostProbeMode;
import org.junit.jupiter.api.Test;

class HostEntryProbeModeTest {

    @Test
    void effectiveProbeModeUsesOverrideThenPingOnlyThenProfile() {
        HostEntry trace = HostEntry.basic("8.8.8.8", true);
        assertEquals(HostProbeMode.TRACE, trace.effectiveProbeMode(HostProbeMode.TRACE));
        assertEquals(HostProbeMode.MTR, trace.effectiveProbeMode(HostProbeMode.MTR));

        HostEntry pingOnly = trace.withPingOnly(true);
        assertEquals(HostProbeMode.PING_ONLY, pingOnly.effectiveProbeMode(HostProbeMode.TRACE));

        HostEntry mtrOverride = trace.withProbeModeOverride(HostProbeMode.MTR);
        assertEquals(HostProbeMode.MTR, mtrOverride.effectiveProbeMode(HostProbeMode.TRACE));

        HostEntry overrideWins = pingOnly.withProbeModeOverride(HostProbeMode.TRACE);
        assertEquals(HostProbeMode.TRACE, overrideWins.effectiveProbeMode(HostProbeMode.MTR));
    }
}

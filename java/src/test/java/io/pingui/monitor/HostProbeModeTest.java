package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HostProbeModeTest {

    @Test
    void parseAcceptsYamlValues() {
        assertEquals(HostProbeMode.TRACE, HostProbeMode.parse("trace"));
        assertEquals(HostProbeMode.MTR, HostProbeMode.parse("mtr"));
        assertEquals(HostProbeMode.PING_ONLY, HostProbeMode.parse("ping_only"));
    }

    @Test
    void parseRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> HostProbeMode.parse("udp"));
    }
}

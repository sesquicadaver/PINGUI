package io.pingui.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HostProbeModeTest {

    @Test
    void parseAcceptsYamlValues() {
        assertEquals(HostProbeMode.TRACE, HostProbeMode.parse("trace"));
        assertEquals(HostProbeMode.MTR, HostProbeMode.parse("mtr"));
        assertEquals(HostProbeMode.PING_ONLY, HostProbeMode.parse("ping_only"));
        assertEquals(HostProbeMode.TCP_CONNECT, HostProbeMode.parse("tcp_connect"));
        assertTrue(HostProbeMode.TCP_CONNECT.isTargetOnly());
    }

    @Test
    void parseRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> HostProbeMode.parse("udp"));
    }
}

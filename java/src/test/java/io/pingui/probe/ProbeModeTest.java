package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProbeModeTest {
    @Test
    void parseKnownModes() {
        assertEquals(ProbeMode.AUTO, ProbeMode.parse("auto"));
        assertEquals(ProbeMode.PROCESS, ProbeMode.parse("process"));
        assertEquals(ProbeMode.RAW, ProbeMode.parse("icmp"));
    }

    @Test
    void parseRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ProbeMode.parse("udp"));
    }

    @Test
    void cliValueRoundTrip() {
        assertEquals("auto", ProbeMode.AUTO.cliValue());
        assertEquals("process", ProbeMode.PROCESS.cliValue());
        assertEquals("raw", ProbeMode.RAW.cliValue());
    }
}

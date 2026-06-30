package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessHostPingTest {
    @Test
    void buildCommandLinuxUsesSingleProbe() {
        List<String> cmd = ProcessHostPing.buildCommand("8.8.8.8", 0.5, false);
        assertEquals("ping", cmd.get(0));
        assertTrue(cmd.contains("-c"));
        assertTrue(cmd.contains("1"));
        assertEquals("8.8.8.8", cmd.get(cmd.size() - 1));
    }

    @Test
    void buildCommandWindowsUsesSingleProbe() {
        List<String> cmd = ProcessHostPing.buildCommand("8.8.8.8", 0.5, true);
        assertTrue(cmd.get(0).endsWith("ping") || cmd.get(0).endsWith("ping.exe"));
        assertTrue(cmd.contains("-n"));
        assertTrue(cmd.contains("1"));
        assertTrue(cmd.contains("-w"));
        assertEquals("8.8.8.8", cmd.get(cmd.size() - 1));
    }

    @Test
    void parseRttFromLinuxOutput() {
        assertEquals(
                12.3,
                ProcessHostPing.parseRtt(List.of("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms"))
                        .orElseThrow());
    }

    @Test
    void parseRttFromSubMillisecondOutput() {
        assertEquals(
                0.5,
                ProcessHostPing.parseRtt(List.of("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time<1 ms"))
                        .orElseThrow());
    }
}

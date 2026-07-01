package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.config.PingExpertEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessExpertPingTest {
    @Test
    void buildCommandAlwaysIncludesSingleProbeAndTimeout() {
        PingExpertEntry expert = new PingExpertEntry(false, List.of("-4", "-s", "128"));
        List<String> cmd = ProcessExpertPing.buildCommand("8.8.8.8", expert, 0.5);

        assertEquals("ping", cmd.get(0));
        assertTrue(cmd.contains("-c"));
        assertTrue(cmd.contains("1"));
        assertTrue(cmd.contains("-n"));
        assertTrue(cmd.contains("-W"));
        assertTrue(cmd.contains("-4"));
        assertTrue(cmd.contains("-s"));
        assertTrue(cmd.contains("128"));
        assertEquals("8.8.8.8", cmd.get(cmd.size() - 1));
    }

    @Test
    void buildCommandAutoAddsIpv6ForV6Literal() {
        List<String> cmd = ProcessExpertPing.buildCommand("2001:db8::1", PingExpertEntry.empty(), 0.5);
        assertTrue(cmd.contains("-6"));
        assertEquals("2001:db8::1", cmd.get(cmd.size() - 1));
    }
}

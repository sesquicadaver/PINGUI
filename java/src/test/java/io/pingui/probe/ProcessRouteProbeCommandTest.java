package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessRouteProbeCommandTest {
    @Test
    void buildCommandUnix() {
        ProcessRouteProbe probe = new ProcessRouteProbe();
        List<String> command = probe.buildCommand("8.8.8.8", 15, 0.5);
        assertEquals("traceroute", command.get(0));
        assertTrue(command.contains("-n"));
        assertTrue(command.contains("8.8.8.8"));
        assertTrue(command.contains("-m"));
        assertEquals("15", command.get(command.indexOf("-m") + 1));
    }
}

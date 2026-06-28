package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ProcessRouteProbeCommandTest {
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Test
    void buildCommandUsesPlatformTool() {
        ProcessRouteProbe probe = new ProcessRouteProbe();
        List<String> command = probe.buildCommand("8.8.8.8", 15, 0.5);
        if (isWindows()) {
            assertEquals("tracert", command.get(0));
            assertTrue(command.contains("-h"));
            assertEquals("15", command.get(command.indexOf("-h") + 1));
        } else {
            assertEquals("traceroute", command.get(0));
            assertTrue(command.contains("-n"));
            assertTrue(command.contains("-m"));
            assertEquals("15", command.get(command.indexOf("-m") + 1));
        }
        assertTrue(command.contains("8.8.8.8"));
    }
}

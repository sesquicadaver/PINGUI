package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProcessRouteProbeCommandTest {
    @Test
    void resolveTracerouteUsesMacOsSbinWhenPresent() {
        assertEquals(
                "/usr/sbin/traceroute",
                ProcessRouteProbe.resolveTracerouteExecutable("Mac OS X", path -> "/usr/sbin/traceroute".equals(path)));
    }

    @Test
    void resolveTracerouteFallsBackToPathNameOnMacWithoutSbin() {
        assertEquals("traceroute", ProcessRouteProbe.resolveTracerouteExecutable("Mac OS X", path -> false));
    }

    @Test
    void resolveTracerouteUsesPathNameOnLinux() {
        assertEquals("traceroute", ProcessRouteProbe.resolveTracerouteExecutable("Linux", path -> true));
    }

    @Test
    void buildCommandUsesResolvedTracerouteOnUnix() {
        ProcessRouteProbe probe = new ProcessRouteProbe(TracerouteFlavor.BSD);
        assertEquals("traceroute", probe.buildCommand("8.8.8.8", 20, 0.5).get(0));
    }

    @Test
    void buildCommandAddsIpv6FlagForLinuxBsd() {
        ProcessRouteProbe probe = new ProcessRouteProbe(TracerouteFlavor.BSD);
        assertTrue(probe.buildCommand("2001:db8::1", 20, 0.5).contains("-6"));
    }

    @Test
    void buildCommandSkipsIpv6FlagForHostname() {
        ProcessRouteProbe probe = new ProcessRouteProbe(TracerouteFlavor.BSD);
        assertFalse(probe.buildCommand("example.com", 20, 0.5).contains("-6"));
    }

    @Test
    void buildCommandWindowsAddsIpv6Flag() {
        ProcessRouteProbe probe = new ProcessRouteProbe(new WindowsTracertCommand());
        assertTrue(probe.buildCommand("2001:db8::1", 20, 0.5).contains("-6"));
    }

    @Test
    void buildCommandMacAddsIpv6Flag() {
        ProcessRouteProbe probe = new ProcessRouteProbe(new MacTracerouteCommand());
        assertTrue(probe.buildCommand("2001:db8::1", 20, 0.5).contains("-6"));
    }

    @Test
    void windowsTracertWaitMsUsesAtLeast4000() {
        assertEquals(4000, ProcessRouteProbe.windowsTracertWaitMs(0.5));
        assertEquals(5000, ProcessRouteProbe.windowsTracertWaitMs(5.0));
    }

    @Test
    void computeProcessWaitMsWindowsAllowsThreeProbesPerHop() {
        assertEquals(255_000L, ProcessRouteProbe.computeProcessWaitMs(true, 20, 0.5));
    }

    @Test
    void resolveTracertUsesSystem32WhenPresent() {
        String resolved = ProcessRouteProbe.resolveTracertExecutable(
                "C:\\Windows", path -> path.endsWith(Path.of("System32", "tracert.exe")));
        assertEquals(Path.of("C:\\Windows", "System32", "tracert.exe").toString(), resolved);
    }

    @Test
    void resolveTracertFallsBackToPathName() {
        assertEquals("tracert", ProcessRouteProbe.resolveTracertExecutable(null, path -> false));
    }
}

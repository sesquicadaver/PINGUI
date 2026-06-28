package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(
                "traceroute",
                ProcessRouteProbe.resolveTracerouteExecutable("Mac OS X", path -> false));
    }

    @Test
    void resolveTracerouteUsesPathNameOnLinux() {
        assertEquals(
                "traceroute",
                ProcessRouteProbe.resolveTracerouteExecutable("Linux", path -> true));
    }

    @Test
    void buildCommandUsesResolvedTracerouteOnUnix() {
        ProcessRouteProbe probe = new ProcessRouteProbe(ProcessRouteProbe.TracerouteFlavor.BSD);
        assertEquals("traceroute", probe.buildCommand("8.8.8.8", 20, 0.5).get(0));
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
}

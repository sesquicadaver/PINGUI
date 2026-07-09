package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class RouteProbeFactoryTest {
    @Test
    void processModeAlwaysUsesSubprocessProbe() {
        RouteProbe probe = RouteProbeFactory.create(ProbeMode.PROCESS);
        assertInstanceOf(ProcessRouteProbe.class, probe);
        assertEquals("process", RouteProbeFactory.describe(ProbeMode.PROCESS));
    }

    @Test
    void autoModeUsesDualStackWhenRawAvailable() {
        RouteProbe probe = RouteProbeFactory.create(ProbeMode.AUTO);
        if (io.pingui.probe.icmp.LinuxJnaIcmpTransport.isLinux()
                && io.pingui.probe.icmp.RawIcmpPermission.isAvailable()) {
            assertInstanceOf(DualStackRouteProbe.class, probe);
        } else {
            assertInstanceOf(ProcessRouteProbe.class, probe);
        }
    }
}

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
    void autoModeFallsBackToProcessWhenRawUnavailable() {
        RouteProbe probe = RouteProbeFactory.create(ProbeMode.AUTO);
        assertInstanceOf(RouteProbe.class, probe);
    }
}

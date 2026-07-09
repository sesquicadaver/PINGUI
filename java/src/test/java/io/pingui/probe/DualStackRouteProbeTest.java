package io.pingui.probe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.pingui.model.Models.HopNode;
import io.pingui.model.Models.RouteSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DualStackRouteProbeTest {

    @Test
    void requiresProcessTraceForIpv6Literal() {
        assertTrue(DualStackRouteProbe.requiresProcessTrace("2001:db8::1"));
        assertTrue(DualStackRouteProbe.requiresProcessTrace("[2001:db8::1]"));
        assertFalse(DualStackRouteProbe.requiresProcessTrace("8.8.8.8"));
        assertFalse(DualStackRouteProbe.requiresProcessTrace("example.com"));
    }

    @Test
    void ipv6TargetUsesProcessProbe() throws IOException {
        AtomicBoolean rawCalled = new AtomicBoolean();
        AtomicBoolean processCalled = new AtomicBoolean();
        RouteProbe raw = (host, maxHops, timeout) -> {
            rawCalled.set(true);
            throw new IOException("raw should not run");
        };
        RouteProbe process = (host, maxHops, timeout) -> {
            processCalled.set(true);
            return new RouteSnapshot(host, host, List.of(new HopNode(1, host, 1.0, false)));
        };
        DualStackRouteProbe probe = new DualStackRouteProbe(raw, process);
        probe.trace("2001:db8::1", 5, 1.0);
        assertTrue(processCalled.get());
        assertFalse(rawCalled.get());
    }

    @Test
    void ipv4TargetUsesRawProbe() throws IOException {
        AtomicBoolean rawCalled = new AtomicBoolean();
        RouteProbe raw = (host, maxHops, timeout) -> {
            rawCalled.set(true);
            return new RouteSnapshot(host, host, List.of(new HopNode(1, "10.0.0.1", 1.0, false)));
        };
        RouteProbe process = (host, maxHops, timeout) -> {
            throw new IOException("process should not run");
        };
        DualStackRouteProbe probe = new DualStackRouteProbe(raw, process);
        probe.trace("8.8.8.8", 5, 1.0);
        assertTrue(rawCalled.get());
    }
}

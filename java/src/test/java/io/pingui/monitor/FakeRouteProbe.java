package io.pingui.monitor;

import io.pingui.model.Models;
import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.RouteProbe;
import java.util.List;

/** In-memory probe for unit tests. */
public final class FakeRouteProbe implements RouteProbe {
    private final RouteSnapshot snapshot;

    public FakeRouteProbe(RouteSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) {
        return snapshot;
    }
}

package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.RouteProbe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** In-memory probe for unit tests. */
public final class FakeRouteProbe implements RouteProbe {
    private final List<RouteSnapshot> script;
    private int index;

    public FakeRouteProbe(RouteSnapshot snapshot) {
        this.script = new ArrayList<>();
        this.script.add(snapshot);
    }

    public FakeRouteProbe(RouteSnapshot first, RouteSnapshot... rest) {
        this.script = new ArrayList<>();
        this.script.add(first);
        if (rest != null) {
            this.script.addAll(Arrays.asList(rest));
        }
    }

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) {
        if (script.isEmpty()) {
            return null;
        }
        RouteSnapshot next = script.get(Math.min(index, script.size() - 1));
        if (index < script.size() - 1) {
            index++;
        }
        return next;
    }
}

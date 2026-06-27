package io.pingui.probe;

import io.pingui.model.Models.RouteSnapshot;
import java.io.IOException;

/** Cross-platform route tracing abstraction. */
public interface RouteProbe {
    RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) throws IOException;
}

package io.pingui.monitor;

import io.pingui.model.Models.RouteSnapshot;
import io.pingui.probe.RouteProbe;
import java.io.IOException;

/** Probe that fails for error-path tests. */
final class FailingRouteProbe implements RouteProbe {
    private final Exception failure;

    private FailingRouteProbe(Exception failure) {
        this.failure = failure;
    }

    static FailingRouteProbe io(String message) {
        return new FailingRouteProbe(new IOException(message));
    }

    static FailingRouteProbe runtime(String message) {
        return new FailingRouteProbe(new IllegalStateException(message));
    }

    @Override
    public RouteSnapshot trace(String targetHost, int maxHops, double timeoutSeconds) throws IOException {
        if (failure instanceof IOException io) {
            throw io;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new IOException(failure);
    }
}

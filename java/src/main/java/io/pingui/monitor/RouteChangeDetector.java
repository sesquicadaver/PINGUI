package io.pingui.monitor;

import java.util.List;

/** Detect route IP sequence changes. */
public final class RouteChangeDetector {
    private RouteChangeDetector() {}

    public static RouteChangeResult detect(List<String> previousIps, List<String> currentIps) {
        if (previousIps.isEmpty()) {
            return new RouteChangeResult(false, List.copyOf(previousIps), List.copyOf(currentIps));
        }
        if (previousIps.equals(currentIps)) {
            return new RouteChangeResult(false, List.copyOf(previousIps), List.copyOf(currentIps));
        }
        return new RouteChangeResult(true, List.copyOf(previousIps), List.copyOf(currentIps));
    }

    public record RouteChangeResult(boolean changed, List<String> oldIps, List<String> newIps) {}
}

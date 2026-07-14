package io.pingui.persistence.timeseries;

import java.util.ArrayList;
import java.util.List;

/** In-memory time-series backend for unit tests. */
public final class MemoryTimeSeriesBackend implements TimeSeriesBackend {
    private final List<PingSample> pingSamples = new ArrayList<>();
    private final List<RouteEvent> routeEvents = new ArrayList<>();

    @Override
    public void writePingSamples(List<PingSample> samples) {
        pingSamples.addAll(samples);
    }

    @Override
    public void writeRouteEvent(RouteEvent event) {
        routeEvents.add(event);
    }

    @Override
    public void close() {
        // no-op
    }

    public List<PingSample> pingSamples() {
        return List.copyOf(pingSamples);
    }

    public List<RouteEvent> routeEvents() {
        return List.copyOf(routeEvents);
    }
}

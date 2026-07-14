package io.pingui.persistence.timeseries;

import java.util.List;

/** Append-only writer for monitoring metrics (Python TimeSeriesBackend parity). */
public interface TimeSeriesBackend extends AutoCloseable {
    /** Persist one or more RTT samples. */
    void writePingSamples(List<PingSample> samples);

    /** Persist a route snapshot / change marker. */
    void writeRouteEvent(RouteEvent event);

    @Override
    void close();
}

package io.pingui;

import java.util.Optional;

/** CLI / env overrides for optional time-series backends (P15-020). */
public record CliTimeSeriesOverrides(
        Optional<String> backend,
        Optional<String> influxUrl,
        Optional<String> influxToken,
        Optional<String> influxOrg,
        Optional<String> influxBucket,
        Optional<String> timescaleDsn) {

    public static CliTimeSeriesOverrides none() {
        return new CliTimeSeriesOverrides(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public boolean isEmpty() {
        return backend.isEmpty()
                && influxUrl.isEmpty()
                && influxToken.isEmpty()
                && influxOrg.isEmpty()
                && influxBucket.isEmpty()
                && timescaleDsn.isEmpty();
    }
}

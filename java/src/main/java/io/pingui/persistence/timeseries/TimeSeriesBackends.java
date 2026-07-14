package io.pingui.persistence.timeseries;

import io.pingui.CliTimeSeriesOverrides;
import java.util.Optional;

/** Factory for optional time-series backends (Python create_timeseries_backend parity). */
public final class TimeSeriesBackends {
    private TimeSeriesBackends() {}

    /**
     * Build a configured backend or return {@code null} when disabled.
     *
     * <p>CLI values win over environment variables {@code INFLUXDB_*} / {@code PINGUI_TIMESCALE_DSN}.
     */
    public static TimeSeriesBackend create(CliTimeSeriesOverrides overrides) {
        CliTimeSeriesOverrides cfg = overrides != null ? overrides : CliTimeSeriesOverrides.none();
        String backend = cfg.backend().orElse(null);
        if (backend == null || backend.isBlank()) {
            return null;
        }
        String kind = backend.strip().toLowerCase();
        if (kind.equals("none") || kind.equals("off")) {
            return null;
        }
        if (kind.equals("influx")) {
            return createInflux(cfg);
        }
        if (kind.equals("timescale") || kind.equals("postgres") || kind.equals("postgresql")) {
            return createTimescale(cfg);
        }
        throw new TimeSeriesConfigException("Unknown time-series backend: '" + backend + "' (use influx or timescale)");
    }

    private static TimeSeriesBackend createInflux(CliTimeSeriesOverrides cfg) {
        String url = first(cfg.influxUrl(), env("INFLUXDB_URL"));
        String token = first(cfg.influxToken(), env("INFLUXDB_TOKEN"));
        String org = first(cfg.influxOrg(), env("INFLUXDB_ORG"));
        String bucket = first(cfg.influxBucket(), env("INFLUXDB_BUCKET"));
        StringBuilder missing = new StringBuilder();
        appendMissing(missing, "url", url);
        appendMissing(missing, "token", token);
        appendMissing(missing, "org", org);
        appendMissing(missing, "bucket", bucket);
        if (!missing.isEmpty()) {
            throw new TimeSeriesConfigException(
                    "InfluxDB backend requires: " + missing + " (CLI flags or INFLUXDB_* env vars)");
        }
        return new InfluxTimeSeriesBackend(url, token, org, bucket);
    }

    private static TimeSeriesBackend createTimescale(CliTimeSeriesOverrides cfg) {
        String dsn = first(cfg.timescaleDsn(), env("PINGUI_TIMESCALE_DSN"));
        if (dsn == null || dsn.isBlank()) {
            throw new TimeSeriesConfigException("Timescale backend requires --timescale-dsn or PINGUI_TIMESCALE_DSN");
        }
        return new TimescaleTimeSeriesBackend(dsn);
    }

    private static String first(Optional<String> cli, String envValue) {
        if (cli != null && cli.isPresent() && !cli.get().isBlank()) {
            return cli.get().strip();
        }
        return envValue;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void appendMissing(StringBuilder missing, String name, String value) {
        if (value == null || value.isBlank()) {
            if (!missing.isEmpty()) {
                missing.append(", ");
            }
            missing.append(name);
        }
    }
}

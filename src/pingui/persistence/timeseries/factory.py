"""Factory for optional time-series backends."""

from __future__ import annotations

import os

from pingui.persistence.timeseries.base import TimeSeriesBackend, TimeSeriesConfigError
from pingui.persistence.timeseries.influx import InfluxTimeSeriesBackend
from pingui.persistence.timeseries.timescale import TimescaleTimeSeriesBackend


def create_timeseries_backend(
    backend: str | None,
    *,
    influx_url: str | None = None,
    influx_token: str | None = None,
    influx_org: str | None = None,
    influx_bucket: str | None = None,
    timescale_dsn: str | None = None,
) -> TimeSeriesBackend | None:
    """Build a configured backend or return None when disabled."""
    if backend is None or backend.strip().lower() in {"", "none", "off"}:
        return None

    kind = backend.strip().lower()
    if kind == "influx":
        return _create_influx(
            url=influx_url or os.environ.get("INFLUXDB_URL"),
            token=influx_token or os.environ.get("INFLUXDB_TOKEN"),
            org=influx_org or os.environ.get("INFLUXDB_ORG"),
            bucket=influx_bucket or os.environ.get("INFLUXDB_BUCKET"),
        )
    if kind in {"timescale", "postgres", "postgresql"}:
        return _create_timescale(
            dsn=timescale_dsn or os.environ.get("PINGUI_TIMESCALE_DSN"),
        )
    msg = f"Unknown time-series backend: {backend!r} (use influx or timescale)"
    raise TimeSeriesConfigError(msg)


def _create_influx(
    *,
    url: str | None,
    token: str | None,
    org: str | None,
    bucket: str | None,
) -> InfluxTimeSeriesBackend:
    missing = [
        name
        for name, value in (
            ("url", url),
            ("token", token),
            ("org", org),
            ("bucket", bucket),
        )
        if not value
    ]
    if missing:
        msg = (
            "InfluxDB backend requires: "
            + ", ".join(missing)
            + " (CLI flags or INFLUXDB_* env vars)"
        )
        raise TimeSeriesConfigError(msg)
    assert url is not None
    assert token is not None
    assert org is not None
    assert bucket is not None
    return InfluxTimeSeriesBackend(
        url=url,
        token=token,
        org=org,
        bucket=bucket,
    )


def _create_timescale(*, dsn: str | None) -> TimescaleTimeSeriesBackend:
    if not dsn:
        msg = "Timescale backend requires --timescale-dsn or PINGUI_TIMESCALE_DSN"
        raise TimeSeriesConfigError(msg)
    try:
        return TimescaleTimeSeriesBackend(dsn)
    except TimeSeriesConfigError:
        raise
    except Exception as exc:
        if "psycopg" in exc.__class__.__module__:
            msg = f"Timescale/PostgreSQL connection failed: {exc}"
            raise TimeSeriesConfigError(msg) from exc
        raise

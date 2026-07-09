"""InfluxDB time-series backend."""

from __future__ import annotations

from pingui.persistence.timeseries.base import PingSample, RouteEvent, TimeSeriesConfigError


class InfluxTimeSeriesBackend:
    """Write ping/route metrics to InfluxDB 2.x."""

    PING_MEASUREMENT = "pingui_rtt"
    ROUTE_MEASUREMENT = "pingui_route"

    def __init__(
        self,
        *,
        url: str,
        token: str,
        org: str,
        bucket: str,
    ) -> None:
        try:
            from influxdb_client import InfluxDBClient
            from influxdb_client.client.write_api import SYNCHRONOUS
        except ImportError as exc:
            msg = (
                "InfluxDB backend requires influxdb-client. "
                "Install with: pip install 'pingui[timeseries]'"
            )
            raise TimeSeriesConfigError(msg) from exc

        self._org = org
        self._bucket = bucket
        self._client = InfluxDBClient(url=url, token=token, org=org)
        self._write_api = self._client.write_api(write_options=SYNCHRONOUS)

    def write_ping_samples(self, samples: list[PingSample]) -> None:
        if not samples:
            return
        from influxdb_client import Point, WritePrecision

        points = [
            (
                Point(self.PING_MEASUREMENT)
                .tag("target", sample.target_host)
                .tag("hop_ip", sample.hop_ip)
                .field("hop", sample.hop)
                .field("rtt_ms", sample.rtt_ms)
                .time(sample.observed_at, WritePrecision.NS)
            )
            for sample in samples
        ]
        self._write_api.write(bucket=self._bucket, org=self._org, record=points)

    def write_route_event(self, event: RouteEvent) -> None:
        from influxdb_client import Point, WritePrecision

        point = (
            Point(self.ROUTE_MEASUREMENT)
            .tag("target", event.target_host)
            .field("route_ips", ",".join(event.route_ips))
            .field("route_changed", int(event.route_changed))
            .time(event.observed_at, WritePrecision.NS)
        )
        self._write_api.write(bucket=self._bucket, org=self._org, record=point)

    def close(self) -> None:
        self._write_api.close()
        self._client.close()

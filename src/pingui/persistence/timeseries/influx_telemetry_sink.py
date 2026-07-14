"""TelemetrySink wrapper over B-05 / P15 TimeSeriesBackend (P16-052 / ADR_TELEMETRY).

Maps bus ``MetricSample`` / ``TelemetryEvent`` onto ``PingSample`` / ``RouteEvent`` so Influx
and Timescale share one emit path from the telemetry bus (no SessionStore dual-emit).
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from pingui.metric_names import RTT_MS
from pingui.models import ROUTE_CHANGE_EVENT_TYPE, MetricSample, TelemetryEvent
from pingui.persistence.timeseries.base import PingSample, RouteEvent

if TYPE_CHECKING:
    from pingui.persistence.timeseries.base import TimeSeriesBackend

logger = logging.getLogger(__name__)

LABEL_HOP_IP = "hop_ip"


class InfluxTelemetrySink:
    """
    Apply telemetry bus items to a :class:`TimeSeriesBackend` (Influx or Timescale).

    ``events_only`` is False — RTT samples must reach the backend. Failures are logged;
    methods never raise into the poll / emitter path.
    """

    ID = "influx"

    def __init__(self, backend: TimeSeriesBackend) -> None:
        self._backend = backend

    @property
    def id(self) -> str:
        return self.ID

    @property
    def events_only(self) -> bool:
        return False

    @property
    def backend(self) -> TimeSeriesBackend:
        return self._backend

    def on_sample(self, sample: MetricSample) -> None:
        if sample is None or sample.name != RTT_MS or sample.hop is None:
            return
        try:
            hop_ip = sample.labels.get(LABEL_HOP_IP)
            if hop_ip is None or not str(hop_ip).strip():
                hop_ip = f"hop{sample.hop}"
            self._backend.write_ping_samples(
                [
                    PingSample(
                        target_host=sample.host,
                        hop=sample.hop,
                        hop_ip=hop_ip,
                        rtt_ms=float(sample.value),
                        observed_at=sample.timestamp,
                    )
                ]
            )
        except Exception:  # noqa: BLE001 — sink isolation
            logger.warning("InfluxTelemetrySink sample failed for %s", sample.host, exc_info=True)

    def on_event(self, event: TelemetryEvent) -> None:
        if event is None or event.event != ROUTE_CHANGE_EVENT_TYPE:
            return
        try:
            self._backend.write_route_event(
                RouteEvent(
                    target_host=event.host,
                    route_ips=list(event.new_ips),
                    route_changed=bool(event.old_ips),
                    observed_at=event.timestamp,
                )
            )
        except Exception:  # noqa: BLE001 — sink isolation
            logger.warning("InfluxTelemetrySink event failed for %s", event.host, exc_info=True)

    def close(self) -> None:
        try:
            self._backend.close()
        except Exception:  # noqa: BLE001
            logger.warning("InfluxTelemetrySink close failed", exc_info=True)

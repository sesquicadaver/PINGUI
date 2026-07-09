"""In-memory time-series backend for unit tests."""

from __future__ import annotations

from pingui.persistence.timeseries.base import PingSample, RouteEvent


class MemoryTimeSeriesBackend:
    """Collects samples/events in RAM for assertions in tests."""

    def __init__(self) -> None:
        self.ping_samples: list[PingSample] = []
        self.route_events: list[RouteEvent] = []

    def write_ping_samples(self, samples: list[PingSample]) -> None:
        self.ping_samples.extend(samples)

    def write_route_event(self, event: RouteEvent) -> None:
        self.route_events.append(event)

    def close(self) -> None:
        return

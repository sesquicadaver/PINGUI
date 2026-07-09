"""Time-series backend protocol and shared record types."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol, runtime_checkable


class TimeSeriesConfigError(ValueError):
    """Raised when time-series backend configuration is invalid."""


@dataclass(frozen=True, slots=True)
class PingSample:
    """One RTT observation for a hop on a monitored target."""

    target_host: str
    hop: int
    hop_ip: str
    rtt_ms: float
    observed_at: datetime


@dataclass(frozen=True, slots=True)
class RouteEvent:
    """Route snapshot or route-change marker for a target."""

    target_host: str
    route_ips: list[str]
    route_changed: bool
    observed_at: datetime


@runtime_checkable
class TimeSeriesBackend(Protocol):
    """Append-only writer for monitoring metrics."""

    def write_ping_samples(self, samples: list[PingSample]) -> None:
        """Persist one or more RTT samples."""

    def write_route_event(self, event: RouteEvent) -> None:
        """Persist a route snapshot event."""

    def close(self) -> None:
        """Release backend resources."""

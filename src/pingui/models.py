"""Domain models for route monitoring."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    pass

TIMEOUT_IP = "*"


@dataclass(frozen=True, slots=True)
class HopNode:
    """Single hop on a traced route."""

    hop: int
    ip: str
    ping_ms: float | None
    is_timeout: bool = False

    @classmethod
    def timeout(cls, hop: int) -> HopNode:
        return cls(hop=hop, ip=TIMEOUT_IP, ping_ms=None, is_timeout=True)


@dataclass(slots=True)
class RouteSnapshot:
    """Complete route trace for one target at a point in time."""

    target: str
    target_ip: str
    nodes: list[HopNode]
    timestamp: datetime = field(default_factory=lambda: datetime.now(UTC))

    def route_ips(self) -> list[str]:
        """Return reachable hop IPs excluding timeout markers."""
        return [n.ip for n in self.nodes if not n.is_timeout and n.ip != TIMEOUT_IP]


@dataclass(slots=True)
class HostSessionData:
    """In-memory session state for one monitored host."""

    current_route: list[HopNode] = field(default_factory=list)
    previous_route: list[HopNode] = field(default_factory=list)
    last_known_by_hop: dict[int, HopNode] = field(default_factory=dict)
    ping_history: dict[str, list[float]] = field(default_factory=dict)
    enabled: bool = False

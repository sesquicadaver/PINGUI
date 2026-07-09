"""Domain models for route monitoring."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    pass

TIMEOUT_IP = "*"
MAX_HOP_RTT_SAMPLES = 50
ROUTE_CHANGE_EVENT_TYPE = "route_change"


@dataclass(slots=True)
class HopProbeStats:
    """Aggregated probe outcomes for one TTL hop index."""

    probes: int = 0
    successes: int = 0
    rtt_samples: list[float] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class HopStatsSummary:
    """Derived jitter/loss metrics for display."""

    jitter_ms: float | None
    loss_pct: float


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


@dataclass(frozen=True, slots=True)
class RouteChangeEvent:
    """Alert payload for a detected route change (P10-010 / PY-040)."""

    host: str
    old_ips: list[str]
    new_ips: list[str]
    timestamp: datetime
    profile: str = "default"

    @classmethod
    def from_route_change(
        cls,
        host: str,
        old_ips: list[str],
        new_ips: list[str],
        *,
        profile: str = "default",
        timestamp: datetime | None = None,
    ) -> RouteChangeEvent:
        return cls(
            host=host,
            old_ips=list(old_ips),
            new_ips=list(new_ips),
            timestamp=timestamp or datetime.now(UTC),
            profile=profile,
        )

    def to_dict(self) -> dict[str, object]:
        ts = self.timestamp.astimezone(UTC).isoformat().replace("+00:00", "Z")
        return {
            "event": ROUTE_CHANGE_EVENT_TYPE,
            "host": self.host,
            "old_ips": list(self.old_ips),
            "new_ips": list(self.new_ips),
            "timestamp": ts,
            "profile": self.profile,
        }

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), separators=(",", ":"))

    @classmethod
    def from_dict(cls, data: dict[str, object]) -> RouteChangeEvent:
        event_type = data.get("event")
        if event_type not in (ROUTE_CHANGE_EVENT_TYPE, None):
            msg = f"Unsupported event type: {event_type!r}"
            raise ValueError(msg)

        host = data.get("host")
        if not isinstance(host, str) or not host:
            msg = "host must be a non-empty string"
            raise ValueError(msg)

        old_ips = data.get("old_ips")
        new_ips = data.get("new_ips")
        if not isinstance(old_ips, list) or not all(isinstance(ip, str) for ip in old_ips):
            msg = "old_ips must be a list of strings"
            raise ValueError(msg)
        if not isinstance(new_ips, list) or not all(isinstance(ip, str) for ip in new_ips):
            msg = "new_ips must be a list of strings"
            raise ValueError(msg)

        timestamp_raw = data.get("timestamp")
        if not isinstance(timestamp_raw, str):
            msg = "timestamp must be an ISO-8601 string"
            raise ValueError(msg)
        normalized = timestamp_raw.replace("Z", "+00:00")
        timestamp = datetime.fromisoformat(normalized)

        profile = data.get("profile", "default")
        if not isinstance(profile, str):
            msg = "profile must be a string"
            raise ValueError(msg)

        return cls(
            host=host,
            old_ips=old_ips,
            new_ips=new_ips,
            timestamp=timestamp,
            profile=profile,
        )

    @classmethod
    def from_json(cls, raw: str) -> RouteChangeEvent:
        data = json.loads(raw)
        if not isinstance(data, dict):
            msg = "Route change payload must be a JSON object"
            raise TypeError(msg)
        return cls.from_dict(data)


@dataclass(slots=True)
class HostSessionData:
    """In-memory session state for one monitored host."""

    current_route: list[HopNode] = field(default_factory=list)
    previous_route: list[HopNode] = field(default_factory=list)
    last_known_by_hop: dict[int, HopNode] = field(default_factory=dict)
    ping_history: dict[str, list[float]] = field(default_factory=dict)
    hop_stats: dict[int, HopProbeStats] = field(default_factory=dict)
    enabled: bool = False

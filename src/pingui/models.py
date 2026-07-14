"""Domain models for route monitoring."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import TYPE_CHECKING

from pingui.metric_names import RTT_MS

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


METRIC_SAMPLE_KIND = "sample"
TELEMETRY_EVENT_KIND = "event"
PROBE_ERROR_EVENT_TYPE = "probe_error"
DAEMON_START_EVENT_TYPE = "daemon_start"


def _utc_z(timestamp: datetime) -> str:
    return timestamp.astimezone(UTC).isoformat().replace("+00:00", "Z")


def _parse_utc(timestamp_raw: str) -> datetime:
    return datetime.fromisoformat(timestamp_raw.replace("Z", "+00:00"))


def _require_non_blank(value: object, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        msg = f"{field} must be a non-empty string"
        raise ValueError(msg)
    return value


def _copy_labels(labels: dict[str, str] | None) -> dict[str, str]:
    if not labels:
        return {}
    out: dict[str, str] = {}
    for key in sorted(labels):
        if not isinstance(key, str) or not key.strip():
            msg = "label keys must be non-blank strings"
            raise ValueError(msg)
        value = labels[key]
        if not isinstance(value, str):
            msg = "label values must be strings"
            raise ValueError(msg)
        out[key] = value
    return out


@dataclass(frozen=True, slots=True)
class MetricSample:
    """High-frequency telemetry sample for the bus (P16-010 / ADR_TELEMETRY)."""

    name: str
    value: float
    host: str
    hop: int | None
    labels: dict[str, str]
    timestamp: datetime

    def __post_init__(self) -> None:
        _require_non_blank(self.name, "name")
        _require_non_blank(self.host, "host")
        if not isinstance(self.value, (int, float)) or isinstance(self.value, bool):
            msg = "value must be a finite number"
            raise ValueError(msg)
        if self.value != self.value or self.value in (float("inf"), float("-inf")):
            msg = "value must be a finite number"
            raise ValueError(msg)
        if self.hop is not None and self.hop < 1:
            msg = "hop must be >= 1 when present"
            raise ValueError(msg)
        object.__setattr__(self, "labels", _copy_labels(self.labels))

    @classmethod
    def rtt_ms(
        cls,
        host: str,
        hop: int,
        rtt_ms: float,
        *,
        labels: dict[str, str] | None = None,
        timestamp: datetime | None = None,
    ) -> MetricSample:
        """Factory for ``pingui_rtt_ms`` hop samples."""
        return cls(
            name=RTT_MS,
            value=rtt_ms,
            host=host,
            hop=hop,
            labels=labels or {},
            timestamp=timestamp or datetime.now(UTC),
        )

    def to_dict(self) -> dict[str, object]:
        return {
            "kind": METRIC_SAMPLE_KIND,
            "name": self.name,
            "value": self.value,
            "host": self.host,
            "hop": self.hop,
            "labels": dict(self.labels),
            "timestamp": _utc_z(self.timestamp),
        }

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), separators=(",", ":"))

    @classmethod
    def from_dict(cls, data: dict[str, object]) -> MetricSample:
        kind = data.get("kind")
        if kind not in (METRIC_SAMPLE_KIND, None):
            msg = f"Unsupported kind: {kind!r}"
            raise ValueError(msg)
        name = data.get("name")
        name = _require_non_blank(name, "name")
        value = data.get("value")
        if not isinstance(value, (int, float)) or isinstance(value, bool):
            msg = "value must be a number"
            raise ValueError(msg)
        if value != value or value in (float("inf"), float("-inf")):
            msg = "value must be a finite number"
            raise ValueError(msg)
        host = _require_non_blank(data.get("host"), "host")
        hop = data.get("hop")
        if hop is not None and not isinstance(hop, int):
            msg = "hop must be an int or null"
            raise ValueError(msg)
        labels_raw = data.get("labels", {})
        if not isinstance(labels_raw, dict):
            msg = "labels must be an object"
            raise ValueError(msg)
        for key, label_value in labels_raw.items():
            if not isinstance(key, str) or not isinstance(label_value, str):
                msg = "labels must be string-to-string"
                raise ValueError(msg)
        labels = dict(labels_raw)
        timestamp_raw = data.get("timestamp")
        if not isinstance(timestamp_raw, str):
            msg = "timestamp must be an ISO-8601 string"
            raise ValueError(msg)
        return cls(
            name=name,
            value=float(value),
            host=host,
            hop=hop,
            labels=labels,
            timestamp=_parse_utc(timestamp_raw),
        )

    @classmethod
    def from_json(cls, raw: str) -> MetricSample:
        data = json.loads(raw)
        if not isinstance(data, dict):
            msg = "MetricSample payload must be a JSON object"
            raise TypeError(msg)
        return cls.from_dict(data)


@dataclass(frozen=True, slots=True)
class TelemetryEvent:
    """Rare telemetry event for the bus (P16-010 / ADR_TELEMETRY)."""

    event: str
    host: str
    labels: dict[str, str]
    timestamp: datetime
    message: str | None = None
    old_ips: list[str] = field(default_factory=list)
    new_ips: list[str] = field(default_factory=list)

    def __post_init__(self) -> None:
        _require_non_blank(self.event, "event")
        _require_non_blank(self.host, "host")
        object.__setattr__(self, "labels", _copy_labels(self.labels))
        object.__setattr__(self, "old_ips", list(self.old_ips))
        object.__setattr__(self, "new_ips", list(self.new_ips))
        if self.message is not None and not str(self.message).strip():
            object.__setattr__(self, "message", None)

    @classmethod
    def route_change(
        cls,
        host: str,
        old_ips: list[str],
        new_ips: list[str],
        *,
        labels: dict[str, str] | None = None,
        timestamp: datetime | None = None,
    ) -> TelemetryEvent:
        return cls(
            event=ROUTE_CHANGE_EVENT_TYPE,
            host=host,
            labels=labels or {},
            timestamp=timestamp or datetime.now(UTC),
            message=None,
            old_ips=list(old_ips),
            new_ips=list(new_ips),
        )

    @classmethod
    def probe_error(
        cls,
        host: str,
        message: str,
        *,
        labels: dict[str, str] | None = None,
        timestamp: datetime | None = None,
    ) -> TelemetryEvent:
        return cls(
            event=PROBE_ERROR_EVENT_TYPE,
            host=host,
            labels=labels or {},
            timestamp=timestamp or datetime.now(UTC),
            message=message,
            old_ips=[],
            new_ips=[],
        )

    def to_dict(self) -> dict[str, object]:
        return {
            "kind": TELEMETRY_EVENT_KIND,
            "event": self.event,
            "host": self.host,
            "labels": dict(self.labels),
            "message": self.message,
            "old_ips": list(self.old_ips),
            "new_ips": list(self.new_ips),
            "timestamp": _utc_z(self.timestamp),
        }

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), separators=(",", ":"), sort_keys=False)

    @classmethod
    def from_dict(cls, data: dict[str, object]) -> TelemetryEvent:
        kind = data.get("kind")
        if kind not in (TELEMETRY_EVENT_KIND, None):
            msg = f"Unsupported kind: {kind!r}"
            raise ValueError(msg)
        event = _require_non_blank(data.get("event"), "event")
        host = _require_non_blank(data.get("host"), "host")
        labels_raw = data.get("labels", {})
        if not isinstance(labels_raw, dict):
            msg = "labels must be an object"
            raise ValueError(msg)
        for key, label_value in labels_raw.items():
            if not isinstance(key, str) or not isinstance(label_value, str):
                msg = "labels must be string-to-string"
                raise ValueError(msg)
        labels = dict(labels_raw)
        message = data.get("message")
        if message is not None and not isinstance(message, str):
            msg = "message must be a string or null"
            raise ValueError(msg)
        old_ips = data.get("old_ips", [])
        new_ips = data.get("new_ips", [])
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
        return cls(
            event=event,
            host=host,
            labels=labels,
            timestamp=_parse_utc(timestamp_raw),
            message=message,
            old_ips=old_ips,
            new_ips=new_ips,
        )

    @classmethod
    def from_json(cls, raw: str) -> TelemetryEvent:
        data = json.loads(raw)
        if not isinstance(data, dict):
            msg = "TelemetryEvent payload must be a JSON object"
            raise TypeError(msg)
        return cls.from_dict(data)

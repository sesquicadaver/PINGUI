"""In-memory session storage for route and ping metrics."""

from __future__ import annotations

from typing import TYPE_CHECKING

from pingui.config import MAX_HOSTS, ConfigError, validate_session_host
from pingui.models import TIMEOUT_IP, HopNode, HostSessionData, RouteSnapshot
from pingui.monitor.route_history import record_last_known, route_with_last_known_ips

if TYPE_CHECKING:
    from pingui.persistence.session_db import SessionDatabase

MAX_PING_SAMPLES = 50


class SessionStore:
    """Session metrics per host; optional SQLite persistence between runs."""

    def __init__(
        self,
        hosts: list[str],
        *,
        session_db: SessionDatabase | None = None,
    ) -> None:
        self._db = session_db
        self._data: dict[str, HostSessionData] = {}
        for host in hosts:
            if session_db is not None:
                loaded = session_db.load(host)
                self._data[host] = loaded if loaded is not None else HostSessionData()
            else:
                self._data[host] = HostSessionData()

    def hosts(self) -> list[str]:
        return list(self._data.keys())

    def host_count(self) -> int:
        return len(self._data)

    def can_add_host(self) -> bool:
        return len(self._data) < MAX_HOSTS

    def add_host(self, host: str, *, enabled: bool = False) -> str:
        """Register a target in the session list."""
        normalized = validate_session_host(host, self.hosts())
        if normalized in self._data:
            msg = f"Host already in list: {normalized}"
            raise ConfigError(msg)
        self._data[normalized] = HostSessionData(enabled=enabled)
        self._persist(normalized)
        return normalized

    def remove_host(self, host: str) -> None:
        if host not in self._data:
            msg = f"Unknown host: {host}"
            raise ConfigError(msg)
        del self._data[host]
        if self._db is not None:
            self._db.delete(host)

    def set_enabled(self, host: str, enabled: bool) -> None:
        self._data[host].enabled = enabled
        self._persist(host)

    def rename_host(self, old: str, new: str) -> str:
        others = [h for h in self.hosts() if h != old]
        normalized = validate_session_host(new, others)
        self._data[normalized] = self._data.pop(old)
        if self._db is not None:
            self._db.rename(old, normalized)
        else:
            self._persist(normalized)
        return normalized

    def get(self, host: str) -> HostSessionData:
        return self._data[host]

    def inactive_route(self, host: str) -> list[HopNode]:
        """Previous route with last known IPs filled in for timeout hops."""
        data = self._data[host]
        return route_with_last_known_ips(
            data.previous_route,
            data.last_known_by_hop,
        )

    def update_route(self, host: str, snapshot: RouteSnapshot) -> None:
        """Replace current route; retain enriched previous hop list on change."""
        data = self._data[host]
        old_ips = self._route_ips(data.current_route)
        new_ips = snapshot.route_ips()
        if data.current_route and old_ips != new_ips:
            data.previous_route = route_with_last_known_ips(
                data.current_route,
                data.last_known_by_hop,
            )
        record_last_known(data.last_known_by_hop, snapshot.nodes)
        data.current_route = list(snapshot.nodes)
        self._persist(host)

    @staticmethod
    def _route_ips(route: list[HopNode]) -> list[str]:
        return [
            n.ip
            for n in route
            if not n.is_timeout and n.ip != TIMEOUT_IP
        ]

    def append_ping_samples(self, host: str, snapshot: RouteSnapshot) -> None:
        """Append RTT samples from snapshot, trimming history per IP."""
        history = self._data[host].ping_history
        changed = False
        for node in snapshot.nodes:
            if node.is_timeout or node.ip == "*" or node.ping_ms is None:
                continue
            samples = history.setdefault(node.ip, [])
            samples.append(node.ping_ms)
            if len(samples) > MAX_PING_SAMPLES:
                del samples[:-MAX_PING_SAMPLES]
            changed = True
        if changed:
            self._persist(host)

    def avg_ping(self, host: str, ip: str) -> float | None:
        """Return average ping for IP on host, or None if no samples."""
        samples = self._data[host].ping_history.get(ip)
        if not samples:
            return None
        return sum(samples) / len(samples)

    def flush_all(self) -> None:
        """Write all hosts to SQLite when persistence is enabled."""
        if self._db is None:
            return
        for host, data in self._data.items():
            self._db.save(host, data)

    def close(self) -> None:
        """Flush and close optional SQLite backend."""
        self.flush_all()
        if self._db is not None:
            self._db.close()
            self._db = None

    def _persist(self, host: str) -> None:
        if self._db is not None:
            self._db.save(host, self._data[host])

    @staticmethod
    def extract_route_ips(snapshot: RouteSnapshot) -> list[str]:
        """Extract ordered IP list from snapshot, excluding timeouts."""
        return snapshot.route_ips()

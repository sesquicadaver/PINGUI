"""In-memory session storage for route and ping metrics."""

from __future__ import annotations

from pingui.config import MAX_HOSTS, ConfigError, validate_session_host
from pingui.models import TIMEOUT_IP, HopNode, HostSessionData, RouteSnapshot
from pingui.monitor.route_history import record_last_known, route_with_last_known_ips

MAX_PING_SAMPLES = 50


class SessionStore:
    """RAM-only store for current session metrics per host."""

    def __init__(self, hosts: list[str]) -> None:
        self._data: dict[str, HostSessionData] = {
            host: HostSessionData() for host in hosts
        }

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
        return normalized

    def remove_host(self, host: str) -> None:
        if host not in self._data:
            msg = f"Unknown host: {host}"
            raise ConfigError(msg)
        del self._data[host]

    def set_enabled(self, host: str, enabled: bool) -> None:
        self._data[host].enabled = enabled

    def rename_host(self, old: str, new: str) -> str:
        others = [h for h in self.hosts() if h != old]
        normalized = validate_session_host(new, others)
        self._data[normalized] = self._data.pop(old)
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
        for node in snapshot.nodes:
            if node.is_timeout or node.ip == "*" or node.ping_ms is None:
                continue
            samples = history.setdefault(node.ip, [])
            samples.append(node.ping_ms)
            if len(samples) > MAX_PING_SAMPLES:
                del samples[:-MAX_PING_SAMPLES]

    def avg_ping(self, host: str, ip: str) -> float | None:
        """Return average ping for IP on host, or None if no samples."""
        samples = self._data[host].ping_history.get(ip)
        if not samples:
            return None
        return sum(samples) / len(samples)

    @staticmethod
    def extract_route_ips(snapshot: RouteSnapshot) -> list[str]:
        """Extract ordered IP list from snapshot, excluding timeouts."""
        return snapshot.route_ips()

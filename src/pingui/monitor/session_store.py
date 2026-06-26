"""In-memory session storage for route and ping metrics."""

from __future__ import annotations

from pingui.models import HostSessionData, RouteSnapshot

MAX_PING_SAMPLES = 50


class SessionStore:
    """RAM-only store for current session metrics per host."""

    def __init__(self, hosts: list[str]) -> None:
        self._data: dict[str, HostSessionData] = {
            host: HostSessionData() for host in hosts
        }

    def hosts(self) -> list[str]:
        return list(self._data.keys())

    def get(self, host: str) -> HostSessionData:
        return self._data[host]

    def update_route(self, host: str, snapshot: RouteSnapshot) -> None:
        """Replace current route snapshot for host."""
        self._data[host].current_route = list(snapshot.nodes)

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

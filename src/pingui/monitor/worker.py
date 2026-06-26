"""Background monitoring worker thread."""

from __future__ import annotations

import logging
import time
from threading import Lock

from PyQt6.QtCore import QThread, pyqtSignal

from pingui.config import MAX_HOSTS, ConfigError, validate_session_host
from pingui.icmp.raw_socket import ProbeTransport
from pingui.monitor.polling import poll_host_route

logger = logging.getLogger(__name__)


class LightweightMonitorWorker(QThread):
    """
    Background thread cycling through enabled hosts only.

    Emits route snapshots and route-change alerts without blocking the GUI.
    """

    data_received = pyqtSignal(str, object)
    route_changed = pyqtSignal(str, list, list)
    probe_error = pyqtSignal(str, str)

    def __init__(
        self,
        hosts: list[str],
        interval_seconds: float = 1.0,
        max_hops: int = 20,
        timeout: float = 0.5,
        transport: ProbeTransport | None = None,
    ) -> None:
        super().__init__()
        self._lock = Lock()
        self._hosts = list(hosts)
        self._enabled: set[str] = set()
        self._interval = interval_seconds
        self._max_hops = max_hops
        self._timeout = timeout
        self._transport = transport
        self._running = True
        self._last_routes: dict[str, list[str]] = {h: [] for h in hosts}

    def hosts(self) -> list[str]:
        with self._lock:
            return list(self._hosts)

    def enabled_hosts(self) -> list[str]:
        with self._lock:
            return [host for host in self._hosts if host in self._enabled]

    def can_add_host(self) -> bool:
        with self._lock:
            return len(self._hosts) < MAX_HOSTS

    def add_host(self, host: str, *, enabled: bool = False) -> str:
        """Append a target to the session list (thread-safe)."""
        normalized = validate_session_host(host, self.hosts())
        with self._lock:
            if normalized in self._hosts:
                msg = f"Host already in list: {normalized}"
                raise ConfigError(msg)
            self._hosts.append(normalized)
            self._last_routes[normalized] = []
            if enabled:
                self._enabled.add(normalized)
        return normalized

    def set_host_enabled(self, host: str, enabled: bool) -> None:
        with self._lock:
            if host not in self._hosts:
                msg = f"Unknown host: {host}"
                raise ConfigError(msg)
            if enabled:
                if len(self._enabled) >= MAX_HOSTS and host not in self._enabled:
                    msg = f"Maximum {MAX_HOSTS} active traces at once"
                    raise ConfigError(msg)
                self._enabled.add(host)
            else:
                self._enabled.discard(host)

    def rename_host(self, old: str, new: str) -> str:
        others = [h for h in self.hosts() if h != old]
        normalized = validate_session_host(new, others)
        with self._lock:
            if old not in self._hosts:
                msg = f"Unknown host: {old}"
                raise ConfigError(msg)
            idx = self._hosts.index(old)
            self._hosts[idx] = normalized
            if old in self._enabled:
                self._enabled.discard(old)
                self._enabled.add(normalized)
            self._last_routes[normalized] = self._last_routes.pop(old, [])
        return normalized

    def remove_host(self, host: str) -> None:
        with self._lock:
            if host not in self._hosts:
                msg = f"Unknown host: {host}"
                raise ConfigError(msg)
            self._hosts.remove(host)
            self._enabled.discard(host)
            self._last_routes.pop(host, None)

    def run(self) -> None:
        while self._running:
            with self._lock:
                hosts = [host for host in self._hosts if host in self._enabled]
            for host in hosts:
                if not self._running:
                    break
                with self._lock:
                    previous_ips = list(self._last_routes.get(host, []))
                outcome = poll_host_route(
                    host,
                    previous_ips,
                    max_hops=self._max_hops,
                    timeout=self._timeout,
                    transport=self._transport,
                )
                if outcome.error is not None:
                    logger.warning("Probe failed for %s: %s", host, outcome.error)
                    self.probe_error.emit(host, outcome.error)
                    continue

                if outcome.route_changed:
                    self.route_changed.emit(host, outcome.old_ips, outcome.new_ips)
                with self._lock:
                    self._last_routes[host] = outcome.current_ips
                if outcome.snapshot is not None:
                    self.data_received.emit(host, outcome.snapshot)

            if self._running:
                time.sleep(self._interval)

    def stop(self) -> None:
        """Request worker loop termination."""
        self._running = False

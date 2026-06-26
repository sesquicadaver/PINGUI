"""Background monitoring worker thread."""

from __future__ import annotations

import logging
import time

from PyQt6.QtCore import QThread, pyqtSignal

from pingui.icmp.raw_socket import ProbeTransport
from pingui.monitor.polling import poll_host_route

logger = logging.getLogger(__name__)


class LightweightMonitorWorker(QThread):
    """
    Background thread cycling through monitored hosts.

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
        self._hosts = list(hosts)
        self._interval = interval_seconds
        self._max_hops = max_hops
        self._timeout = timeout
        self._transport = transport
        self._running = True
        self._last_routes: dict[str, list[str]] = {h: [] for h in hosts}

    def run(self) -> None:
        while self._running:
            for host in self._hosts:
                if not self._running:
                    break
                outcome = poll_host_route(
                    host,
                    self._last_routes[host],
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
                self._last_routes[host] = outcome.current_ips
                if outcome.snapshot is not None:
                    self.data_received.emit(host, outcome.snapshot)

            if self._running:
                time.sleep(self._interval)

    def stop(self) -> None:
        """Request worker loop termination."""
        self._running = False

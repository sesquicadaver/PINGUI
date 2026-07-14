"""Background monitoring worker thread."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from PyQt6.QtCore import QThread, pyqtSignal

from pingui.monitor.monitor_loop import MonitorCallbacks, MonitorLoop
from pingui.telemetry_emit import NULL_TELEMETRY, TelemetryEmitter

if TYPE_CHECKING:
    from pingui.icmp.raw_socket import ProbeTransport
    from pingui.monitor.session_store import SessionStore

logger = logging.getLogger(__name__)


class LightweightMonitorWorker(QThread):
    """
    Qt thread wrapper around :class:`MonitorLoop`.

    When ``session_store`` is provided, enabled state lives only in the store.
    """

    data_received = pyqtSignal(str, object)
    route_changed = pyqtSignal(str, list, list)
    probe_error = pyqtSignal(str, str)

    def __init__(
        self,
        hosts: list[str] | None = None,
        interval_seconds: float = 1.0,
        max_hops: int = 20,
        timeout: float = 0.5,
        transport: ProbeTransport | None = None,
        *,
        session_store: SessionStore | None = None,
        telemetry: TelemetryEmitter | None = None,
    ) -> None:
        super().__init__()
        self._loop = MonitorLoop(
            hosts if session_store is None else None,
            session_store=session_store,
            interval_seconds=interval_seconds,
            max_hops=max_hops,
            timeout=timeout,
            transport=transport,
            callbacks=MonitorCallbacks(
                on_data_received=self._on_data_received,
                on_route_changed=self._on_route_changed,
                on_probe_error=self._on_probe_error,
            ),
            telemetry=telemetry or NULL_TELEMETRY,
        )

    def set_telemetry(self, telemetry: TelemetryEmitter | None) -> None:
        """Forward optional telemetry emitter to the underlying loop (P16-013)."""
        self._loop.set_telemetry(telemetry)

    def _on_data_received(self, host: str, snapshot: object) -> None:
        self.data_received.emit(host, snapshot)

    def _on_route_changed(self, host: str, old_ips: list[str], new_ips: list[str]) -> None:
        self.route_changed.emit(host, old_ips, new_ips)

    def _on_probe_error(self, host: str, message: str) -> None:
        self.probe_error.emit(host, message)

    def hosts(self) -> list[str]:
        return self._loop.hosts()

    def enabled_hosts(self) -> list[str]:
        return self._loop.enabled_hosts()

    def can_add_host(self) -> bool:
        return self._loop.can_add_host()

    def add_host(self, host: str, *, enabled: bool = False) -> str:
        return self._loop.add_host(host, enabled=enabled)

    def set_host_enabled(self, host: str, enabled: bool) -> None:
        self._loop.set_host_enabled(host, enabled)

    def rename_host(self, old: str, new: str) -> str:
        return self._loop.rename_host(old, new)

    def remove_host(self, host: str) -> None:
        self._loop.remove_host(host)

    def run(self) -> None:
        self._loop._running = True
        self._loop._run()

    def stop(self) -> None:
        self._loop.stop()

"""Worker thread integration tests (Qt event loop)."""

from __future__ import annotations

import time

import pytest

pytest.importorskip("PyQt6")

from PyQt6.QtWidgets import QApplication

from pingui.icmp.raw_socket import ProbeResult
from pingui.monitor.worker import LightweightMonitorWorker


class OkTransport:
    """Fake transport returning a two-hop route."""

    def send_probe(
        self,
        target_ip: str,
        ttl: int,
        timeout: float,
    ) -> ProbeResult | None:
        _ = timeout
        if ttl == 1:
            return ProbeResult("10.0.0.1", 5.0, False)
        if ttl == 2:
            return ProbeResult(target_ip, 10.0, True)
        return None


class FailTransport:
    def send_probe(
        self,
        target_ip: str,
        ttl: int,
        timeout: float,
    ) -> ProbeResult | None:
        _ = target_ip, ttl, timeout
        raise OSError("permission denied")


def _qt_app() -> QApplication:
    return QApplication.instance() or QApplication([])


def test_worker_emits_snapshot_when_enabled() -> None:
    app = _qt_app()
    received: list[tuple[str, object]] = []
    worker = LightweightMonitorWorker(
        ["8.8.8.8"],
        interval_seconds=0.05,
        transport=OkTransport(),
    )
    worker.data_received.connect(lambda host, snap: received.append((host, snap)))
    worker.set_host_enabled("8.8.8.8", True)
    worker.start()
    deadline = time.monotonic() + 3.0
    while time.monotonic() < deadline and not received:
        app.processEvents()
        time.sleep(0.02)
    worker.stop()
    worker.wait(3000)
    assert received
    assert received[0][0] == "8.8.8.8"


def test_worker_emits_probe_error() -> None:
    app = _qt_app()
    errors: list[tuple[str, str]] = []
    worker = LightweightMonitorWorker(
        ["8.8.8.8"],
        interval_seconds=0.05,
        transport=FailTransport(),
    )
    worker.probe_error.connect(lambda host, msg: errors.append((host, msg)))
    worker.set_host_enabled("8.8.8.8", True)
    worker.start()
    deadline = time.monotonic() + 3.0
    while time.monotonic() < deadline and not errors:
        app.processEvents()
        time.sleep(0.02)
    worker.stop()
    worker.wait(3000)
    assert errors
    assert errors[0] == ("8.8.8.8", "permission denied")


def test_worker_emits_route_changed_on_second_cycle() -> None:
    app = _qt_app()
    changes: list[tuple[str, list[str], list[str]]] = []

    class ChangingTransport:
        def __init__(self) -> None:
            self.completed_traces = 0

        def send_probe(
            self,
            target_ip: str,
            ttl: int,
            timeout: float,
        ) -> ProbeResult | None:
            _ = timeout
            if ttl == 1:
                hop = "10.0.0.1" if self.completed_traces == 0 else "192.168.1.1"
                return ProbeResult(hop, 5.0, False)
            if ttl == 2:
                self.completed_traces += 1
                return ProbeResult(target_ip, 10.0, True)
            return None

    worker = LightweightMonitorWorker(
        ["8.8.8.8"],
        interval_seconds=0.05,
        transport=ChangingTransport(),
    )
    worker.route_changed.connect(
        lambda host, old, new: changes.append((host, old, new))
    )
    worker.set_host_enabled("8.8.8.8", True)
    worker.start()
    deadline = time.monotonic() + 5.0
    while time.monotonic() < deadline and not changes:
        app.processEvents()
        time.sleep(0.02)
    worker.stop()
    worker.wait(3000)
    assert changes
    assert changes[0][0] == "8.8.8.8"
    assert "192.168.1.1" in changes[0][2]

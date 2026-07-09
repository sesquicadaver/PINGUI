"""MonitorLoop unit tests."""

from __future__ import annotations

import time

import pytest

from pingui.config import ConfigError
from pingui.icmp.raw_socket import ProbeResult
from pingui.monitor.monitor_loop import MonitorCallbacks, MonitorLoop
from pingui.monitor.session_store import SessionStore


class OkTransport:
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


def test_monitor_loop_internal_enabled_hosts() -> None:
    loop = MonitorLoop(["8.8.8.8", "1.1.1.1"], interval_seconds=60.0)
    loop.set_host_enabled("8.8.8.8", True)
    assert loop.enabled_hosts() == ["8.8.8.8"]


def test_monitor_loop_store_enabled_hosts() -> None:
    store = SessionStore(["8.8.8.8"])
    store.set_enabled("8.8.8.8", True)
    loop = MonitorLoop(session_store=store, interval_seconds=60.0)
    assert loop.enabled_hosts() == ["8.8.8.8"]


def test_monitor_loop_callbacks_fire() -> None:
    received: list[str] = []
    loop = MonitorLoop(
        ["8.8.8.8"],
        interval_seconds=0.05,
        transport=OkTransport(),
        callbacks=MonitorCallbacks(
            on_data_received=lambda host, _snap: received.append(host),
        ),
    )
    loop.set_host_enabled("8.8.8.8", True)
    loop.start()
    deadline = time.monotonic() + 3.0
    while time.monotonic() < deadline and not received:
        time.sleep(0.02)
    loop.stop()
    loop.join(timeout=3.0)
    assert received == ["8.8.8.8"]


def test_monitor_loop_store_add_host() -> None:
    store = SessionStore([])
    loop = MonitorLoop(session_store=store, interval_seconds=60.0)
    added = loop.add_host("8.8.8.8", enabled=True)
    assert added == "8.8.8.8"
    assert store.hosts() == ["8.8.8.8"]


def test_monitor_loop_store_unknown_host() -> None:
    store = SessionStore(["8.8.8.8"])
    loop = MonitorLoop(session_store=store, interval_seconds=60.0)
    with pytest.raises(ConfigError, match="Unknown"):
        loop.set_host_enabled("9.9.9.9", True)

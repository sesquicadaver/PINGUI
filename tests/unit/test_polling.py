"""Polling logic unit tests."""

from __future__ import annotations

from pingui.icmp.raw_socket import ProbeResult
from pingui.monitor.polling import poll_host_route


class OkTransport:
    def __init__(self) -> None:
        self.calls = 0

    def send_probe(
        self,
        target_ip: str,
        ttl: int,
        timeout: float,
    ) -> ProbeResult | None:
        _ = target_ip, timeout
        self.calls += 1
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


def test_poll_host_success() -> None:
    outcome = poll_host_route("8.8.8.8", [], transport=OkTransport())
    assert outcome.error is None
    assert outcome.snapshot is not None
    assert outcome.current_ips == ["10.0.0.1", "8.8.8.8"]
    assert outcome.route_changed is False


def test_poll_host_detects_change() -> None:
    transport = OkTransport()
    first = poll_host_route("8.8.8.8", [], transport=transport)
    assert first.route_changed is False

    class AltTransport:
        def send_probe(
            self,
            target_ip: str,
            ttl: int,
            timeout: float,
        ) -> ProbeResult | None:
            _ = target_ip, timeout
            if ttl == 1:
                return ProbeResult("192.168.1.1", 2.0, False)
            if ttl == 2:
                return ProbeResult(target_ip, 4.0, True)
            return None

    second = poll_host_route(
        "8.8.8.8",
        first.current_ips,
        transport=AltTransport(),
    )
    assert second.route_changed is True
    assert second.old_ips == ["10.0.0.1", "8.8.8.8"]
    assert second.new_ips == ["192.168.1.1", "8.8.8.8"]


def test_poll_host_os_error() -> None:
    outcome = poll_host_route("8.8.8.8", ["1.1.1.1"], transport=FailTransport())
    assert outcome.error == "permission denied"
    assert outcome.snapshot is None
    assert outcome.current_ips == ["1.1.1.1"]

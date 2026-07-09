"""Contract tests for tracer with mock transport."""

from __future__ import annotations

import pytest

from pingui.icmp.raw_socket import ProbeResult
from pingui.icmp.tracer import trace_route
from pingui.models import TIMEOUT_IP


class MockTransport:
    """Deterministic hop responses for contract testing."""

    def __init__(self, hops: dict[int, ProbeResult | None]) -> None:
        self._hops = hops
        self.calls: list[int] = []

    def send_probe(
        self,
        target_ip: str,
        ttl: int,
        timeout: float,
    ) -> ProbeResult | None:
        _ = target_ip, timeout
        self.calls.append(ttl)
        return self._hops.get(ttl)


def test_trace_route_stops_at_target() -> None:
    transport = MockTransport(
        {
            1: ProbeResult("10.0.0.1", 5.0, False),
            2: ProbeResult("8.8.8.8", 12.0, True),
        }
    )
    snap = trace_route("8.8.8.8", max_hops=10, transport=transport)
    assert len(snap.nodes) == 2
    assert snap.nodes[1].ip == "8.8.8.8"
    assert transport.calls == [1, 2]


def test_trace_route_ipv6_uses_process_tracer(monkeypatch: pytest.MonkeyPatch) -> None:
    from pingui.models import HopNode, RouteSnapshot

    called: list[str] = []

    def fake_process(
        target_host: str,
        max_hops: int,
        timeout: float,
        *,
        runner=None,
    ) -> RouteSnapshot:
        _ = max_hops, timeout, runner
        called.append(target_host)
        return RouteSnapshot(
            target=target_host,
            target_ip="2001:db8::1",
            nodes=[HopNode(hop=1, ip="2001:db8::1", ping_ms=1.0, is_timeout=False)],
        )

    monkeypatch.setattr("pingui.icmp.tracer.trace_route_process", fake_process)
    snap = trace_route("2001:db8::1", max_hops=5, timeout=0.5)
    assert called == ["2001:db8::1"]
    assert snap.target_ip == "2001:db8::1"


def test_trace_route_timeout_hop() -> None:
    transport = MockTransport(
        {
            1: None,
            2: ProbeResult("10.0.0.1", 3.0, False),
            3: ProbeResult("1.1.1.1", 1.1, True),
        }
    )
    snap = trace_route("1.1.1.1", max_hops=5, transport=transport)
    assert snap.nodes[0].ip == TIMEOUT_IP
    assert snap.nodes[0].is_timeout is True
    assert snap.route_ips() == ["10.0.0.1", "1.1.1.1"]

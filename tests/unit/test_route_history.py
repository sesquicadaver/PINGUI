"""Route history unit tests."""

from __future__ import annotations

from pingui.models import HopNode
from pingui.monitor.route_history import record_last_known, route_with_last_known_ips


def test_record_last_known_skips_timeouts() -> None:
    known: dict[int, HopNode] = {}
    record_last_known(
        known,
        [HopNode(1, "1.1.1.1", 10.0), HopNode.timeout(2)],
    )
    assert known[1].ip == "1.1.1.1"
    assert 2 not in known


def test_inactive_route_uses_last_known_ip_for_timeout_hop() -> None:
    known = {2: HopNode(2, "2.2.2.2", 20.0)}
    route = [HopNode(1, "1.1.1.1", 10.0), HopNode.timeout(2)]
    enriched = route_with_last_known_ips(route, known)
    assert enriched[1].ip == "2.2.2.2"
    assert not enriched[1].is_timeout

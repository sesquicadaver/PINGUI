"""Last-known hop IP tracking for inactive route display."""

from __future__ import annotations

from pingui.models import TIMEOUT_IP, HopNode


def record_last_known(
    last_known: dict[int, HopNode],
    nodes: list[HopNode],
) -> None:
    """Remember the latest reachable IP observed at each hop index."""
    for node in nodes:
        if node.is_timeout or node.ip == TIMEOUT_IP:
            continue
        last_known[node.hop] = node


def route_with_last_known_ips(
    route: list[HopNode],
    last_known: dict[int, HopNode],
) -> list[HopNode]:
    """Replace timeout hops with last known IPs for inactive chain display."""
    result: list[HopNode] = []
    for node in route:
        if not node.is_timeout and node.ip != TIMEOUT_IP:
            result.append(node)
            continue
        known = last_known.get(node.hop)
        if known is None:
            result.append(node)
            continue
        result.append(
            HopNode(
                hop=node.hop,
                ip=known.ip,
                ping_ms=known.ping_ms,
                is_timeout=False,
            )
        )
    return result

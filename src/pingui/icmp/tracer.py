"""Route tracing via incremental TTL probes."""

from __future__ import annotations

import logging

from pingui.icmp.process_tracer import requires_process_trace, trace_route_process
from pingui.icmp.raw_socket import ProbeTransport, resolve_target, send_probe
from pingui.models import HopNode, RouteSnapshot

logger = logging.getLogger(__name__)

DEFAULT_MAX_HOPS = 20
DEFAULT_TIMEOUT = 0.5


def trace_route(
    target_host: str,
    max_hops: int = DEFAULT_MAX_HOPS,
    timeout: float = DEFAULT_TIMEOUT,
    transport: ProbeTransport | None = None,
) -> RouteSnapshot:
    """
    Trace route to target using ICMP probes with TTL 1..max_hops.

    IPv6 literals use subprocess ``traceroute -6`` (raw ICMP is v4-only).
    Timeout hops are recorded with ip='*' and ping_ms=None.
    Stops when target IP responds or max_hops is reached.
    """
    if requires_process_trace(target_host):
        return trace_route_process(target_host, max_hops, timeout)

    target_ip = resolve_target(target_host)
    nodes: list[HopNode] = []

    for ttl in range(1, max_hops + 1):
        result = send_probe(target_ip, ttl, timeout, transport=transport)
        if result is None:
            nodes.append(HopNode.timeout(ttl))
            continue

        nodes.append(
            HopNode(
                hop=ttl,
                ip=result.source_ip,
                ping_ms=result.rtt_ms,
                is_timeout=False,
            )
        )
        if result.is_target:
            break

    return RouteSnapshot(target=target_host, target_ip=target_ip, nodes=nodes)

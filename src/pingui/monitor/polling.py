"""Pure polling logic for route monitoring (testable without Qt)."""

from __future__ import annotations

import logging
from dataclasses import dataclass

from pingui.icmp.raw_socket import ProbeTransport
from pingui.icmp.tracer import trace_route
from pingui.models import RouteSnapshot
from pingui.monitor.route_change import detect_route_change

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class HostPollOutcome:
    """Result of polling one host during a monitoring cycle."""

    snapshot: RouteSnapshot | None
    error: str | None
    route_changed: bool
    old_ips: list[str]
    new_ips: list[str]
    current_ips: list[str]


def poll_host_route(
    host: str,
    previous_ips: list[str],
    *,
    max_hops: int = 20,
    timeout: float = 0.5,
    transport: ProbeTransport | None = None,
) -> HostPollOutcome:
    """
    Trace route for host and detect path changes relative to previous_ips.

    Returns outcome with error set when tracing fails.
    """
    try:
        snapshot = trace_route(
            host,
            max_hops=max_hops,
            timeout=timeout,
            transport=transport,
        )
    except OSError as exc:
        return HostPollOutcome(
            snapshot=None,
            error=str(exc),
            route_changed=False,
            old_ips=list(previous_ips),
            new_ips=[],
            current_ips=list(previous_ips),
        )
    except Exception as exc:
        logger.exception("Unexpected error tracing %s", host)
        return HostPollOutcome(
            snapshot=None,
            error=str(exc),
            route_changed=False,
            old_ips=list(previous_ips),
            new_ips=[],
            current_ips=list(previous_ips),
        )

    current_ips = snapshot.route_ips()
    changed, old_ips, new_ips = detect_route_change(previous_ips, current_ips)
    return HostPollOutcome(
        snapshot=snapshot,
        error=None,
        route_changed=changed,
        old_ips=old_ips,
        new_ips=new_ips,
        current_ips=current_ips,
    )

"""ICMP raw socket layer using scapy.

ADR: scapy chosen over stdlib raw sockets for reliable TTL-based traceroute
and cross-hop reply parsing on Linux without manual IP/ICMP header assembly.
Requires CAP_NET_RAW or root on Linux.
"""

from __future__ import annotations

import logging
import socket
import time
from dataclasses import dataclass
from typing import Protocol

from scapy.all import ICMP, IP, sr1

from pingui.config import ConfigError, normalize_host_entry, resolve_trace_target

logger = logging.getLogger(__name__)


class RawIcmpPermissionError(PermissionError):
    """Raised when the process lacks permission for raw ICMP."""


@dataclass(frozen=True, slots=True)
class ProbeResult:
    """Result of a single TTL-limited ICMP probe."""

    source_ip: str
    rtt_ms: float
    is_target: bool


class ProbeTransport(Protocol):
    """Protocol for injectable probe transport (testing)."""

    def send_probe(
        self,
        target_ip: str,
        ttl: int,
        timeout: float,
    ) -> ProbeResult | None:
        """Send probe and return result or None on timeout."""
        ...


def check_raw_icmp_permission() -> None:
    """Verify raw ICMP socket can be opened (Linux cap_net_raw or root)."""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_ICMP)
        sock.close()
    except PermissionError as exc:
        msg = (
            "Raw ICMP requires root or cap_net_raw. "
            "Run: ./pingui.sh --deploy"
        )
        raise RawIcmpPermissionError(msg) from exc


def resolve_target(host: str) -> str:
    """Resolve target for raw ICMP trace (IPv4 dotted quad or hostname→A)."""
    normalized = normalize_host_entry(host)
    if ":" in normalized:
        msg = f"IPv6 literal requires process traceroute, not raw ICMP: {host!r}"
        raise ConfigError(msg)
    return resolve_trace_target(host)


class ScapyProbeTransport:
    """Production ICMP probe transport via scapy."""

    def send_probe(
        self,
        target_ip: str,
        ttl: int,
        timeout: float,
    ) -> ProbeResult | None:
        packet = IP(dst=target_ip, ttl=ttl) / ICMP()
        start = time.perf_counter()
        reply = sr1(packet, verbose=0, timeout=timeout)
        if reply is None:
            return None

        rtt_ms = (time.perf_counter() - start) * 1000.0
        if not reply.haslayer(IP):
            return None

        source_ip = reply[IP].src
        is_target = source_ip == target_ip
        return ProbeResult(source_ip=source_ip, rtt_ms=rtt_ms, is_target=is_target)


_default_transport = ScapyProbeTransport()


def send_probe(
    target_ip: str,
    ttl: int,
    timeout: float,
    transport: ProbeTransport | None = None,
) -> ProbeResult | None:
    """Send one ICMP probe with given TTL and measure RTT."""
    tr = transport if transport is not None else _default_transport
    return tr.send_probe(target_ip, ttl, timeout)

"""Subprocess traceroute for IPv6 literals (dual-stack parity with Java)."""

from __future__ import annotations

import logging
import re
import shutil
import subprocess
from typing import Protocol

from pingui.config import is_ipv6_literal, normalize_host_entry
from pingui.models import HopNode, RouteSnapshot

logger = logging.getLogger(__name__)

_HOP_LINE = re.compile(r"^\s*(\d+)\s+(\S+)(?:\s+(\d+(?:\.\d+)?)\s*ms)?")


class ProcessTraceRunner(Protocol):
    """Injectable subprocess runner for tests."""

    def __call__(self, command: list[str], *, timeout: float) -> list[str]:
        """Execute ``command`` and return stdout lines."""
        ...


def _default_runner(command: list[str], *, timeout: float) -> list[str]:
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
            encoding="utf-8",
            errors="replace",
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        msg = f"traceroute failed: {exc}"
        raise OSError(msg) from exc
    if completed.returncode not in {0, 1}:
        msg = f"traceroute exited with code {completed.returncode}"
        raise OSError(msg)
    output = completed.stdout.splitlines()
    if completed.stderr:
        output.extend(line for line in completed.stderr.splitlines() if line.strip())
    return output


def _resolve_traceroute_executable() -> str:
    for candidate in ("/usr/sbin/traceroute", "traceroute"):
        if candidate.startswith("/") and shutil.which(candidate) is None:
            continue
        if shutil.which(candidate) or candidate.startswith("/"):
            return candidate
    return "traceroute"


def build_traceroute_command(
    target_host: str,
    *,
    max_hops: int,
    timeout: float,
) -> list[str]:
    """Build Linux traceroute argv (adds ``-6`` for IPv6 literals)."""
    normalized = normalize_host_entry(target_host)
    wait_sec = max(1, int(timeout + 0.999))
    command = [
        _resolve_traceroute_executable(),
        "-n",
        "-w",
        str(wait_sec),
        "-m",
        str(max_hops),
        "-q",
        "1",
    ]
    if is_ipv6_literal(normalized):
        command.append("-6")
    command.append(normalized)
    return command


def parse_traceroute_output(lines: list[str]) -> list[HopNode]:
    """Parse BSD/GNU/Linux traceroute stdout into hop nodes."""
    nodes: list[HopNode] = []
    for line in lines:
        match = _HOP_LINE.match(line)
        if match is None:
            continue
        hop = int(match.group(1))
        token = match.group(2)
        if token.startswith("[") and token.endswith("]"):
            token = token[1:-1]
        if token == "*":
            nodes.append(HopNode.timeout(hop))
            continue
        ping_ms = float(match.group(3)) if match.group(3) is not None else None
        nodes.append(HopNode(hop=hop, ip=token, ping_ms=ping_ms, is_timeout=False))
    return nodes


def trace_route_process(
    target_host: str,
    max_hops: int,
    timeout: float,
    *,
    runner: ProcessTraceRunner | None = None,
) -> RouteSnapshot:
    """Trace route via OS ``traceroute`` (IPv6 literals use ``-6``)."""
    normalized = normalize_host_entry(target_host)
    command = build_traceroute_command(
        normalized,
        max_hops=max_hops,
        timeout=timeout,
    )
    run = runner or _default_runner
    process_timeout = max(30.0, max_hops * timeout * 4)
    lines = run(command, timeout=process_timeout)
    nodes = parse_traceroute_output(lines)
    if not nodes:
        msg = f"No hops parsed for {target_host!r}"
        raise OSError(msg)
    target_ip = next(
        (node.ip for node in reversed(nodes) if not node.is_timeout),
        normalized,
    )
    return RouteSnapshot(target=target_host, target_ip=target_ip, nodes=nodes)


def requires_process_trace(target_host: str) -> bool:
    """IPv6 literals must use subprocess trace (raw ICMP is v4-only)."""
    return is_ipv6_literal(target_host)

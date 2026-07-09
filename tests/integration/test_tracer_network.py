"""Optional live network tracer test."""

from __future__ import annotations

import shutil

import pytest

from pingui.icmp.raw_socket import RawIcmpPermissionError, check_raw_icmp_permission
from pingui.icmp.tracer import trace_route


@pytest.mark.network
def test_trace_localhost() -> None:
    try:
        check_raw_icmp_permission()
    except RawIcmpPermissionError:
        pytest.skip("no raw ICMP permission")
    snap = trace_route("127.0.0.1", max_hops=5, timeout=1.0)
    assert len(snap.nodes) >= 1


@pytest.mark.network
def test_trace_ipv6_localhost() -> None:
    if shutil.which("traceroute") is None:
        pytest.skip("traceroute not installed")
    snap = trace_route("::1", max_hops=5, timeout=1.0)
    assert len(snap.nodes) >= 1
    assert snap.target_ip != "*"

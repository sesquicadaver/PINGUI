"""Unit tests for subprocess traceroute (IPv6 dual-stack)."""

from __future__ import annotations

from pathlib import Path

import pytest

from pingui.icmp.process_tracer import (
    build_traceroute_command,
    parse_traceroute_output,
    requires_process_trace,
    trace_route_process,
)

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures" / "trace"


def test_requires_process_trace_ipv6_only() -> None:
    assert requires_process_trace("2001:db8::1") is True
    assert requires_process_trace("[::1]") is True
    assert requires_process_trace("8.8.8.8") is False
    assert requires_process_trace("example.com") is False


def test_build_traceroute_command_ipv6_adds_dash_six() -> None:
    command = build_traceroute_command("2001:db8::1", max_hops=20, timeout=0.5)
    assert "-6" in command
    assert command[-1] == "2001:db8::1"


def test_build_traceroute_command_ipv4_omits_dash_six() -> None:
    command = build_traceroute_command("8.8.8.8", max_hops=20, timeout=0.5)
    assert "-6" not in command
    assert command[-1] == "8.8.8.8"


def test_parse_unix_v6_fixture() -> None:
    lines = (FIXTURES / "unix_v6_ok.txt").read_text(encoding="utf-8").splitlines()
    nodes = parse_traceroute_output(lines)
    assert len(nodes) == 2
    assert nodes[0].hop == 1
    assert nodes[0].ip == "2001:db8:1::1"
    assert nodes[0].ping_ms == pytest.approx(0.123)
    assert nodes[1].hop == 2
    assert nodes[1].ip == "2001:4860:4860::8888"
    assert nodes[1].ping_ms == pytest.approx(10.5)


def test_parse_timeout_hop() -> None:
    nodes = parse_traceroute_output([" 3  * * *"])
    assert len(nodes) == 1
    assert nodes[0].hop == 3
    assert nodes[0].is_timeout is True
    assert nodes[0].ip == "*"


def test_trace_route_process_mock_runner() -> None:
    fixture_lines = (FIXTURES / "unix_v6_ok.txt").read_text(encoding="utf-8").splitlines()

    def runner(command: list[str], *, timeout: float) -> list[str]:
        _ = command, timeout
        return fixture_lines

    snap = trace_route_process("2001:db8::1", max_hops=30, timeout=0.5, runner=runner)
    assert snap.target == "2001:db8::1"
    assert snap.target_ip == "2001:4860:4860::8888"
    assert len(snap.nodes) == 2


def test_trace_route_process_empty_output_raises() -> None:
    def runner(command: list[str], *, timeout: float) -> list[str]:
        _ = command, timeout
        return []

    with pytest.raises(OSError, match="No hops parsed"):
        trace_route_process("2001:db8::1", max_hops=5, timeout=0.5, runner=runner)

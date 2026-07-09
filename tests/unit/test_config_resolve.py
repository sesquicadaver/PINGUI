"""Config resolution tests."""

from __future__ import annotations

import pytest

from pingui.config import ConfigError, resolve_host_ipv4, resolve_trace_target


def test_resolve_ipv4_literal() -> None:
    assert resolve_host_ipv4("8.8.8.8") == "8.8.8.8"


def test_resolve_localhost() -> None:
    assert resolve_host_ipv4("localhost") == "127.0.0.1"


def test_resolve_invalid_host() -> None:
    with pytest.raises(ConfigError, match="Cannot resolve"):
        resolve_host_ipv4("this-host-should-not-exist.invalid.")


def test_resolve_trace_target_ipv6_literal() -> None:
    assert resolve_trace_target("2001:0db8::1") == "2001:db8::1"


def test_resolve_host_ipv4_rejects_ipv6() -> None:
    with pytest.raises(ConfigError, match="IPv6 literal cannot be resolved"):
        resolve_host_ipv4("2001:db8::1")

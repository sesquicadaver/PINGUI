"""Alert rate limiter tests (PY-044)."""

from __future__ import annotations

import pytest

from pingui.monitor.alert_rate_limiter import AlertRateLimiter


def test_rate_limiter_allows_burst_then_blocks() -> None:
    limiter = AlertRateLimiter(max_per_hour=3)
    base = 1_000_000.0
    assert limiter.allow("8.8.8.8", now=base) is True
    assert limiter.allow("8.8.8.8", now=base + 1) is True
    assert limiter.allow("8.8.8.8", now=base + 2) is True
    assert limiter.allow("8.8.8.8", now=base + 3) is False


def test_rate_limiter_is_per_host() -> None:
    limiter = AlertRateLimiter(max_per_hour=1)
    now = 2_000_000.0
    assert limiter.allow("a", now=now) is True
    assert limiter.allow("a", now=now + 1) is False
    assert limiter.allow("b", now=now + 1) is True


def test_rate_limiter_rejects_invalid_max() -> None:
    with pytest.raises(ValueError, match="max_per_hour"):
        AlertRateLimiter(max_per_hour=0)

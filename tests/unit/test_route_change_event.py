"""RouteChangeEvent model tests (PY-040)."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from pingui.models import ROUTE_CHANGE_EVENT_TYPE, RouteChangeEvent


def test_route_change_event_roundtrip_json() -> None:
    ts = datetime(2026, 7, 9, 6, 50, 0, tzinfo=UTC)
    event = RouteChangeEvent(
        host="8.8.8.8",
        old_ips=["10.0.0.1", "8.8.8.8"],
        new_ips=["10.0.0.2", "8.8.8.8"],
        timestamp=ts,
        profile="noc",
    )
    restored = RouteChangeEvent.from_json(event.to_json())
    assert restored == event


def test_route_change_event_from_route_change_factory() -> None:
    event = RouteChangeEvent.from_route_change(
        "google.com",
        ["1.1.1.1"],
        ["2.2.2.2"],
        profile="default",
        timestamp=datetime(2026, 1, 1, tzinfo=UTC),
    )
    payload = event.to_dict()
    assert payload["event"] == ROUTE_CHANGE_EVENT_TYPE
    assert payload["host"] == "google.com"
    assert payload["old_ips"] == ["1.1.1.1"]
    assert payload["new_ips"] == ["2.2.2.2"]
    assert payload["profile"] == "default"
    assert payload["timestamp"] == "2026-01-01T00:00:00Z"


@pytest.mark.parametrize(
    ("payload", "match"),
    [
        ({"event": "other", "host": "x", "old_ips": [], "new_ips": [], "timestamp": "t"}, "event"),
        ({"host": "", "old_ips": [], "new_ips": [], "timestamp": "2026-01-01T00:00:00Z"}, "host"),
        (
            {"host": "x", "old_ips": [1], "new_ips": [], "timestamp": "2026-01-01T00:00:00Z"},
            "old_ips",
        ),
    ],
)
def test_route_change_event_from_dict_validation(payload: dict, match: str) -> None:
    with pytest.raises(ValueError, match=match):
        RouteChangeEvent.from_dict(payload)

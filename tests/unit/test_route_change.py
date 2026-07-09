"""Route change detection unit tests."""

from __future__ import annotations

from pingui.monitor.route_change import detect_route_change


def test_no_change_on_first_observation() -> None:
    changed, old, new = detect_route_change([], ["1.1.1.1", "8.8.8.8"])
    assert changed is False
    assert old == []
    assert new == ["1.1.1.1", "8.8.8.8"]


def test_no_change_when_equal() -> None:
    route = ["10.0.0.1", "10.0.0.2"]
    changed, _, _ = detect_route_change(route, list(route))
    assert changed is False


def test_detect_change() -> None:
    old = ["10.0.0.1", "10.0.0.2"]
    new = ["10.0.0.1", "10.0.0.9"]
    changed, prev, curr = detect_route_change(old, new)
    assert changed is True
    assert prev == old
    assert curr == new

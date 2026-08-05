"""Desktop notifier tests (PY-043) — in-app/callback popup, no notify-send."""

from __future__ import annotations

from datetime import UTC, datetime
from unittest.mock import MagicMock

from pingui.models import RouteChangeEvent
from pingui.monitor.desktop_notifier import DesktopAlertDispatcher, notify_route_change


def test_notify_route_change_uses_injected_popup() -> None:
    event = RouteChangeEvent(
        host="8.8.8.8",
        old_ips=["1.1.1.1"],
        new_ips=["2.2.2.2"],
        timestamp=datetime(2026, 7, 9, tzinfo=UTC),
    )
    show = MagicMock()
    assert notify_route_change(event, show) is True
    show.assert_called_once()
    title, body = show.call_args.args
    assert title == "PINGUI route change"
    assert "8.8.8.8" in body
    assert "1.1.1.1" in body
    assert "2.2.2.2" in body


def test_notify_route_change_without_popup_logs() -> None:
    event = RouteChangeEvent.from_route_change("x", [], ["1.1.1.1"])
    assert notify_route_change(event) is True


def test_dispatcher_forwards_to_popup() -> None:
    event = RouteChangeEvent.from_route_change("8.8.8.8", ["a"], ["b"])
    show = MagicMock()
    DesktopAlertDispatcher(show).dispatch(event)
    show.assert_called_once()

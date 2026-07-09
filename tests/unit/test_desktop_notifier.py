"""Desktop notifier tests (PY-043)."""

from __future__ import annotations

from datetime import UTC, datetime
from unittest.mock import patch

from pingui.models import RouteChangeEvent
from pingui.monitor.desktop_notifier import notify_route_change


def test_notify_route_change_linux_invokes_notify_send() -> None:
    event = RouteChangeEvent(
        host="8.8.8.8",
        old_ips=["1.1.1.1"],
        new_ips=["2.2.2.2"],
        timestamp=datetime(2026, 7, 9, tzinfo=UTC),
    )
    with (
        patch("pingui.monitor.desktop_notifier.sys.platform", "linux"),
        patch("pingui.monitor.desktop_notifier.shutil.which", return_value="/usr/bin/notify-send"),
        patch("pingui.monitor.desktop_notifier.subprocess.run") as run,
    ):
        assert notify_route_change(event) is True
    run.assert_called_once()
    args = run.call_args.args[0]
    assert args[0] == "notify-send"
    assert "8.8.8.8" in args[2]


def test_notify_route_change_skips_when_unavailable() -> None:
    event = RouteChangeEvent.from_route_change("x", [], ["1.1.1.1"])
    with (
        patch("pingui.monitor.desktop_notifier.sys.platform", "darwin"),
        patch("pingui.monitor.desktop_notifier.subprocess.run") as run,
    ):
        assert notify_route_change(event) is False
    run.assert_not_called()

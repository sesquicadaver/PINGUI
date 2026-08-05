"""In-app / callback desktop alerts for route changes (no notify-send / D-Bus)."""

from __future__ import annotations

import logging
from collections.abc import Callable

from pingui.models import RouteChangeEvent

logger = logging.getLogger(__name__)

PopupFn = Callable[[str, str], None]


def notify_route_change(event: RouteChangeEvent, show: PopupFn | None = None) -> bool:
    """
    Present a desktop alert via ``show(title, body)``.

    Without an injected popup (GUI), logs at INFO — never calls ``notify-send`` or D-Bus.
    Returns True when a popup/log was attempted.
    """
    old_str = " -> ".join(event.old_ips) if event.old_ips else "(none)"
    new_str = " -> ".join(event.new_ips) if event.new_ips else "(none)"
    body = f"{event.host}: {old_str} → {new_str}"
    title = "PINGUI route change"
    try:
        if show is not None:
            show(title, body)
        else:
            logger.info("Desktop alert: %s — %s", title, body)
    except Exception as exc:  # noqa: BLE001 — channel must not crash monitor
        logger.warning("Desktop alert popup failed: %s", exc)
        return False
    return True


class DesktopAlertDispatcher:
    """Dispatch route-change alerts to an injected popup (or INFO log)."""

    def __init__(self, show: PopupFn | None = None) -> None:
        self._show = show

    def dispatch(self, event: RouteChangeEvent) -> None:
        notify_route_change(event, self._show)

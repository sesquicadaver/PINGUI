"""Desktop notifications for route changes (Linux notify-send)."""

from __future__ import annotations

import logging
import shutil
import subprocess
import sys

from pingui.models import RouteChangeEvent

logger = logging.getLogger(__name__)


def notify_route_change(event: RouteChangeEvent) -> bool:
    """
    Send a desktop notification via ``notify-send``.

    Returns True when a notification was attempted, False when unsupported.
    """
    if sys.platform != "linux":
        return False
    if shutil.which("notify-send") is None:
        logger.debug("notify-send not found; skipping desktop alert")
        return False

    old_str = " -> ".join(event.old_ips) if event.old_ips else "(none)"
    new_str = " -> ".join(event.new_ips) if event.new_ips else "(none)"
    body = f"{event.host}: {old_str} → {new_str}"
    try:
        subprocess.run(
            ["notify-send", "PINGUI route change", body],
            check=False,
            timeout=5,
            capture_output=True,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        logger.warning("Desktop notification failed: %s", exc)
        return False
    return True


class DesktopAlertDispatcher:
    """Dispatch route-change alerts to the desktop notifier."""

    def dispatch(self, event: RouteChangeEvent) -> None:
        notify_route_change(event)

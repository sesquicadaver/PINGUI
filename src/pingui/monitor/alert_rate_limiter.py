"""Per-host alert rate limiting."""

from __future__ import annotations

import time


class AlertRateLimiter:
    """Allow at most ``max_per_hour`` alerts per host in a rolling hour window."""

    def __init__(self, max_per_hour: int = 10) -> None:
        if max_per_hour < 1:
            msg = "max_per_hour must be >= 1"
            raise ValueError(msg)
        self._max_per_hour = max_per_hour
        self._history: dict[str, list[float]] = {}

    def allow(self, host: str, *, now: float | None = None) -> bool:
        """Return True when an alert for ``host`` may be sent."""
        ts = time.time() if now is None else now
        window_start = ts - 3600.0
        entries = [t for t in self._history.get(host, ()) if t > window_start]
        if len(entries) >= self._max_per_hour:
            self._history[host] = entries
            return False
        entries.append(ts)
        self._history[host] = entries
        return True

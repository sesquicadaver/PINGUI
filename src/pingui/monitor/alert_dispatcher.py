"""Alert dispatch pipeline for route-change events."""

from __future__ import annotations

import json
import logging
import urllib.error
import urllib.request
from typing import Protocol
from urllib.parse import urlparse, urlunparse

from pingui.models import RouteChangeEvent
from pingui.monitor.alert_rate_limiter import AlertRateLimiter
from pingui.monitor.desktop_notifier import DesktopAlertDispatcher

logger = logging.getLogger(__name__)


class AlertDispatcher(Protocol):
    """Send a route-change alert to one or more channels."""

    def dispatch(self, event: RouteChangeEvent) -> None: ...


class NoOpAlertDispatcher:
    """No-op dispatcher used when alerts are disabled."""

    def dispatch(self, event: RouteChangeEvent) -> None:
        return


def redact_webhook_url(url: str) -> str:
    """Return a log-safe webhook URL without credentials or query secrets."""
    parsed = urlparse(url)
    host = parsed.hostname or ""
    if parsed.port is not None:
        host = f"{host}:{parsed.port}"
    return urlunparse((parsed.scheme, host, parsed.path or "", "", "", ""))


class WebhookAlertDispatcher:
    """POST JSON ``RouteChangeEvent`` payload to a webhook URL."""

    def __init__(self, url: str, *, timeout: float = 5.0) -> None:
        self._url = url
        self._timeout = timeout

    def dispatch(self, event: RouteChangeEvent) -> None:
        payload = event.to_json().encode("utf-8")
        request = urllib.request.Request(
            self._url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                response.read()
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            logger.warning(
                "Webhook alert failed for %s (%s): %s",
                event.host,
                redact_webhook_url(self._url),
                exc,
            )


class CompositeAlertDispatcher:
    """Fan-out to multiple dispatchers; failures are logged, not raised."""

    def __init__(self, dispatchers: list[AlertDispatcher]) -> None:
        self._dispatchers = dispatchers

    def dispatch(self, event: RouteChangeEvent) -> None:
        for dispatcher in self._dispatchers:
            try:
                dispatcher.dispatch(event)
            except Exception:
                logger.exception("Alert dispatcher failed for %s", event.host)


class RateLimitedAlertDispatcher:
    """Apply per-host rate limiting before delegating to an inner dispatcher."""

    def __init__(self, inner: AlertDispatcher, limiter: AlertRateLimiter) -> None:
        self._inner = inner
        self._limiter = limiter

    def dispatch(self, event: RouteChangeEvent) -> None:
        if not self._limiter.allow(event.host):
            logger.debug("Alert rate-limited for host %s", event.host)
            return
        self._inner.dispatch(event)


def build_alert_dispatcher(
    *,
    webhook_url: str | None = None,
    desktop_alerts: bool = False,
    max_alerts_per_hour: int = 10,
) -> AlertDispatcher | None:
    """Build a rate-limited alert pipeline from CLI/profile options."""
    channels: list[AlertDispatcher] = []
    if webhook_url:
        channels.append(WebhookAlertDispatcher(webhook_url))
        logger.info("Webhook alerts enabled (%s)", redact_webhook_url(webhook_url))
    if desktop_alerts:
        channels.append(DesktopAlertDispatcher())
        logger.info("Desktop alerts enabled")

    if not channels:
        return None

    inner: AlertDispatcher = (
        channels[0] if len(channels) == 1 else CompositeAlertDispatcher(channels)
    )

    return RateLimitedAlertDispatcher(inner, AlertRateLimiter(max_alerts_per_hour))


def parse_route_change_event_json(raw: str) -> RouteChangeEvent:
    """Parse webhook/contract JSON into a :class:`RouteChangeEvent`."""
    data = json.loads(raw)
    if not isinstance(data, dict):
        msg = "Route change payload must be a JSON object"
        raise TypeError(msg)
    return RouteChangeEvent.from_dict(data)

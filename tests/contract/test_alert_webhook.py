"""Webhook alert dispatcher contract tests (PY-041)."""

from __future__ import annotations

import json
import threading
from datetime import UTC, datetime
from http.server import BaseHTTPRequestHandler, HTTPServer

from pingui.models import RouteChangeEvent
from pingui.monitor.alert_dispatcher import (
    RateLimitedAlertDispatcher,
    WebhookAlertDispatcher,
    build_alert_dispatcher,
    redact_webhook_url,
)


class _CaptureHandler(BaseHTTPRequestHandler):
    bodies: list[bytes] = []

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        _CaptureHandler.bodies.append(body)
        self.send_response(204)
        self.end_headers()

    def log_message(self, _format: str, *_args: object) -> None:
        return


def test_webhook_posts_route_change_json() -> None:
    _CaptureHandler.bodies = []
    server = HTTPServer(("127.0.0.1", 0), _CaptureHandler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        event = RouteChangeEvent(
            host="8.8.8.8",
            old_ips=["10.0.0.1"],
            new_ips=["10.0.0.2"],
            timestamp=datetime(2026, 7, 9, tzinfo=UTC),
            profile="noc",
        )
        dispatcher = WebhookAlertDispatcher(f"http://127.0.0.1:{port}/hook")
        dispatcher.dispatch(event)
        assert len(_CaptureHandler.bodies) == 1
        payload = json.loads(_CaptureHandler.bodies[0].decode("utf-8"))
        assert payload["event"] == "route_change"
        assert payload["host"] == "8.8.8.8"
        assert payload["profile"] == "noc"
    finally:
        server.shutdown()
        thread.join(timeout=2)


def test_webhook_network_error_does_not_raise() -> None:
    event = RouteChangeEvent.from_route_change("x", ["1"], ["2"])
    dispatcher = WebhookAlertDispatcher("http://127.0.0.1:1/unreachable")
    dispatcher.dispatch(event)


def test_redact_webhook_url_strips_credentials_and_query() -> None:
    url = "https://user:secret@hooks.example.com/path?token=abc"
    assert redact_webhook_url(url) == "https://hooks.example.com/path"


def test_build_alert_dispatcher_rate_limits() -> None:
    sent: list[str] = []

    class _Spy:
        def dispatch(self, event: RouteChangeEvent) -> None:
            sent.append(event.host)

    from pingui.monitor.alert_rate_limiter import AlertRateLimiter

    dispatcher = RateLimitedAlertDispatcher(_Spy(), AlertRateLimiter(1))
    event = RouteChangeEvent.from_route_change("host", ["a"], ["b"])
    dispatcher.dispatch(event)
    dispatcher.dispatch(event)
    assert sent == ["host"]


def test_build_alert_dispatcher_none_when_disabled() -> None:
    assert build_alert_dispatcher() is None

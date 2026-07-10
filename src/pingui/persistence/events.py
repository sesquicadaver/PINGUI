"""Write discrete persistence events to SQLite (PY-P11)."""

from __future__ import annotations

import json
from datetime import UTC, datetime

from pingui.models import HostSessionData, RouteChangeEvent
from pingui.persistence.policy import (
    EVENT_PROBE_ERROR,
    EVENT_ROUTE_CHANGE,
    PersistencePolicy,
)
from pingui.persistence.session_db import SessionDatabase


class PersistenceEventWriter:
    """Append ``route_change`` / ``probe_error`` rows when policy allows."""

    def __init__(
        self,
        database: SessionDatabase,
        policy: PersistencePolicy | None = None,
    ) -> None:
        self._db = database
        self._policy = policy if policy is not None else PersistencePolicy()

    @property
    def policy(self) -> PersistencePolicy:
        return self._policy

    def write_route_change(self, event: RouteChangeEvent) -> None:
        if not self._policy.allows(EVENT_ROUTE_CHANGE):
            return
        self._ensure_host(event.host)
        self._db.insert_event(
            EVENT_ROUTE_CHANGE,
            event.host,
            event.profile,
            event.to_json(),
            event.timestamp,
        )

    def write_probe_error(self, host: str, message: str) -> None:
        if not host or not self._policy.allows(EVENT_PROBE_ERROR):
            return
        self._ensure_host(host)
        payload = json.dumps(
            {"message": message or "", "host": host},
            separators=(",", ":"),
        )
        self._db.insert_event(
            EVENT_PROBE_ERROR,
            host,
            None,
            payload,
            datetime.now(UTC),
        )

    def _ensure_host(self, host: str) -> None:
        if self._db.load(host) is None:
            self._db.save(host, HostSessionData())

"""SQLite persistence for host session metrics between GUI runs."""

from __future__ import annotations

import json
import sqlite3
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from pingui.models import HopNode, HostSessionData

SCHEMA_VERSION = 1


def _hop_to_json(node: HopNode) -> dict[str, Any]:
    return {
        "hop": node.hop,
        "ip": node.ip,
        "ping_ms": node.ping_ms,
        "is_timeout": node.is_timeout,
    }


def _hop_from_json(raw: dict[str, Any]) -> HopNode:
    if raw.get("is_timeout"):
        return HopNode.timeout(int(raw["hop"]))
    return HopNode(
        hop=int(raw["hop"]),
        ip=str(raw["ip"]),
        ping_ms=raw.get("ping_ms"),
        is_timeout=bool(raw.get("is_timeout", False)),
    )


def _route_from_json(payload: str) -> list[HopNode]:
    data = json.loads(payload)
    if not isinstance(data, list):
        msg = "Route JSON must be a list"
        raise ValueError(msg)
    return [_hop_from_json(item) for item in data]


def _route_to_json(route: list[HopNode]) -> str:
    return json.dumps([_hop_to_json(node) for node in route])


def _last_known_from_json(payload: str) -> dict[int, HopNode]:
    data = json.loads(payload)
    if not isinstance(data, dict):
        msg = "Last-known JSON must be an object"
        raise ValueError(msg)
    return {int(key): _hop_from_json(value) for key, value in data.items()}


def _last_known_to_json(mapping: dict[int, HopNode]) -> str:
    return json.dumps({str(hop): _hop_to_json(node) for hop, node in mapping.items()})


def _ping_history_from_json(payload: str) -> dict[str, list[float]]:
    data = json.loads(payload)
    if not isinstance(data, dict):
        msg = "Ping history JSON must be an object"
        raise ValueError(msg)
    return {str(ip): [float(v) for v in values] for ip, values in data.items()}


def _ping_history_to_json(history: dict[str, list[float]]) -> str:
    return json.dumps(history)


class SessionDatabase:
    """Persist ``HostSessionData`` per target host in SQLite."""

    def __init__(self, path: Path | str) -> None:
        self._path = Path(path)
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(self._path)
        self._conn.execute("PRAGMA foreign_keys = ON")
        self._init_schema()

    @property
    def path(self) -> Path:
        return self._path

    def _init_schema(self) -> None:
        self._conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS schema_meta (
                version INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS host_session (
                host TEXT PRIMARY KEY,
                enabled INTEGER NOT NULL,
                current_route_json TEXT NOT NULL,
                previous_route_json TEXT NOT NULL,
                last_known_json TEXT NOT NULL,
                ping_history_json TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            """
        )
        row = self._conn.execute("SELECT version FROM schema_meta LIMIT 1").fetchone()
        if row is None:
            self._conn.execute(
                "INSERT INTO schema_meta(version) VALUES (?)",
                (SCHEMA_VERSION,),
            )
        self._conn.commit()

    def load(self, host: str) -> HostSessionData | None:
        row = self._conn.execute(
            """
            SELECT enabled, current_route_json, previous_route_json,
                   last_known_json, ping_history_json
            FROM host_session WHERE host = ?
            """,
            (host,),
        ).fetchone()
        if row is None:
            return None
        enabled, current_json, previous_json, last_known_json, ping_json = row
        return HostSessionData(
            current_route=_route_from_json(current_json),
            previous_route=_route_from_json(previous_json),
            last_known_by_hop=_last_known_from_json(last_known_json),
            ping_history=_ping_history_from_json(ping_json),
            enabled=bool(enabled),
        )

    def save(self, host: str, data: HostSessionData) -> None:
        now = datetime.now(UTC).isoformat()
        self._conn.execute(
            """
            INSERT INTO host_session(
                host, enabled, current_route_json, previous_route_json,
                last_known_json, ping_history_json, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(host) DO UPDATE SET
                enabled = excluded.enabled,
                current_route_json = excluded.current_route_json,
                previous_route_json = excluded.previous_route_json,
                last_known_json = excluded.last_known_json,
                ping_history_json = excluded.ping_history_json,
                updated_at = excluded.updated_at
            """,
            (
                host,
                int(data.enabled),
                _route_to_json(data.current_route),
                _route_to_json(data.previous_route),
                _last_known_to_json(data.last_known_by_hop),
                _ping_history_to_json(data.ping_history),
                now,
            ),
        )
        self._conn.commit()

    def delete(self, host: str) -> None:
        self._conn.execute("DELETE FROM host_session WHERE host = ?", (host,))
        self._conn.commit()

    def rename(self, old_host: str, new_host: str) -> None:
        row = self._conn.execute(
            "SELECT enabled, current_route_json, previous_route_json, "
            "last_known_json, ping_history_json FROM host_session WHERE host = ?",
            (old_host,),
        ).fetchone()
        if row is None:
            return
        self.delete(old_host)
        data = HostSessionData(
            current_route=_route_from_json(row[1]),
            previous_route=_route_from_json(row[2]),
            last_known_by_hop=_last_known_from_json(row[3]),
            ping_history=_ping_history_from_json(row[4]),
            enabled=bool(row[0]),
        )
        self.save(new_host, data)

    def close(self) -> None:
        self._conn.close()

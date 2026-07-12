"""SQLite persistence for host session metrics between GUI runs."""

from __future__ import annotations

import json
import sqlite3
import threading
from contextlib import suppress
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from pingui.models import HopNode, HopProbeStats, HostSessionData

SCHEMA_VERSION = 4


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


def _hop_stats_to_json(stats: dict[int, HopProbeStats]) -> str:
    payload = {
        str(hop): {
            "probes": item.probes,
            "successes": item.successes,
            "rtt_samples": item.rtt_samples,
        }
        for hop, item in stats.items()
    }
    return json.dumps(payload)


def _hop_stats_from_json(payload: str) -> dict[int, HopProbeStats]:
    data = json.loads(payload)
    if not isinstance(data, dict):
        msg = "Hop stats JSON must be an object"
        raise ValueError(msg)
    result: dict[int, HopProbeStats] = {}
    for hop_key, raw in data.items():
        if not isinstance(raw, dict):
            msg = f"Invalid hop stats entry for hop {hop_key!r}"
            raise ValueError(msg)
        result[int(hop_key)] = HopProbeStats(
            probes=int(raw.get("probes", 0)),
            successes=int(raw.get("successes", 0)),
            rtt_samples=[float(v) for v in raw.get("rtt_samples", [])],
        )
    return result


class SessionDatabase:
    """Persist ``HostSessionData`` per target host in SQLite."""

    def __init__(self, path: Path | str) -> None:
        self._path = Path(path)
        self._path.parent.mkdir(parents=True, exist_ok=True)
        # MonitorLoop / Qt worker may call save/insert_event off the opener thread.
        self._conn = sqlite3.connect(self._path, check_same_thread=False)
        self._lock = threading.RLock()
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
                hop_stats_json TEXT NOT NULL DEFAULT '{}',
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS persistence_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_type TEXT NOT NULL,
                host TEXT NOT NULL,
                profile TEXT,
                payload_json TEXT NOT NULL,
                observed_at TEXT NOT NULL,
                FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_pe_host_type_time
                ON persistence_event(host, event_type, observed_at);
            CREATE TABLE IF NOT EXISTS telemetry_sample (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                value REAL NOT NULL,
                host TEXT NOT NULL,
                hop INTEGER,
                payload_json TEXT NOT NULL,
                observed_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_ts_host_time
                ON telemetry_sample(host, observed_at);
            CREATE TABLE IF NOT EXISTS telemetry_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event TEXT NOT NULL,
                host TEXT NOT NULL,
                message TEXT,
                payload_json TEXT NOT NULL,
                observed_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_te_host_time
                ON telemetry_event(host, observed_at);
            """
        )
        row = self._conn.execute("SELECT version FROM schema_meta LIMIT 1").fetchone()
        if row is None:
            self._conn.execute(
                "INSERT INTO schema_meta(version) VALUES (?)",
                (SCHEMA_VERSION,),
            )
            self._conn.commit()
            return
        self._migrate_schema(int(row[0]))
        self._conn.commit()

    def _migrate_schema(self, current_version: int) -> None:
        if current_version < 2:
            with suppress(sqlite3.OperationalError):
                self._conn.execute(
                    "ALTER TABLE host_session ADD COLUMN hop_stats_json TEXT NOT NULL DEFAULT '{}'"
                )
            self._conn.execute("UPDATE schema_meta SET version = ?", (2,))
            current_version = 2
        if current_version < 3:
            self._conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS persistence_event (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT NOT NULL,
                    host TEXT NOT NULL,
                    profile TEXT,
                    payload_json TEXT NOT NULL,
                    observed_at TEXT NOT NULL,
                    FOREIGN KEY (host) REFERENCES host_session(host) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_pe_host_type_time
                    ON persistence_event(host, event_type, observed_at);
                """
            )
            self._conn.execute("UPDATE schema_meta SET version = ?", (3,))
            current_version = 3
        if current_version < 4:
            self._conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS telemetry_sample (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    value REAL NOT NULL,
                    host TEXT NOT NULL,
                    hop INTEGER,
                    payload_json TEXT NOT NULL,
                    observed_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_ts_host_time
                    ON telemetry_sample(host, observed_at);
                CREATE TABLE IF NOT EXISTS telemetry_event (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event TEXT NOT NULL,
                    host TEXT NOT NULL,
                    message TEXT,
                    payload_json TEXT NOT NULL,
                    observed_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_te_host_time
                    ON telemetry_event(host, observed_at);
                """
            )
            self._conn.execute("UPDATE schema_meta SET version = ?", (4,))

    def insert_event(
        self,
        event_type: str,
        host: str,
        profile: str | None,
        payload_json: str,
        observed_at: datetime,
    ) -> int:
        """Append one ``persistence_event`` row; returns new id."""
        ts = observed_at.astimezone(UTC).isoformat().replace("+00:00", "Z")
        with self._lock:
            cursor = self._conn.execute(
                """
                INSERT INTO persistence_event(event_type, host, profile, payload_json, observed_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                (event_type, host, profile, payload_json, ts),
            )
            self._conn.commit()
            row_id = cursor.lastrowid
        if row_id is None:
            msg = "INSERT persistence_event did not return row id"
            raise RuntimeError(msg)
        return int(row_id)

    def count_events(self, event_type: str | None = None, host: str | None = None) -> int:
        clauses: list[str] = []
        params: list[str] = []
        if event_type is not None:
            clauses.append("event_type = ?")
            params.append(event_type)
        if host is not None:
            clauses.append("host = ?")
            params.append(host)
        where = f" WHERE {' AND '.join(clauses)}" if clauses else ""
        with self._lock:
            row = self._conn.execute(
                f"SELECT COUNT(*) FROM persistence_event{where}",
                params,
            ).fetchone()
        return int(row[0]) if row else 0

    def load(self, host: str) -> HostSessionData | None:
        with self._lock:
            row = self._conn.execute(
                """
                SELECT enabled, current_route_json, previous_route_json,
                       last_known_json, ping_history_json, hop_stats_json
                FROM host_session WHERE host = ?
                """,
                (host,),
            ).fetchone()
        if row is None:
            return None
        enabled, current_json, previous_json, last_known_json, ping_json, hop_stats_json = row
        hop_stats_payload = hop_stats_json if hop_stats_json is not None else "{}"
        return HostSessionData(
            current_route=_route_from_json(current_json),
            previous_route=_route_from_json(previous_json),
            last_known_by_hop=_last_known_from_json(last_known_json),
            ping_history=_ping_history_from_json(ping_json),
            hop_stats=_hop_stats_from_json(hop_stats_payload),
            enabled=bool(enabled),
        )

    def save(self, host: str, data: HostSessionData) -> None:
        now = datetime.now(UTC).isoformat()
        with self._lock:
            self._conn.execute(
                """
                INSERT INTO host_session(
                    host, enabled, current_route_json, previous_route_json,
                    last_known_json, ping_history_json, hop_stats_json, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(host) DO UPDATE SET
                    enabled = excluded.enabled,
                    current_route_json = excluded.current_route_json,
                    previous_route_json = excluded.previous_route_json,
                    last_known_json = excluded.last_known_json,
                    ping_history_json = excluded.ping_history_json,
                    hop_stats_json = excluded.hop_stats_json,
                    updated_at = excluded.updated_at
                """,
                (
                    host,
                    int(data.enabled),
                    _route_to_json(data.current_route),
                    _route_to_json(data.previous_route),
                    _last_known_to_json(data.last_known_by_hop),
                    _ping_history_to_json(data.ping_history),
                    _hop_stats_to_json(data.hop_stats),
                    now,
                ),
            )
            self._conn.commit()

    def delete(self, host: str) -> None:
        with self._lock:
            self._conn.execute("DELETE FROM host_session WHERE host = ?", (host,))
            self._conn.commit()

    def rename(self, old_host: str, new_host: str) -> None:
        with self._lock:
            row = self._conn.execute(
                "SELECT enabled, current_route_json, previous_route_json, "
                "last_known_json, ping_history_json, hop_stats_json "
                "FROM host_session WHERE host = ?",
                (old_host,),
            ).fetchone()
            if row is None:
                return
            if old_host == new_host:
                return
            hop_stats_payload = row[5] if row[5] is not None else "{}"
            data = HostSessionData(
                current_route=_route_from_json(row[1]),
                previous_route=_route_from_json(row[2]),
                last_known_by_hop=_last_known_from_json(row[3]),
                ping_history=_ping_history_from_json(row[4]),
                hop_stats=_hop_stats_from_json(hop_stats_payload),
                enabled=bool(row[0]),
            )
        self.save(new_host, data)
        with self._lock:
            self._conn.execute(
                "UPDATE persistence_event SET host = ? WHERE host = ?",
                (new_host, old_host),
            )
            self._conn.execute("DELETE FROM host_session WHERE host = ?", (old_host,))
            self._conn.commit()

    def close(self) -> None:
        with self._lock:
            self._conn.close()

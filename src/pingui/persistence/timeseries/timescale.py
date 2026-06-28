"""TimescaleDB / PostgreSQL time-series backend."""

from __future__ import annotations

import json
from typing import Any

from pingui.persistence.timeseries.base import PingSample, RouteEvent, TimeSeriesConfigError

SCHEMA_VERSION = 1


class TimescaleTimeSeriesBackend:
    """Write ping/route metrics to PostgreSQL (TimescaleDB-compatible schema)."""

    def __init__(self, dsn: str) -> None:
        try:
            import psycopg
        except ImportError as exc:
            msg = (
                "Timescale backend requires psycopg. "
                "Install with: pip install 'pingui[timeseries]'"
            )
            raise TimeSeriesConfigError(msg) from exc

        self._conn = psycopg.connect(dsn)
        self._conn.autocommit = True
        self._init_schema()

    def write_ping_samples(self, samples: list[PingSample]) -> None:
        if not samples:
            return
        with self._conn.cursor() as cur:
            cur.executemany(
                """
                INSERT INTO pingui_ping_samples
                    (time, target_host, hop, hop_ip, rtt_ms)
                VALUES (%s, %s, %s, %s, %s)
                """,
                [
                    (
                        sample.observed_at,
                        sample.target_host,
                        sample.hop,
                        sample.hop_ip,
                        sample.rtt_ms,
                    )
                    for sample in samples
                ],
            )

    def write_route_event(self, event: RouteEvent) -> None:
        with self._conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO pingui_route_events
                    (time, target_host, route_ips, route_changed)
                VALUES (%s, %s, %s::jsonb, %s)
                """,
                (
                    event.observed_at,
                    event.target_host,
                    json.dumps(event.route_ips),
                    event.route_changed,
                ),
            )

    def close(self) -> None:
        self._conn.close()

    def _init_schema(self) -> None:
        with self._conn.cursor() as cur:
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS pingui_schema_meta (
                    version INTEGER NOT NULL
                )
                """
            )
            row = cur.execute("SELECT version FROM pingui_schema_meta LIMIT 1").fetchone()
            if row is None:
                cur.execute(
                    "INSERT INTO pingui_schema_meta(version) VALUES (%s)",
                    (SCHEMA_VERSION,),
                )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS pingui_ping_samples (
                    time TIMESTAMPTZ NOT NULL,
                    target_host TEXT NOT NULL,
                    hop INTEGER NOT NULL,
                    hop_ip TEXT NOT NULL,
                    rtt_ms DOUBLE PRECISION NOT NULL
                )
                """
            )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS pingui_route_events (
                    time TIMESTAMPTZ NOT NULL,
                    target_host TEXT NOT NULL,
                    route_ips JSONB NOT NULL,
                    route_changed BOOLEAN NOT NULL
                )
                """
            )
            self._ensure_hypertables(cur)

    @staticmethod
    def _ensure_hypertables(cur: Any) -> None:
        """Create hypertables when TimescaleDB extension is available."""
        ext = cur.execute(
            "SELECT 1 FROM pg_extension WHERE extname = 'timescaledb'"
        ).fetchone()
        if ext is None:
            return
        for table in ("pingui_ping_samples", "pingui_route_events"):
            cur.execute(f"SELECT create_hypertable('{table}', 'time', if_not_exists => TRUE)")

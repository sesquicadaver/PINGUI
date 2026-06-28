"""Build and write session monitoring reports."""

from __future__ import annotations

import csv
import html
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from pingui.models import HopNode
from pingui.monitor.session_store import SessionStore

ROUTE_CSV_FIELDS = (
    "host",
    "enabled",
    "route_kind",
    "hop",
    "ip",
    "ping_ms",
    "avg_ping_ms",
    "is_timeout",
)


@dataclass(frozen=True, slots=True)
class RouteRow:
    """One hop row in an exported report."""

    host: str
    enabled: bool
    route_kind: str
    hop: int
    ip: str
    ping_ms: float | None
    avg_ping_ms: float | None
    is_timeout: bool


def build_route_rows(store: SessionStore) -> list[RouteRow]:
    """Collect current, previous/inactive, and ping averages for all hosts."""
    rows: list[RouteRow] = []
    for host in store.hosts():
        data = store.get(host)
        rows.extend(_rows_for_route(host, data.enabled, "current", data.current_route, store))
        inactive = store.inactive_route(host)
        if inactive:
            rows.extend(_rows_for_route(host, data.enabled, "inactive", inactive, store))
        elif data.previous_route:
            rows.extend(
                _rows_for_route(host, data.enabled, "previous", data.previous_route, store)
            )
    return rows


def _rows_for_route(
    host: str,
    enabled: bool,
    route_kind: str,
    route: list[HopNode],
    store: SessionStore,
) -> list[RouteRow]:
    result: list[RouteRow] = []
    for node in route:
        avg = None if node.is_timeout or node.ip == "*" else store.avg_ping(host, node.ip)
        result.append(
            RouteRow(
                host=host,
                enabled=enabled,
                route_kind=route_kind,
                hop=node.hop,
                ip=node.ip,
                ping_ms=node.ping_ms,
                avg_ping_ms=avg,
                is_timeout=node.is_timeout,
            )
        )
    return result


def export_session_csv(store: SessionStore, path: Path | str) -> None:
    """Write flat CSV report for all hosts in the session store."""
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    rows = build_route_rows(store)
    with target.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=ROUTE_CSV_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "host": row.host,
                    "enabled": int(row.enabled),
                    "route_kind": row.route_kind,
                    "hop": row.hop,
                    "ip": row.ip,
                    "ping_ms": "" if row.ping_ms is None else row.ping_ms,
                    "avg_ping_ms": "" if row.avg_ping_ms is None else row.avg_ping_ms,
                    "is_timeout": int(row.is_timeout),
                }
            )


def export_session_html(store: SessionStore, path: Path | str) -> None:
    """Write a standalone HTML summary for all session hosts."""
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    generated = datetime.now(UTC).isoformat()
    rows = build_route_rows(store)
    body = _render_html_body(store.hosts(), rows, generated)
    target.write_text(body, encoding="utf-8")


def _render_html_body(hosts: list[str], rows: Iterable[RouteRow], generated: str) -> str:
    sections: list[str] = [
        "<!DOCTYPE html>",
        "<html lang=\"uk\">",
        "<head>",
        "<meta charset=\"utf-8\">",
        "<title>PINGUI session report</title>",
        "<style>",
        "body{font-family:sans-serif;margin:2rem;}",
        "table{border-collapse:collapse;margin-bottom:1.5rem;width:100%;}",
        "th,td{border:1px solid #ccc;padding:0.4rem 0.6rem;text-align:left;}",
        "th{background:#f4f4f4;}",
        "</style>",
        "</head>",
        "<body>",
        f"<h1>PINGUI session report</h1><p>Generated: {html.escape(generated)}</p>",
    ]
    rows_by_host: dict[str, list[RouteRow]] = {host: [] for host in hosts}
    for row in rows:
        rows_by_host.setdefault(row.host, []).append(row)

    for host in hosts:
        host_rows = rows_by_host.get(host, [])
        sections.append(f"<h2>{html.escape(host)}</h2>")
        if not host_rows:
            sections.append("<p>No route data collected yet.</p>")
            continue
        sections.append("<table>")
        sections.append(
            "<tr><th>Route</th><th>Hop</th><th>IP</th>"
            "<th>Ping ms</th><th>Avg ms</th><th>Timeout</th></tr>"
        )
        for row in host_rows:
            sections.append(
                "<tr>"
                f"<td>{html.escape(row.route_kind)}</td>"
                f"<td>{row.hop}</td>"
                f"<td>{html.escape(row.ip)}</td>"
                f"<td>{'' if row.ping_ms is None else row.ping_ms}</td>"
                f"<td>{'' if row.avg_ping_ms is None else row.avg_ping_ms}</td>"
                f"<td>{'yes' if row.is_timeout else 'no'}</td>"
                "</tr>"
            )
        sections.append("</table>")

    sections.extend(["</body>", "</html>"])
    return "\n".join(sections)

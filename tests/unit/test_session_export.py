"""Unit tests for session CSV/HTML export."""

from __future__ import annotations

from pathlib import Path

from pingui.export.session_report import export_session_csv, export_session_html
from pingui.models import HopNode, RouteSnapshot
from pingui.monitor.session_store import SessionStore


def _sample_store() -> SessionStore:
    store = SessionStore(["8.8.8.8", "1.1.1.1"])
    store.set_enabled("8.8.8.8", True)
    snapshot = RouteSnapshot(
        "8.8.8.8",
        "8.8.8.8",
        [
            HopNode(1, "10.0.0.1", 5.0, False),
            HopNode(2, "8.8.8.8", 10.0, False),
        ],
    )
    store.update_route("8.8.8.8", snapshot)
    store.append_ping_samples("8.8.8.8", snapshot)
    return store


def test_export_csv_contains_route_rows(tmp_path: Path) -> None:
    store = _sample_store()
    csv_path = tmp_path / "report.csv"
    export_session_csv(store, csv_path)
    text = csv_path.read_text(encoding="utf-8")
    assert "route_kind" in text
    assert "10.0.0.1" in text
    assert "current" in text


def test_export_html_contains_host_table(tmp_path: Path) -> None:
    store = _sample_store()
    html_path = tmp_path / "report.html"
    export_session_html(store, html_path)
    text = html_path.read_text(encoding="utf-8")
    assert "<title>PINGUI session report</title>" in text
    assert "8.8.8.8" in text
    assert "10.0.0.1" in text
    assert "1.1.1.1" in text

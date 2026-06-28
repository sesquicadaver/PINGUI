"""Unit tests for SQLite session persistence."""

from __future__ import annotations

from pathlib import Path

import pytest

from pingui.models import HopNode, HostSessionData, RouteSnapshot
from pingui.monitor.session_store import SessionStore
from pingui.persistence.session_db import SessionDatabase


@pytest.fixture
def db_path(tmp_path: Path) -> Path:
    return tmp_path / "session.db"


def test_save_and_load_roundtrip(db_path: Path) -> None:
    db = SessionDatabase(db_path)
    data = HostSessionData(
        current_route=[HopNode(1, "10.0.0.1", 5.0, False)],
        previous_route=[HopNode(1, "192.168.1.1", 2.0, False)],
        last_known_by_hop={2: HopNode(2, "8.8.8.8", 10.0, False)},
        ping_history={"10.0.0.1": [1.0, 2.0]},
        enabled=True,
    )
    db.save("host.example", data)
    loaded = db.load("host.example")
    db.close()
    assert loaded is not None
    assert loaded.enabled is True
    assert loaded.current_route[0].ip == "10.0.0.1"
    assert loaded.previous_route[0].ip == "192.168.1.1"
    assert loaded.last_known_by_hop[2].ip == "8.8.8.8"
    assert loaded.ping_history["10.0.0.1"] == [1.0, 2.0]


def test_delete_and_rename(db_path: Path) -> None:
    db = SessionDatabase(db_path)
    db.save("old", HostSessionData(enabled=False))
    db.rename("old", "new")
    assert db.load("old") is None
    assert db.load("new") is not None
    db.delete("new")
    assert db.load("new") is None
    db.close()


def test_session_store_persists_updates(db_path: Path) -> None:
    db = SessionDatabase(db_path)
    store = SessionStore(["8.8.8.8"], session_db=db)
    snapshot = RouteSnapshot(
        "8.8.8.8",
        "8.8.8.8",
        [HopNode(1, "10.0.0.1", 4.0, False)],
    )
    store.update_route("8.8.8.8", snapshot)
    store.append_ping_samples("8.8.8.8", snapshot)
    store.close()

    db2 = SessionDatabase(db_path)
    restored = SessionStore(["8.8.8.8"], session_db=db2)
    data = restored.get("8.8.8.8")
    assert data.current_route[0].ip == "10.0.0.1"
    assert data.ping_history["10.0.0.1"] == [4.0]
    restored.close()

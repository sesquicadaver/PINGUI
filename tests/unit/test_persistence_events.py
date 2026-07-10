"""Unit tests for persistence.events YAML and SQLite event writes (PY-P11)."""

from __future__ import annotations

from pathlib import Path

import pytest

from pingui.config import ConfigError, save_hosts_config
from pingui.models import RouteChangeEvent
from pingui.persistence.events import PersistenceEventWriter
from pingui.persistence.policy import (
    EVENT_PROBE_ERROR,
    EVENT_ROUTE_CHANGE,
    PersistencePolicy,
    apply_cli_event_overrides,
    load_persistence_config,
    resolve_session_db_path,
)
from pingui.persistence.session_db import SCHEMA_VERSION, SessionDatabase


def test_load_persistence_events_from_yaml(tmp_path: Path) -> None:
    path = tmp_path / "hosts.yaml"
    path.write_text(
        """
hosts:
  - "8.8.8.8"
persistence:
  session_db: data/from-yaml.db
  events:
    route_change: false
    probe_error: true
""",
        encoding="utf-8",
    )
    cfg = load_persistence_config(path)
    assert cfg.session_db == Path("data/from-yaml.db")
    assert cfg.events.route_change is False
    assert cfg.events.probe_error is True


def test_cli_overrides_disable_events() -> None:
    policy = apply_cli_event_overrides(
        PersistencePolicy(True, True),
        no_persist_route_change=True,
        no_persist_probe_error=False,
    )
    assert policy.route_change is False
    assert policy.probe_error is True


def test_resolve_session_db_cli_wins(tmp_path: Path) -> None:
    path = tmp_path / "hosts.yaml"
    path.write_text(
        """
hosts: []
persistence:
  session_db: yaml.db
""",
        encoding="utf-8",
    )
    assert resolve_session_db_path(Path("cli.db"), path) == Path("cli.db")
    assert resolve_session_db_path(None, path) == Path("yaml.db")


def test_invalid_events_type_raises(tmp_path: Path) -> None:
    path = tmp_path / "bad.yaml"
    path.write_text(
        """
hosts: []
persistence:
  events: []
""",
        encoding="utf-8",
    )
    with pytest.raises(ConfigError, match="persistence.events"):
        load_persistence_config(path)


def test_schema_v3_and_event_writer(tmp_path: Path) -> None:
    db_path = tmp_path / "session.db"
    db = SessionDatabase(db_path)
    assert SCHEMA_VERSION == 3
    writer = PersistenceEventWriter(db, PersistencePolicy(True, True))
    event = RouteChangeEvent.from_route_change("8.8.8.8", [], ["1.1.1.1"])
    writer.write_route_change(event)
    writer.write_probe_error("8.8.8.8", "timeout")
    assert db.count_events(EVENT_ROUTE_CHANGE, "8.8.8.8") == 1
    assert db.count_events(EVENT_PROBE_ERROR, "8.8.8.8") == 1
    db.close()


def test_policy_gate_skips_writes(tmp_path: Path) -> None:
    db = SessionDatabase(tmp_path / "gated.db")
    writer = PersistenceEventWriter(db, PersistencePolicy(False, False))
    writer.write_route_change(RouteChangeEvent.from_route_change("h", [], ["a"]))
    writer.write_probe_error("h", "err")
    assert db.count_events() == 0
    db.close()


def test_rename_preserves_events(tmp_path: Path) -> None:
    db = SessionDatabase(tmp_path / "rename.db")
    writer = PersistenceEventWriter(db)
    writer.write_route_change(RouteChangeEvent.from_route_change("old", [], ["x"]))
    db.rename("old", "new")
    assert db.load("old") is None
    assert db.load("new") is not None
    assert db.count_events(host="new") == 1
    assert db.count_events(host="old") == 0
    db.close()


def test_event_write_from_worker_thread(tmp_path: Path) -> None:
    import threading

    db = SessionDatabase(tmp_path / "threaded.db")
    writer = PersistenceEventWriter(db)
    errors: list[BaseException] = []

    def _write() -> None:
        try:
            writer.write_route_change(RouteChangeEvent.from_route_change("h", [], ["a"]))
            writer.write_probe_error("h", "boom")
        except BaseException as exc:  # noqa: BLE001 — capture for assertion
            errors.append(exc)

    thread = threading.Thread(target=_write)
    thread.start()
    thread.join(timeout=5)
    assert not errors
    assert db.count_events() == 2
    db.close()


def test_save_hosts_preserves_persistence_block(tmp_path: Path) -> None:
    path = tmp_path / "hosts.yaml"
    path.write_text(
        """
hosts:
  - "8.8.8.8"
persistence:
  events:
    route_change: false
""",
        encoding="utf-8",
    )
    save_hosts_config(path, ["1.1.1.1"])
    cfg = load_persistence_config(path)
    assert cfg.events.route_change is False

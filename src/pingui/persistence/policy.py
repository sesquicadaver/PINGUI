"""Persistence event policy and YAML config (PY-P11 / SPIKE P11-002)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

from pingui.config import ConfigError

EVENT_ROUTE_CHANGE = "route_change"
EVENT_PROBE_ERROR = "probe_error"


@dataclass(frozen=True, slots=True)
class PersistencePolicy:
    """Which discrete events to append to ``persistence_event``."""

    route_change: bool = True
    probe_error: bool = True

    def allows(self, event_type: str) -> bool:
        if event_type == EVENT_ROUTE_CHANGE:
            return self.route_change
        if event_type == EVENT_PROBE_ERROR:
            return self.probe_error
        return False


@dataclass(frozen=True, slots=True)
class PersistenceConfig:
    """Optional SQLite path + event toggles from YAML ``persistence:``."""

    session_db: Path | None = None
    events: PersistencePolicy = PersistencePolicy()

    @classmethod
    def defaults(cls) -> PersistenceConfig:
        return cls()


def load_persistence_config(path: Path | str) -> PersistenceConfig:
    """Load optional ``persistence:`` block; missing/unknown → defaults."""
    config_path = Path(path)
    if not config_path.is_file():
        return PersistenceConfig.defaults()

    raw = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        return PersistenceConfig.defaults()

    block = raw.get("persistence")
    if block is None:
        return PersistenceConfig.defaults()
    if not isinstance(block, dict):
        msg = "persistence must be a mapping"
        raise ConfigError(msg)

    session_db: Path | None = None
    raw_db = block.get("session_db")
    if raw_db is not None:
        if not isinstance(raw_db, str) or not raw_db.strip():
            msg = "persistence.session_db must be a non-empty string path"
            raise ConfigError(msg)
        session_db = Path(raw_db.strip())

    events = _parse_events(block.get("events"))
    return PersistenceConfig(session_db=session_db, events=events)


def apply_cli_event_overrides(
    policy: PersistencePolicy,
    *,
    no_persist_route_change: bool = False,
    no_persist_probe_error: bool = False,
) -> PersistencePolicy:
    """CLI flags disable event types (highest priority)."""
    return PersistencePolicy(
        route_change=policy.route_change and not no_persist_route_change,
        probe_error=policy.probe_error and not no_persist_probe_error,
    )


def resolve_session_db_path(
    cli_path: Path | None,
    config_path: Path | str,
) -> Path | None:
    """CLI ``--session-db`` wins over YAML ``persistence.session_db``."""
    if cli_path is not None:
        return cli_path
    return load_persistence_config(config_path).session_db


def _parse_events(raw: Any) -> PersistencePolicy:
    if raw is None:
        return PersistencePolicy()
    if not isinstance(raw, dict):
        msg = "persistence.events must be a mapping"
        raise ConfigError(msg)
    route_change = _bool_field(raw, "route_change", default=True)
    probe_error = _bool_field(raw, "probe_error", default=True)
    return PersistencePolicy(route_change=route_change, probe_error=probe_error)


def _bool_field(mapping: dict[str, Any], key: str, *, default: bool) -> bool:
    if key not in mapping:
        return default
    value = mapping[key]
    if not isinstance(value, bool):
        msg = f"persistence.events.{key} must be a boolean"
        raise ConfigError(msg)
    return value

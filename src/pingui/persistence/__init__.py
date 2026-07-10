"""Optional SQLite persistence for session metrics and discrete events."""

from pingui.persistence.events import PersistenceEventWriter
from pingui.persistence.policy import (
    PersistenceConfig,
    PersistencePolicy,
    apply_cli_event_overrides,
    load_persistence_config,
    resolve_session_db_path,
)
from pingui.persistence.session_db import SessionDatabase

__all__ = [
    "PersistenceConfig",
    "PersistenceEventWriter",
    "PersistencePolicy",
    "SessionDatabase",
    "apply_cli_event_overrides",
    "load_persistence_config",
    "resolve_session_db_path",
]

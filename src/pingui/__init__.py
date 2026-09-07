"""PINGUI — Linux route and ping monitor (legacy / bugfix-only edition)."""

from __future__ import annotations

from importlib.metadata import PackageNotFoundError, version

try:
    __version__ = version("pingui")
except PackageNotFoundError:  # pragma: no cover - editable/dev fallback
    __version__ = "0.2.0"

"""Shared pytest fixtures."""

from __future__ import annotations

import os

import pytest

# Must be set before PyQt/matplotlib imports during test collection.
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ.setdefault("MPLBACKEND", "Agg")


@pytest.fixture(scope="session", autouse=True)
def offscreen_qt() -> None:
    """Ensure headless Qt/matplotlib env for GUI tests."""
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    os.environ.setdefault("MPLBACKEND", "Agg")

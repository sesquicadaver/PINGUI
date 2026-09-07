"""Package version alignment (P34-009)."""

from __future__ import annotations

from importlib.metadata import version

import pingui


def test_package_version_matches_metadata() -> None:
    assert pingui.__version__ == version("pingui")
    assert pingui.__version__ == "0.2.0"

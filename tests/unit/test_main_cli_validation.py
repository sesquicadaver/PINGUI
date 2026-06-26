"""CLI argument validation tests."""

from __future__ import annotations

from pingui.__main__ import main


def test_main_rejects_invalid_interval() -> None:
    assert main(["--interval", "0"]) == 1


def test_main_rejects_invalid_max_hops() -> None:
    assert main(["--max-hops", "0"]) == 1


def test_main_rejects_invalid_timeout() -> None:
    assert main(["--timeout", "-1"]) == 1

"""CLI entry tests."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

from pingui.__main__ import main
from pingui.icmp.raw_socket import RawIcmpPermissionError


def test_main_missing_config() -> None:
    assert main(["--config", "/no/such/config.yaml"]) == 1


def test_main_permission_denied(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 127.0.0.1\n", encoding="utf-8")
    with patch(
        "pingui.__main__.check_raw_icmp_permission",
        side_effect=RawIcmpPermissionError("denied"),
    ):
        assert main(["--config", str(cfg)]) == 1

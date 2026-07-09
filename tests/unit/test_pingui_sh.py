"""pingui.sh launcher argument parsing smoke tests."""

from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_pingui_sh_bash_syntax() -> None:
    subprocess.run(["bash", "-n", str(ROOT / "pingui.sh")], check=True, cwd=ROOT)


def test_pingui_sh_help_lists_passthrough() -> None:
    result = subprocess.run(
        [str(ROOT / "pingui.sh"), "--help"],
        check=True,
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    assert "PINGUI_OPTIONS" in result.stdout or "export-csv" in result.stdout


def test_pingui_sh_rejects_unknown_deploy_arg() -> None:
    result = subprocess.run(
        [str(ROOT / "pingui.sh"), "--deploy", "--export-csv", "out.csv"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    assert result.returncode != 0
    assert "Невідомий аргумент" in result.stderr or "ПОМИЛКА" in result.stderr

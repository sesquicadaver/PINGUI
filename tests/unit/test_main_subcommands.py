"""CLI subcommand tests (PY-023)."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

from pingui.__main__ import main


def test_monitor_subcommand_requires_icmp(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    with (
        patch("pingui.__main__.run_headless_monitor", return_value=0) as run,
        patch("pingui.__main__.check_raw_icmp_permission"),
    ):
        assert (
            main(
                [
                    "monitor",
                    "--config",
                    str(cfg),
                    "--interval",
                    "0.1",
                ]
            )
            == 0
        )
    run.assert_called_once()


def test_daemon_subcommand_passes_pid_file(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    pid = tmp_path / "pingui.pid"
    webhook = "https://hooks.example.com/pingui"
    with (
        patch("pingui.__main__.run_headless_monitor", return_value=0) as run,
        patch("pingui.__main__.check_raw_icmp_permission"),
    ):
        assert (
            main(
                [
                    "daemon",
                    "--config",
                    str(cfg),
                    "--pid-file",
                    str(pid),
                    "--alert-webhook",
                    webhook,
                    "--desktop-alerts",
                ]
            )
            == 0
        )
    assert run.call_args.kwargs["pid_file"] == pid
    dispatcher = run.call_args.kwargs["alert_dispatcher"]
    assert dispatcher is not None


def test_export_subcommand(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    csv_path = tmp_path / "out.csv"
    assert main(["export", "--config", str(cfg), "--csv", str(csv_path)]) == 0
    assert csv_path.is_file()


def test_stop_subcommand(tmp_path: Path) -> None:
    pid = tmp_path / "pingui.pid"
    pid.write_text("99999\n", encoding="utf-8")
    with patch("pingui.__main__.PidFile.stop", return_value=0) as stop:
        assert main(["stop", "--pid-file", str(pid)]) == 0
    stop.assert_called_once_with(pid)

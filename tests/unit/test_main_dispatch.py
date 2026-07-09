"""Additional __main__ dispatch coverage (PY-064)."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

import pytest

from pingui.__main__ import (
    build_parser,
    main,
    parse_args,
)
from pingui.monitor.daemon_runner import DaemonError


def test_parse_args_defaults_to_run_command() -> None:
    args = parse_args(["--interval", "2"])
    assert args.command == "run"
    assert args.interval == 2.0


def test_parse_args_legacy_export_sets_command() -> None:
    args = parse_args(["--export-csv", "out.csv"])
    assert args.command == "export"
    assert args.csv == Path("out.csv")


def test_build_parser_alias() -> None:
    parser = build_parser()
    args = parser.parse_args(["--export-html", "report.html"])
    assert args.export_html == Path("report.html")


def test_main_export_requires_output_path(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    assert main(["export", "--config", str(cfg)]) == 1


def test_main_rejects_invalid_alert_rate_limit(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    assert main(["monitor", "--config", str(cfg), "--alert-rate-limit", "0"]) == 1


def test_main_stop_handles_daemon_error(tmp_path: Path) -> None:
    pid = tmp_path / "missing.pid"
    with patch("pingui.__main__.PidFile.stop", side_effect=DaemonError("missing")):
        assert main(["stop", "--pid-file", str(pid)]) == 1


def test_main_status_missing_pid_file(tmp_path: Path) -> None:
    pid = tmp_path / "missing.pid"
    assert main(["status", "--pid-file", str(pid)]) == 1


def test_main_geoip_hints_error(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    bad_hints = tmp_path / "bad.yaml"
    bad_hints.write_text("prefixes: 1\n", encoding="utf-8")
    with patch("pingui.__main__.check_raw_icmp_permission"):
        assert (
            main(
                [
                    "run",
                    "--config",
                    str(cfg),
                    "--geoip-hints",
                    str(bad_hints),
                ]
            )
            == 1
        )


def test_main_timeseries_config_exits(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    with (
        patch("pingui.__main__.check_raw_icmp_permission"),
        pytest.raises(SystemExit) as exc,
    ):
        main(["monitor", "--config", str(cfg), "--ts-backend", "influx"])
    assert exc.value.code == 1


def test_main_legacy_export_html(tmp_path: Path) -> None:
    cfg = tmp_path / "hosts.yaml"
    cfg.write_text("hosts:\n  - 8.8.8.8\n", encoding="utf-8")
    html_path = tmp_path / "report.html"
    assert main(["--config", str(cfg), "--export-html", str(html_path)]) == 0
    assert html_path.is_file()

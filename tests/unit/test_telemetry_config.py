"""P16-040: load_telemetry_config parity with Java TelemetryConfig."""

from __future__ import annotations

from pathlib import Path

import pytest

from pingui.config import ConfigError
from pingui.telemetry_config import (
    TelemetryConfig,
    apply_cli_overrides,
    load_telemetry_config,
    parse_syslog_host_port,
)


def test_missing_file_defaults(tmp_path: Path) -> None:
    assert load_telemetry_config(tmp_path / "missing.yaml").is_default()


def test_profile_telemetry_block(tmp_path: Path) -> None:
    path = tmp_path / "hosts.yaml"
    path.write_text(
        """
active_profile: noc
profiles:
  noc:
    hosts:
      - "8.8.8.8"
    telemetry:
      events_only: true
      log_aggregates: true
      sqlite: data/telemetry.db
      jsonl_dir: data/telemetry
      syslog:
        host: 127.0.0.1
        port: 1514
        tls: true
      gelf:
        host: 10.0.0.5
        port: 12201
        transport: udp
      loki:
        url: http://127.0.0.1:3100
        site: lab
""",
        encoding="utf-8",
    )
    cfg = load_telemetry_config(path)
    assert cfg.events_only is True
    assert cfg.log_aggregates is True
    assert cfg.sqlite == Path("data/telemetry.db")
    assert cfg.jsonl_dir == Path("data/telemetry")
    assert cfg.syslog is not None and cfg.syslog.port == 1514 and cfg.syslog.tls is True
    assert cfg.gelf is not None and cfg.gelf.transport == "udp"
    assert cfg.loki is not None and cfg.loki.site == "lab"


def test_top_level_telemetry_legacy(tmp_path: Path) -> None:
    path = tmp_path / "legacy.yaml"
    path.write_text(
        """
hosts:
  - "1.1.1.1"
telemetry:
  events_only: false
  syslog:
    host: syslog.example
    port: 514
""",
        encoding="utf-8",
    )
    cfg = load_telemetry_config(path)
    assert cfg.events_only is False
    assert cfg.syslog is not None and cfg.syslog.host == "syslog.example"


def test_invalid_gelf_transport(tmp_path: Path) -> None:
    path = tmp_path / "bad.yaml"
    path.write_text(
        """
telemetry:
  gelf:
    host: 127.0.0.1
    transport: http
""",
        encoding="utf-8",
    )
    with pytest.raises(ConfigError, match="transport"):
        load_telemetry_config(path)


def test_apply_cli_overrides_syslog_and_jsonl() -> None:
    base = TelemetryConfig.defaults()
    cfg = apply_cli_overrides(base, syslog="127.0.0.1:1514", jsonl_dir="data/telemetry")
    assert cfg.syslog is not None and cfg.syslog.host == "127.0.0.1" and cfg.syslog.port == 1514
    assert cfg.jsonl_dir == Path("data/telemetry")
    assert cfg.events_only is True


def test_parse_syslog_ipv6() -> None:
    sink = parse_syslog_host_port("[::1]:514")
    assert sink.host == "::1"
    assert sink.port == 514


def test_parse_syslog_invalid() -> None:
    with pytest.raises(ConfigError, match="HOST:PORT"):
        parse_syslog_host_port("nosport")


def test_defaults_dataclass() -> None:
    assert TelemetryConfig.defaults().is_default()

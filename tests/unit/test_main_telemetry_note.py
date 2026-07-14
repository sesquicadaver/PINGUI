"""P16-093: Python telemetry LOG sinks note (Java/daemon owns emit)."""

from __future__ import annotations

from pathlib import Path

from pingui.__main__ import _note_python_log_sinks
from pingui.telemetry_config import SyslogSinkConfig, TelemetryConfig


def test_note_skipped_for_defaults(capsys) -> None:
    _note_python_log_sinks(TelemetryConfig.defaults(), verbose=True)
    assert capsys.readouterr().err == ""


def test_note_warns_when_log_sink_configured(capsys) -> None:
    cfg = TelemetryConfig(syslog=SyslogSinkConfig(host="127.0.0.1", port=1514))
    _note_python_log_sinks(cfg, verbose=False)
    err = capsys.readouterr().err
    assert "Java GUI/daemon" in err
    assert "InfluxTelemetrySink" in err
    assert "Resolved:" not in err


def test_note_verbose_includes_redacted_summary(capsys) -> None:
    cfg = TelemetryConfig(sqlite=Path("data/t.db"))
    _note_python_log_sinks(cfg, verbose=True)
    err = capsys.readouterr().err
    assert "Resolved: TelemetryConfig{" in err
    assert "sqlite=" in err

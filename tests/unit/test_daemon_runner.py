"""DaemonRunner and PID file tests."""

from __future__ import annotations

import os
import signal
from unittest.mock import patch

import pytest

from pingui.monitor.daemon_runner import DaemonError, PidFile


def test_pid_file_acquire_and_release(tmp_path) -> None:
    path = tmp_path / "pingui.pid"
    pid = PidFile(path)
    pid.acquire()
    assert path.read_text(encoding="utf-8").strip() == str(os.getpid())
    pid.release()
    assert not path.exists()


def test_pid_file_acquire_twice_fails(tmp_path) -> None:
    path = tmp_path / "pingui.pid"
    PidFile(path).acquire()
    with pytest.raises(DaemonError, match="already exists"):
        PidFile(path).acquire()


def test_pid_file_acquire_removes_stale_file(tmp_path) -> None:
    path = tmp_path / "pingui.pid"
    path.write_text("999999\n", encoding="utf-8")
    pid = PidFile(path)
    pid.acquire()
    assert path.read_text(encoding="utf-8").strip() == str(os.getpid())
    pid.release()


def test_pid_file_stop_sends_sigterm(tmp_path) -> None:
    path = tmp_path / "pingui.pid"
    path.write_text("4242\n", encoding="utf-8")
    with patch("pingui.monitor.daemon_runner.os.kill") as kill:
        assert PidFile.stop(path) == 0
    kill.assert_called_once_with(4242, signal.SIGTERM)


def test_pid_file_status_running(tmp_path) -> None:
    path = tmp_path / "pingui.pid"
    path.write_text(f"{os.getpid()}\n", encoding="utf-8")
    assert PidFile.status(path) == 0

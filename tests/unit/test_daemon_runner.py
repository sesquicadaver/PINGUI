"""DaemonRunner and PID file tests."""

from __future__ import annotations

import os
import signal
from unittest.mock import MagicMock, patch

import pytest

from pingui.models import RouteChangeEvent
from pingui.monitor.daemon_runner import (
    DaemonError,
    PidFile,
    _store_callbacks,
    run_headless_monitor,
)
from pingui.monitor.session_store import SessionStore


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


def test_store_callbacks_dispatches_route_change_alert() -> None:
    store = SessionStore(["8.8.8.8"])
    dispatcher = MagicMock()
    callbacks = _store_callbacks(store, alert_dispatcher=dispatcher, profile="noc")
    assert callbacks.on_route_changed is not None
    callbacks.on_route_changed("8.8.8.8", ["1.1.1.1"], ["2.2.2.2"])
    dispatcher.dispatch.assert_called_once()
    event = dispatcher.dispatch.call_args.args[0]
    assert isinstance(event, RouteChangeEvent)
    assert event.host == "8.8.8.8"
    assert event.profile == "noc"


def test_run_headless_monitor_cleanup_is_idempotent(tmp_path) -> None:
    """Signal stop + finally + atexit must not double-close the session DB (P34-009)."""
    db_path = tmp_path / "daemon.db"
    pid_path = tmp_path / "daemon.pid"

    class ImmediateStopLoop:
        def __init__(self, **_kwargs: object) -> None:
            self._running = True

        def start(self) -> None:
            self._running = False

        def stop(self) -> None:
            self._running = False

        def join(self, timeout: float | None = None) -> None:
            return None

        def is_running(self) -> bool:
            return self._running

        def is_alive(self) -> bool:
            return False

    with patch("pingui.monitor.daemon_runner.MonitorLoop", ImmediateStopLoop):
        assert (
            run_headless_monitor(
                ["8.8.8.8"],
                interval_seconds=1.0,
                max_hops=5,
                timeout=1.0,
                session_db_path=db_path,
                pid_file=pid_path,
            )
            == 0
        )
    assert not pid_path.exists()


def test_run_headless_skips_db_close_while_monitor_alive(tmp_path) -> None:
    """P35-010: stuck monitor join must not close SessionStore/DB under a live worker."""
    db_path = tmp_path / "stuck.db"
    pid_path = tmp_path / "stuck.pid"
    close_calls: list[str] = []

    class StuckLoop:
        def __init__(self, **_kwargs: object) -> None:
            self._running = True

        def start(self) -> None:
            self._running = False

        def stop(self) -> None:
            self._running = False

        def join(self, timeout: float | None = None) -> None:
            return None

        def is_running(self) -> bool:
            return self._running

        def is_alive(self) -> bool:
            return True

    with (
        patch("pingui.monitor.daemon_runner.MonitorLoop", StuckLoop),
        patch.object(SessionStore, "close", autospec=True, side_effect=lambda self: close_calls.append("store")),
    ):
        assert (
            run_headless_monitor(
                ["8.8.8.8"],
                interval_seconds=1.0,
                max_hops=5,
                timeout=1.0,
                session_db_path=db_path,
                pid_file=pid_path,
            )
            == 0
        )
    assert close_calls == []
    assert not pid_path.exists()

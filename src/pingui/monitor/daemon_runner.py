from __future__ import annotations

import logging
import os
import signal
import sys
import time
from pathlib import Path

from pingui.icmp.raw_socket import ProbeTransport
from pingui.models import RouteChangeEvent
from pingui.monitor.alert_dispatcher import AlertDispatcher
from pingui.monitor.monitor_loop import MonitorCallbacks, MonitorLoop
from pingui.monitor.session_store import SessionStore
from pingui.persistence.events import PersistenceEventWriter
from pingui.persistence.policy import PersistencePolicy
from pingui.persistence.session_db import SessionDatabase
from pingui.persistence.timeseries.base import TimeSeriesBackend
from pingui.telemetry_emit import QueueTelemetryEmitter

logger = logging.getLogger(__name__)

DEFAULT_PID_FILE = Path("/tmp/pingui.pid")


class DaemonError(RuntimeError):
    """Raised when daemon PID operations fail."""


class PidFile:
    """Simple PID file for start/stop/status."""

    def __init__(self, path: Path) -> None:
        self.path = path

    def acquire(self) -> None:
        if self.path.exists():
            try:
                pid = int(self.path.read_text(encoding="utf-8").strip())
                os.kill(pid, 0)
            except (ValueError, ProcessLookupError):
                self.path.unlink(missing_ok=True)
            except PermissionError:
                msg = f"PID file already exists: {self.path}"
                raise DaemonError(msg) from None
            else:
                msg = f"PID file already exists: {self.path}"
                raise DaemonError(msg)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(f"{os.getpid()}\n", encoding="utf-8")

    def release(self) -> None:
        try:
            self.path.unlink(missing_ok=True)
        except OSError:
            logger.exception("Failed to remove PID file %s", self.path)

    @staticmethod
    def read_pid(path: Path) -> int:
        if not path.is_file():
            msg = f"PID file not found: {path}"
            raise DaemonError(msg)
        try:
            return int(path.read_text(encoding="utf-8").strip())
        except ValueError as exc:
            msg = f"Invalid PID file: {path}"
            raise DaemonError(msg) from exc

    @classmethod
    def stop(cls, path: Path) -> int:
        pid = cls.read_pid(path)
        os.kill(pid, signal.SIGTERM)
        print(f"Sent SIGTERM to PID {pid}")
        return 0

    @classmethod
    def status(cls, path: Path) -> int:
        try:
            pid = cls.read_pid(path)
        except DaemonError as exc:
            print(str(exc), file=sys.stderr)
            return 1
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            print(f"not running (stale PID file: {path})")
            return 1
        except PermissionError:
            print(f"running (pid {pid}, permission denied for signal 0)")
            return 0
        print(f"running (pid {pid})")
        return 0


def _store_callbacks(
    store: SessionStore,
    *,
    alert_dispatcher: AlertDispatcher | None = None,
    profile: str = "default",
    event_writer: PersistenceEventWriter | None = None,
) -> MonitorCallbacks:
    def on_data(host: str, snapshot: object) -> None:
        from pingui.models import RouteSnapshot

        if not isinstance(snapshot, RouteSnapshot):
            return
        try:
            store.update_route(host, snapshot)
            store.append_ping_samples(host, snapshot)
        except KeyError:
            logger.debug("Skipping update for removed host: %s", host)

    def on_route_change(host: str, old_ips: list[str], new_ips: list[str]) -> None:
        logger.info(
            "Route change %s: %s -> %s",
            host,
            " ".join(old_ips) or "(none)",
            " ".join(new_ips) or "(none)",
        )
        event = RouteChangeEvent.from_route_change(
            host,
            old_ips,
            new_ips,
            profile=profile,
        )
        if event_writer is not None:
            event_writer.write_route_change(event)
        if alert_dispatcher is not None:
            alert_dispatcher.dispatch(event)

    def on_error(host: str, message: str) -> None:
        logger.warning("Probe error %s: %s", host, message)
        if event_writer is not None:
            event_writer.write_probe_error(host, message)

    return MonitorCallbacks(
        on_data_received=on_data,
        on_route_changed=on_route_change,
        on_probe_error=on_error,
    )


def run_headless_monitor(
    hosts: list[str],
    *,
    interval_seconds: float,
    max_hops: int,
    timeout: float,
    session_db_path: Path | None = None,
    timeseries_backend: TimeSeriesBackend | None = None,
    pid_file: Path | None = None,
    enable_all_hosts: bool = True,
    transport: ProbeTransport | None = None,
    alert_dispatcher: AlertDispatcher | None = None,
    profile: str = "default",
    persistence_policy: PersistencePolicy | None = None,
) -> int:
    """
    Run monitoring loop until SIGINT/SIGTERM.

    When ``pid_file`` is set, write PID on start and remove on exit.
    """
    session_db = SessionDatabase(session_db_path) if session_db_path is not None else None
    event_writer = (
        PersistenceEventWriter(session_db, persistence_policy) if session_db is not None else None
    )
    store = SessionStore(hosts, session_db=session_db, timeseries=timeseries_backend)
    if enable_all_hosts:
        for host in hosts:
            store.set_enabled(host, True)

    telemetry = QueueTelemetryEmitter()
    loop = MonitorLoop(
        session_store=store,
        interval_seconds=interval_seconds,
        max_hops=max_hops,
        timeout=timeout,
        transport=transport,
        callbacks=_store_callbacks(
            store,
            alert_dispatcher=alert_dispatcher,
            profile=profile,
            event_writer=event_writer,
        ),
        telemetry=telemetry,
    )

    pid = PidFile(pid_file) if pid_file is not None else None
    shutting_down = False

    def _shutdown(_signum: int, _frame: object) -> None:
        nonlocal shutting_down
        if shutting_down:
            raise SystemExit(0)
        shutting_down = True
        logger.info("Shutting down monitor (signal %s)", _signum)
        loop.stop()

    signal.signal(signal.SIGINT, _shutdown)
    signal.signal(signal.SIGTERM, _shutdown)

    try:
        if pid is not None:
            try:
                pid.acquire()
            except DaemonError as exc:
                logger.error("%s", exc)
                return 1
        loop.start()
        logger.info(
            "Monitoring %d host(s); interval=%.1fs",
            len(hosts),
            interval_seconds,
        )
        while loop.is_running():
            time.sleep(0.5)
    finally:
        loop.stop()
        loop.join(timeout=5.0)
        telemetry.close()
        store.close()
        if pid is not None:
            pid.release()

    return 0

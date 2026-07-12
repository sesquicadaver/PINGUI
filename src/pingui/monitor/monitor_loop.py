"""Headless monitoring loop (stdlib threading, no Qt)."""

from __future__ import annotations

import logging
import threading
import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import TYPE_CHECKING

from pingui.config import MAX_HOSTS, ConfigError, validate_session_host
from pingui.icmp.raw_socket import ProbeTransport
from pingui.models import (
    TIMEOUT_IP,
    MetricSample,
    RouteSnapshot,
    TelemetryEvent,
)
from pingui.monitor.polling import poll_host_route
from pingui.telemetry_emit import NULL_TELEMETRY, TelemetryEmitter

if TYPE_CHECKING:
    from pingui.monitor.session_store import SessionStore

logger = logging.getLogger(__name__)

DataCallback = Callable[[str, RouteSnapshot], None]
RouteChangeCallback = Callable[[str, list[str], list[str]], None]
ErrorCallback = Callable[[str, str], None]


@dataclass(slots=True)
class MonitorCallbacks:
    """Optional hooks invoked from the monitor thread."""

    on_data_received: DataCallback | None = None
    on_route_changed: RouteChangeCallback | None = None
    on_probe_error: ErrorCallback | None = None


class MonitorLoop:
    """
    Poll enabled hosts on a background thread.

    When ``session_store`` is set, host list and enabled flags are read from the
    store (single source of truth). Otherwise internal state is used (unit tests).
    """

    def __init__(
        self,
        hosts: list[str] | None = None,
        *,
        session_store: SessionStore | None = None,
        interval_seconds: float = 1.0,
        max_hops: int = 20,
        timeout: float = 0.5,
        transport: ProbeTransport | None = None,
        callbacks: MonitorCallbacks | None = None,
        telemetry: TelemetryEmitter | None = None,
    ) -> None:
        self._store = session_store
        self._lock = threading.Lock()
        self._hosts = list(hosts or [])
        self._enabled: set[str] = set()
        self._interval = interval_seconds
        self._max_hops = max_hops
        self._timeout = timeout
        self._transport = transport
        self._callbacks = callbacks or MonitorCallbacks()
        self._telemetry: TelemetryEmitter = telemetry or NULL_TELEMETRY
        self._running = False
        self._last_routes: dict[str, list[str]] = {
            host: [] for host in self._hosts
        }
        self._thread: threading.Thread | None = None

    def set_telemetry(self, telemetry: TelemetryEmitter | None) -> None:
        """Optional telemetry emitter (P16-013); None resets to no-op."""
        self._telemetry = telemetry or NULL_TELEMETRY

    def hosts(self) -> list[str]:
        if self._store is not None:
            return self._store.hosts()
        with self._lock:
            return list(self._hosts)

    def enabled_hosts(self) -> list[str]:
        if self._store is not None:
            return self._store.enabled_hosts()
        with self._lock:
            return [host for host in self._hosts if host in self._enabled]

    def can_add_host(self) -> bool:
        if self._store is not None:
            return self._store.can_add_host()
        with self._lock:
            return len(self._hosts) < MAX_HOSTS

    def add_host(self, host: str, *, enabled: bool = False) -> str:
        if self._store is not None:
            return self._store.add_host(host, enabled=enabled)
        normalized = validate_session_host(host, self.hosts())
        with self._lock:
            if normalized in self._hosts:
                msg = f"Host already in list: {normalized}"
                raise ConfigError(msg)
            self._hosts.append(normalized)
            self._last_routes[normalized] = []
            if enabled:
                self._enabled.add(normalized)
        return normalized

    def set_host_enabled(self, host: str, enabled: bool) -> None:
        if self._store is not None:
            if host not in self._store.hosts():
                msg = f"Unknown host: {host}"
                raise ConfigError(msg)
            if enabled:
                active = self.enabled_hosts()
                if len(active) >= MAX_HOSTS and host not in active:
                    msg = f"Maximum {MAX_HOSTS} active traces at once"
                    raise ConfigError(msg)
            self._store.set_enabled(host, enabled)
            return
        with self._lock:
            if host not in self._hosts:
                msg = f"Unknown host: {host}"
                raise ConfigError(msg)
            if enabled:
                if len(self._enabled) >= MAX_HOSTS and host not in self._enabled:
                    msg = f"Maximum {MAX_HOSTS} active traces at once"
                    raise ConfigError(msg)
                self._enabled.add(host)
            else:
                self._enabled.discard(host)

    def rename_host(self, old: str, new: str) -> str:
        if self._store is not None:
            return self._store.rename_host(old, new)
        others = [h for h in self.hosts() if h != old]
        normalized = validate_session_host(new, others)
        with self._lock:
            if old not in self._hosts:
                msg = f"Unknown host: {old}"
                raise ConfigError(msg)
            idx = self._hosts.index(old)
            self._hosts[idx] = normalized
            if old in self._enabled:
                self._enabled.discard(old)
                self._enabled.add(normalized)
            self._last_routes[normalized] = self._last_routes.pop(old, [])
        return normalized

    def remove_host(self, host: str) -> None:
        if self._store is not None:
            self._store.remove_host(host)
            return
        with self._lock:
            if host not in self._hosts:
                msg = f"Unknown host: {host}"
                raise ConfigError(msg)
            self._hosts.remove(host)
            self._enabled.discard(host)
            self._last_routes.pop(host, None)

    def set_callbacks(self, callbacks: MonitorCallbacks) -> None:
        self._callbacks = callbacks

    def start(self) -> None:
        if self._thread is not None and self._thread.is_alive():
            return
        self._running = True
        self._thread = threading.Thread(
            target=self._run,
            name="pingui-monitor-loop",
            daemon=True,
        )
        self._thread.start()

    def stop(self) -> None:
        self._running = False

    def join(self, timeout: float | None = None) -> None:
        if self._thread is not None:
            self._thread.join(timeout)

    def is_running(self) -> bool:
        return self._running and self._thread is not None and self._thread.is_alive()

    def _run(self) -> None:
        while self._running:
            for host in self.enabled_hosts():
                if not self._running:
                    break
                with self._lock:
                    previous_ips = list(self._last_routes.get(host, []))
                started = time.perf_counter()
                outcome = poll_host_route(
                    host,
                    previous_ips,
                    max_hops=self._max_hops,
                    timeout=self._timeout,
                    transport=self._transport,
                )
                duration_ms = (time.perf_counter() - started) * 1000.0
                if outcome.error is not None:
                    logger.warning("Probe failed for %s: %s", host, outcome.error)
                    self._emit_probe_error(host, outcome.error)
                    if self._callbacks.on_probe_error is not None:
                        self._callbacks.on_probe_error(host, outcome.error)
                    continue

                if outcome.route_changed:
                    self._emit_route_change(host, outcome.old_ips, outcome.new_ips)
                    if self._callbacks.on_route_changed is not None:
                        self._callbacks.on_route_changed(
                            host,
                            outcome.old_ips,
                            outcome.new_ips,
                        )
                with self._lock:
                    self._last_routes[host] = outcome.current_ips
                if outcome.snapshot is not None:
                    self._emit_success_samples(host, outcome.snapshot, duration_ms)
                    if self._callbacks.on_data_received is not None:
                        self._callbacks.on_data_received(host, outcome.snapshot)

            if self._running:
                time.sleep(self._interval)

    def _emit_probe_error(self, host: str, message: str) -> None:
        try:
            self._telemetry.offer_event(
                TelemetryEvent.probe_error(host, message, labels={"profile": "default"})
            )
        except Exception:  # noqa: BLE001 — never break poll on telemetry
            logger.warning("Telemetry probe_error offer failed for %s", host, exc_info=True)

    def _emit_route_change(self, host: str, old_ips: list[str], new_ips: list[str]) -> None:
        try:
            self._telemetry.offer_event(
                TelemetryEvent.route_change(
                    host,
                    old_ips,
                    new_ips,
                    labels={"profile": "default"},
                )
            )
        except Exception:  # noqa: BLE001
            logger.warning("Telemetry route_change offer failed for %s", host, exc_info=True)

    def _emit_success_samples(
        self, host: str, snapshot: RouteSnapshot, duration_ms: float = 0.0
    ) -> None:
        ts = datetime.now(UTC)
        labels = {"profile": "default", "probe_mode": "trace"}
        try:
            if snapshot.target_ip:
                reachable = any(
                    not n.is_timeout and n.ip != TIMEOUT_IP and n.ip == snapshot.target_ip
                    for n in snapshot.nodes
                )
            else:
                reachable = any(not n.is_timeout and n.ip != TIMEOUT_IP for n in snapshot.nodes)
            self._telemetry.offer_sample(
                MetricSample(
                    name="pingui_target_reachable",
                    value=1.0 if reachable else 0.0,
                    host=host,
                    hop=None,
                    labels=labels,
                    timestamp=ts,
                )
            )
            self._telemetry.offer_sample(
                MetricSample(
                    name="pingui_trace_duration_ms",
                    value=duration_ms,
                    host=host,
                    hop=None,
                    labels=labels,
                    timestamp=ts,
                )
            )
            for node in snapshot.nodes:
                loss = 0.0 if (not node.is_timeout and node.ping_ms is not None) else 100.0
                self._telemetry.offer_sample(
                    MetricSample(
                        name="pingui_hop_loss_pct",
                        value=loss,
                        host=host,
                        hop=node.hop,
                        labels=labels,
                        timestamp=ts,
                    )
                )
                if node.ping_ms is not None and not node.is_timeout:
                    self._telemetry.offer_sample(
                        MetricSample.rtt_ms(
                            host,
                            node.hop,
                            node.ping_ms,
                            labels=labels,
                            timestamp=ts,
                        )
                    )
        except Exception:  # noqa: BLE001
            logger.warning("Telemetry sample offer failed for %s", host, exc_info=True)

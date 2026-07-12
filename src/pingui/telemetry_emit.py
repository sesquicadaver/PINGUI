"""Optional telemetry emit surface for the Python monitor loop (P16-013)."""

from __future__ import annotations

import logging
import threading
from collections.abc import Callable
from contextlib import suppress
from queue import Empty, Full, Queue
from typing import Protocol

from pingui.models import MetricSample, TelemetryEvent

logger = logging.getLogger(__name__)

SampleHandler = Callable[[MetricSample], None]
EventHandler = Callable[[TelemetryEvent], None]


class TelemetryEmitter(Protocol):
    """Non-blocking offer API mirrored from Java TelemetryBus."""

    def offer_sample(self, sample: MetricSample) -> bool: ...

    def offer_event(self, event: TelemetryEvent) -> bool: ...


class NullTelemetryEmitter:
    """Default no-op emitter (sinks off)."""

    def offer_sample(self, sample: MetricSample) -> bool:
        return True

    def offer_event(self, event: TelemetryEvent) -> bool:
        return True


class QueueTelemetryEmitter:
    """
    Bounded async emitter (Python parity with Java TelemetryBus DROP_OLDEST).

    ``offer_*`` never blocks the poll thread; overflow drops the oldest queued item.
    """

    def __init__(
        self,
        *,
        capacity: int = 8192,
        on_sample: SampleHandler | None = None,
        on_event: EventHandler | None = None,
    ) -> None:
        if capacity < 1:
            msg = "capacity must be >= 1"
            raise ValueError(msg)
        self._queue: Queue[tuple[str, MetricSample | TelemetryEvent]] = Queue(maxsize=capacity)
        self._on_sample = on_sample
        self._on_event = on_event
        self._dropped = 0
        self._lock = threading.Lock()
        self._running = True
        self._thread = threading.Thread(target=self._run, name="pingui-telemetry-emit", daemon=True)
        self._thread.start()

    @property
    def dropped_count(self) -> int:
        with self._lock:
            return self._dropped

    def offer_sample(self, sample: MetricSample) -> bool:
        return self._offer(("sample", sample))

    def offer_event(self, event: TelemetryEvent) -> bool:
        return self._offer(("event", event))

    def close(self) -> None:
        self._running = False
        self._thread.join(timeout=5.0)
        while True:
            try:
                kind, payload = self._queue.get_nowait()
            except Empty:
                break
            self._dispatch(kind, payload)

    def _offer(self, item: tuple[str, MetricSample | TelemetryEvent]) -> bool:
        if not self._running:
            with self._lock:
                self._dropped += 1
            return False
        try:
            self._queue.put_nowait(item)
            return True
        except Full:
            with suppress(Empty):
                self._queue.get_nowait()
            with self._lock:
                self._dropped += 1
            try:
                self._queue.put_nowait(item)
                return True
            except Full:
                with self._lock:
                    self._dropped += 1
                return False

    def _run(self) -> None:
        while self._running or not self._queue.empty():
            try:
                kind, payload = self._queue.get(timeout=0.05)
            except Empty:
                continue
            self._dispatch(kind, payload)

    def _dispatch(self, kind: str, payload: MetricSample | TelemetryEvent) -> None:
        try:
            if (
                kind == "sample"
                and self._on_sample is not None
                and isinstance(payload, MetricSample)
            ):
                self._on_sample(payload)
            elif (
                kind == "event"
                and self._on_event is not None
                and isinstance(payload, TelemetryEvent)
            ):
                self._on_event(payload)
        except Exception:  # noqa: BLE001 — sink isolation
            logger.warning("Telemetry emitter dispatch failed", exc_info=True)


NULL_TELEMETRY: TelemetryEmitter = NullTelemetryEmitter()

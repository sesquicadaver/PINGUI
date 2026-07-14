"""P16-013: MonitorLoop offers telemetry samples/events without blocking poll."""

from __future__ import annotations

from datetime import UTC, datetime

from pingui.models import HopNode, MetricSample, RouteSnapshot, TelemetryEvent
from pingui.monitor.monitor_loop import MonitorLoop
from pingui.monitor.polling import HostPollOutcome


class RecordingEmitter:
    def __init__(self) -> None:
        self.samples: list[MetricSample] = []
        self.events: list[TelemetryEvent] = []

    def offer_sample(self, sample: MetricSample) -> bool:
        self.samples.append(sample)
        return True

    def offer_event(self, event: TelemetryEvent) -> bool:
        self.events.append(event)
        return True


def test_monitor_loop_emits_success_and_route_change(monkeypatch) -> None:
    emitter = RecordingEmitter()
    snapshot = RouteSnapshot(
        target="8.8.8.8",
        target_ip="8.8.8.8",
        nodes=[HopNode(1, "10.0.0.1", 5.0), HopNode(2, "8.8.8.8", 10.0)],
        timestamp=datetime.now(UTC),
    )
    loop = MonitorLoop(hosts=["8.8.8.8"], interval_seconds=10.0, telemetry=emitter)
    loop.set_host_enabled("8.8.8.8", True)

    def fake_poll(host, previous_ips, **kwargs):  # noqa: ANN001
        return HostPollOutcome(
            snapshot=snapshot,
            old_ips=[],
            new_ips=["10.0.0.1", "8.8.8.8"],
            current_ips=["10.0.0.1", "8.8.8.8"],
            route_changed=True,
            error=None,
        )

    monkeypatch.setattr("pingui.monitor.monitor_loop.poll_host_route", fake_poll)
    loop._running = True
    # One iteration of the poll body without sleeping forever:
    host = "8.8.8.8"
    previous_ips: list[str] = []
    outcome = fake_poll(host, previous_ips)
    assert outcome.error is None
    loop._emit_route_change(host, outcome.old_ips, outcome.new_ips)
    loop._emit_success_samples(host, snapshot, duration_ms=12.5)
    assert any(s.name == "pingui_rtt_ms" for s in emitter.samples)
    assert any(s.name == "pingui_hop_loss_pct" for s in emitter.samples)
    assert any(s.name == "pingui_trace_duration_ms" for s in emitter.samples)
    assert any(e.event == "route_change" for e in emitter.events)


def test_queue_emitter_drops_oldest_when_full() -> None:
    import threading

    from pingui.telemetry_emit import QueueTelemetryEmitter

    received: list[MetricSample] = []
    entered = threading.Event()
    release = threading.Event()

    def handler(sample: MetricSample) -> None:
        entered.set()
        release.wait(2)
        received.append(sample)

    emitter = QueueTelemetryEmitter(capacity=1, on_sample=handler)
    s1 = MetricSample.rtt_ms("h", 1, 1.0, timestamp=datetime.now(UTC))
    s2 = MetricSample.rtt_ms("h", 2, 2.0, timestamp=datetime.now(UTC))
    s3 = MetricSample.rtt_ms("h", 3, 3.0, timestamp=datetime.now(UTC))
    assert emitter.offer_sample(s1)
    assert entered.wait(1)
    assert emitter.offer_sample(s2)
    assert emitter.offer_sample(s3)
    assert emitter.dropped_count >= 1
    release.set()
    emitter.close()
    assert any(s.hop == 3 for s in received)


def test_monitor_loop_emits_probe_error(monkeypatch) -> None:
    emitter = RecordingEmitter()
    loop = MonitorLoop(hosts=["8.8.8.8"], telemetry=emitter)
    loop._emit_probe_error("8.8.8.8", "timeout")
    assert len(emitter.events) == 1
    assert emitter.events[0].event == "probe_error"
    assert emitter.events[0].message == "timeout"

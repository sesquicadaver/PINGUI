"""Unit tests for InfluxTelemetrySink (P16-052)."""

from __future__ import annotations

from datetime import UTC, datetime

from pingui.metric_names import HOP_LOSS_PCT, TARGET_REACHABLE, python_labels
from pingui.models import MetricSample, TelemetryEvent
from pingui.persistence.timeseries.influx_telemetry_sink import InfluxTelemetrySink
from pingui.persistence.timeseries.memory import MemoryTimeSeriesBackend
from pingui.telemetry_emit import QueueTelemetryEmitter


def test_influx_telemetry_sink_maps_rtt_and_route_change() -> None:
    backend = MemoryTimeSeriesBackend()
    sink = InfluxTelemetrySink(backend)
    assert sink.id == "influx"
    assert sink.events_only is False
    ts = datetime(2026, 7, 14, 9, 0, tzinfo=UTC)
    labels = {**python_labels("lab", "trace"), "hop_ip": "10.0.0.1"}
    sink.on_sample(MetricSample(TARGET_REACHABLE, 1.0, "8.8.8.8", None, labels, ts))
    sink.on_sample(MetricSample(HOP_LOSS_PCT, 0.0, "8.8.8.8", 1, labels, ts))
    sink.on_sample(MetricSample.rtt_ms("8.8.8.8", 1, 12.5, labels=labels, timestamp=ts))
    sink.on_event(
        TelemetryEvent.route_change(
            "8.8.8.8",
            ["10.0.0.1"],
            ["10.0.0.1", "8.8.8.8"],
            labels=python_labels("lab", "trace"),
            timestamp=ts,
        )
    )
    sink.on_event(
        TelemetryEvent.route_change(
            "8.8.8.8",
            [],
            ["8.8.8.8"],
            labels=python_labels("lab", "trace"),
            timestamp=ts,
        )
    )
    assert len(backend.ping_samples) == 1
    assert backend.ping_samples[0].hop_ip == "10.0.0.1"
    assert backend.ping_samples[0].rtt_ms == 12.5
    assert len(backend.route_events) == 2
    assert backend.route_events[0].route_changed is True
    assert backend.route_events[1].route_changed is False
    sink.close()


def test_influx_telemetry_sink_fallback_hop_ip() -> None:
    backend = MemoryTimeSeriesBackend()
    sink = InfluxTelemetrySink(backend)
    ts = datetime(2026, 7, 14, 9, 0, tzinfo=UTC)
    sink.on_sample(
        MetricSample.rtt_ms("h", 2, 5.0, labels=python_labels("d", "trace"), timestamp=ts)
    )
    assert backend.ping_samples[0].hop_ip == "hop2"
    sink.close()


def test_queue_emitter_feeds_influx_telemetry_sink() -> None:
    backend = MemoryTimeSeriesBackend()
    sink = InfluxTelemetrySink(backend)
    emitter = QueueTelemetryEmitter(on_sample=sink.on_sample, on_event=sink.on_event, capacity=64)
    ts = datetime(2026, 7, 14, 9, 0, tzinfo=UTC)
    emitter.offer_sample(
        MetricSample.rtt_ms(
            "1.1.1.1",
            1,
            3.0,
            labels={**python_labels("noc", "ping_only"), "hop_ip": "1.1.1.1"},
            timestamp=ts,
        )
    )
    emitter.offer_event(
        TelemetryEvent.route_change("1.1.1.1", ["9.9.9.9"], ["1.1.1.1"], timestamp=ts)
    )
    emitter.close()
    assert len(backend.ping_samples) == 1
    assert backend.route_events[0].route_ips == ["1.1.1.1"]
    sink.close()

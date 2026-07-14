"""MetricSample / TelemetryEvent serialize tests (P16-010)."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from pingui.models import (
    METRIC_SAMPLE_KIND,
    PROBE_ERROR_EVENT_TYPE,
    ROUTE_CHANGE_EVENT_TYPE,
    TELEMETRY_EVENT_KIND,
    MetricSample,
    TelemetryEvent,
)

JAVA_SAMPLE = (
    '{"kind":"sample","name":"pingui_rtt_ms","value":12.5,"host":"8.8.8.8","hop":3,'
    '"labels":{"probe_mode":"traceroute","profile":"noc"},"timestamp":"2026-07-12T13:00:00Z"}'
)

JAVA_ROUTE_CHANGE = (
    '{"kind":"event","event":"route_change","host":"8.8.8.8",'
    '"labels":{"profile":"noc"},"message":null,'
    '"old_ips":["10.0.0.1","192.168.1.1"],"new_ips":["10.0.0.1","8.8.8.8"],'
    '"timestamp":"2026-07-12T13:00:00Z"}'
)


def test_metric_sample_roundtrip() -> None:
    ts = datetime(2026, 7, 12, 13, 0, 0, tzinfo=UTC)
    sample = MetricSample.rtt_ms(
        "8.8.8.8",
        3,
        12.5,
        labels={"profile": "noc", "probe_mode": "traceroute"},
        timestamp=ts,
    )
    restored = MetricSample.from_json(sample.to_json())
    assert restored == sample
    assert sample.to_dict()["kind"] == METRIC_SAMPLE_KIND
    assert '"labels":{"probe_mode":"traceroute","profile":"noc"}' in sample.to_json()


def test_metric_sample_parses_java_contract() -> None:
    sample = MetricSample.from_json(JAVA_SAMPLE)
    assert sample.name == "pingui_rtt_ms"
    assert sample.value == 12.5
    assert sample.hop == 3
    assert sample.labels["profile"] == "noc"


def test_telemetry_event_route_change_roundtrip() -> None:
    ts = datetime(2026, 7, 12, 13, 0, 0, tzinfo=UTC)
    event = TelemetryEvent.route_change(
        "8.8.8.8",
        ["10.0.0.1"],
        ["8.8.8.8"],
        labels={"profile": "noc"},
        timestamp=ts,
    )
    restored = TelemetryEvent.from_json(event.to_json())
    assert restored == event
    assert event.to_dict()["kind"] == TELEMETRY_EVENT_KIND
    assert event.event == ROUTE_CHANGE_EVENT_TYPE


def test_telemetry_event_parses_java_contract() -> None:
    event = TelemetryEvent.from_json(JAVA_ROUTE_CHANGE)
    assert event.event == ROUTE_CHANGE_EVENT_TYPE
    assert event.old_ips == ["10.0.0.1", "192.168.1.1"]
    assert event.new_ips == ["10.0.0.1", "8.8.8.8"]
    assert event.message is None


def test_telemetry_event_probe_error() -> None:
    event = TelemetryEvent.probe_error(
        "1.1.1.1",
        "timeout",
        labels={"profile": "default"},
        timestamp=datetime(2026, 7, 12, 13, 0, 0, tzinfo=UTC),
    )
    assert TelemetryEvent.from_json(event.to_json()) == event
    assert event.event == PROBE_ERROR_EVENT_TYPE


def test_metric_sample_null_hop_host_gauge() -> None:
    ts = datetime(2026, 7, 12, 13, 0, 0, tzinfo=UTC)
    sample = MetricSample(
        name="pingui_target_reachable",
        value=1.0,
        host="1.1.1.1",
        hop=None,
        labels={"profile": "default"},
        timestamp=ts,
    )
    assert MetricSample.from_json(sample.to_json()).hop is None


def test_telemetry_event_colliding_label_keys() -> None:
    raw = (
        '{"kind":"event","event":"probe_error","host":"h",'
        '"labels":{"message":"from-label","timestamp":"from-label"},'
        '"message":"real-message","old_ips":[],"new_ips":[],'
        '"timestamp":"2026-07-12T13:00:00Z"}'
    )
    event = TelemetryEvent.from_json(raw)
    assert event.message == "real-message"
    assert event.labels["message"] == "from-label"
    assert event.timestamp == datetime(2026, 7, 12, 13, 0, 0, tzinfo=UTC)


def test_rejects_whitespace_host() -> None:
    with pytest.raises(ValueError, match="host"):
        MetricSample(
            name="pingui_rtt_ms",
            value=1.0,
            host=" ",
            hop=1,
            labels={},
            timestamp=datetime.now(UTC),
        )


@pytest.mark.parametrize(
    ("factory", "kwargs"),
    [
        (
            lambda: MetricSample(
                name="",
                value=1.0,
                host="h",
                hop=1,
                labels={},
                timestamp=datetime.now(UTC),
            ),
            "name",
        ),
        (
            lambda: MetricSample(
                name="n",
                value=1.0,
                host="h",
                hop=0,
                labels={},
                timestamp=datetime.now(UTC),
            ),
            "hop",
        ),
        (
            lambda: TelemetryEvent(
                event="",
                host="h",
                labels={},
                timestamp=datetime.now(UTC),
            ),
            "event",
        ),
    ],
)
def test_validation(factory: object, kwargs: str) -> None:
    with pytest.raises(ValueError, match=kwargs):
        factory()  # type: ignore[operator]

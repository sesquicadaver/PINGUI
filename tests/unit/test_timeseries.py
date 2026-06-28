"""Time-series backend unit tests."""

from __future__ import annotations

import sys
import types
from datetime import UTC, datetime
from unittest.mock import MagicMock

import pytest

from pingui.models import HopNode, RouteSnapshot
from pingui.monitor.session_store import SessionStore
from pingui.persistence.timeseries.base import PingSample, RouteEvent, TimeSeriesConfigError
from pingui.persistence.timeseries.factory import create_timeseries_backend
from pingui.persistence.timeseries.memory import MemoryTimeSeriesBackend


def test_create_timeseries_backend_disabled() -> None:
    assert create_timeseries_backend(None) is None
    assert create_timeseries_backend("none") is None


def test_create_timeseries_backend_unknown() -> None:
    with pytest.raises(TimeSeriesConfigError, match="Unknown"):
        create_timeseries_backend("prometheus")


def test_create_influx_requires_config() -> None:
    with pytest.raises(TimeSeriesConfigError, match="InfluxDB"):
        create_timeseries_backend("influx")


def test_create_timescale_requires_dsn() -> None:
    with pytest.raises(TimeSeriesConfigError, match="Timescale"):
        create_timeseries_backend("timescale")


def _install_fake_influx(monkeypatch: pytest.MonkeyPatch) -> MagicMock:
    mock_write_api = MagicMock()
    mock_client = MagicMock()
    mock_client.write_api.return_value = mock_write_api

    influx_module = types.ModuleType("influxdb_client")
    influx_module.InfluxDBClient = MagicMock(return_value=mock_client)
    influx_module.Point = MagicMock(side_effect=lambda *args, **kwargs: MagicMock())
    influx_module.WritePrecision = MagicMock(NS="ns")

    write_api_module = types.ModuleType("influxdb_client.client.write_api")
    write_api_module.SYNCHRONOUS = "sync"

    monkeypatch.setitem(sys.modules, "influxdb_client", influx_module)
    monkeypatch.setitem(sys.modules, "influxdb_client.client.write_api", write_api_module)
    return mock_write_api


def test_influx_writes_ping_and_route(monkeypatch: pytest.MonkeyPatch) -> None:
    mock_write_api = _install_fake_influx(monkeypatch)
    backend = create_timeseries_backend(
        "influx",
        influx_url="http://localhost:8086",
        influx_token="token",
        influx_org="org",
        influx_bucket="bucket",
    )
    assert backend is not None
    ts = datetime(2026, 6, 26, 12, 0, tzinfo=UTC)
    backend.write_ping_samples(
        [PingSample("host", 1, "8.8.8.8", 12.5, ts)],
    )
    backend.write_route_event(
        RouteEvent("host", ["10.0.0.1", "8.8.8.8"], True, ts),
    )
    backend.close()
    assert mock_write_api.write.call_count == 2


def _install_fake_psycopg(monkeypatch: pytest.MonkeyPatch) -> MagicMock:
    mock_conn = MagicMock()
    mock_cursor = MagicMock()
    mock_cursor.execute.return_value = mock_cursor
    mock_cursor.fetchone.return_value = None
    mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

    psycopg_module = types.ModuleType("psycopg")
    psycopg_module.connect = MagicMock(return_value=mock_conn)
    monkeypatch.setitem(sys.modules, "psycopg", psycopg_module)
    return mock_cursor


def test_timescale_writes_ping_and_route(monkeypatch: pytest.MonkeyPatch) -> None:
    mock_cursor = _install_fake_psycopg(monkeypatch)
    backend = create_timeseries_backend(
        "timescale",
        timescale_dsn="postgresql://user:pass@localhost/pingui",
    )
    assert backend is not None
    ts = datetime(2026, 6, 26, 12, 0, tzinfo=UTC)
    backend.write_ping_samples(
        [PingSample("host", 2, "1.1.1.1", 5.0, ts)],
    )
    backend.write_route_event(
        RouteEvent("host", ["1.1.1.1"], False, ts),
    )
    backend.close()
    assert mock_cursor.executemany.called
    assert mock_cursor.execute.called


def test_session_store_forwards_to_memory_backend() -> None:
    backend = MemoryTimeSeriesBackend()
    store = SessionStore(["8.8.8.8"], timeseries=backend)
    snap = RouteSnapshot(
        target="8.8.8.8",
        target_ip="8.8.8.8",
        nodes=[
            HopNode(1, "10.0.0.1", 4.0, False),
            HopNode(2, "8.8.8.8", 8.0, False),
        ],
    )
    store.update_route("8.8.8.8", snap)
    store.append_ping_samples("8.8.8.8", snap)
    assert len(backend.ping_samples) == 2
    assert backend.ping_samples[0].hop_ip == "10.0.0.1"
    assert len(backend.route_events) == 1
    assert backend.route_events[0].route_ips == ["10.0.0.1", "8.8.8.8"]
    store.close()


def test_session_store_route_change_flag() -> None:
    backend = MemoryTimeSeriesBackend()
    store = SessionStore(["h"], timeseries=backend)
    first = RouteSnapshot(
        "h",
        "2.2.2.2",
        [HopNode(1, "1.1.1.1", 1.0, False), HopNode(2, "2.2.2.2", 2.0, False)],
    )
    second = RouteSnapshot(
        "h",
        "2.2.2.2",
        [HopNode(1, "9.9.9.9", 1.0, False), HopNode(2, "2.2.2.2", 2.0, False)],
    )
    store.update_route("h", first)
    store.update_route("h", second)
    assert backend.route_events[0].route_changed is False
    assert backend.route_events[1].route_changed is True
    store.close()

"""Worker unit tests."""

from __future__ import annotations

import pytest

from pingui.config import ConfigError
from pingui.monitor.worker import LightweightMonitorWorker


def test_worker_add_host() -> None:
    worker = LightweightMonitorWorker([], interval_seconds=60.0)
    added = worker.add_host("1.1.1.1")
    assert added == "1.1.1.1"
    assert "1.1.1.1" in worker.hosts()
    assert worker.enabled_hosts() == []


def test_worker_rejects_duplicate() -> None:
    worker = LightweightMonitorWorker(["8.8.8.8"], interval_seconds=60.0)
    with pytest.raises(ConfigError, match="Duplicate"):
        worker.add_host("8.8.8.8")


def test_worker_traces_only_enabled() -> None:
    worker = LightweightMonitorWorker(["8.8.8.8", "1.1.1.1"], interval_seconds=60.0)
    assert worker.enabled_hosts() == []
    worker.set_host_enabled("8.8.8.8", True)
    assert worker.enabled_hosts() == ["8.8.8.8"]


def test_worker_rename_preserves_enabled() -> None:
    worker = LightweightMonitorWorker(["8.8.8.8"], interval_seconds=60.0)
    worker.set_host_enabled("8.8.8.8", True)
    renamed = worker.rename_host("8.8.8.8", "1.1.1.1")
    assert renamed == "1.1.1.1"
    assert worker.enabled_hosts() == ["1.1.1.1"]


def test_worker_remove_host() -> None:
    worker = LightweightMonitorWorker(["8.8.8.8", "1.1.1.1"], interval_seconds=60.0)
    worker.remove_host("8.8.8.8")
    assert worker.hosts() == ["1.1.1.1"]

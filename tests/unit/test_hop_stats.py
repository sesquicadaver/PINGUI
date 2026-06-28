"""Unit tests for per-hop jitter and loss statistics."""

from __future__ import annotations

from pingui.models import HopNode, HopProbeStats
from pingui.monitor.hop_stats import (
    jitter_ms,
    loss_pct,
    record_hop_probe,
    summarize_hop_stats,
)
from pingui.monitor.session_store import SessionStore
from pingui.persistence.session_db import SessionDatabase


def test_jitter_requires_two_samples() -> None:
    assert jitter_ms([10.0]) is None
    assert jitter_ms([10.0, 14.0]) == 2.0


def test_loss_pct_counts_timeouts() -> None:
    stats = HopProbeStats()
    record_hop_probe(stats, HopNode(1, "1.1.1.1", 10.0, False))
    record_hop_probe(stats, HopNode.timeout(1))
    assert loss_pct(stats) == 50.0


def test_summarize_hop_stats() -> None:
    stats = HopProbeStats()
    record_hop_probe(stats, HopNode(2, "8.8.8.8", 10.0, False))
    record_hop_probe(stats, HopNode(2, "8.8.8.8", 14.0, False))
    summary = summarize_hop_stats(stats)
    assert summary is not None
    assert summary.jitter_ms == 2.0
    assert summary.loss_pct == 0.0


def test_session_store_records_hop_stats() -> None:
    from pingui.models import RouteSnapshot

    store = SessionStore(["h"])
    snapshot = RouteSnapshot(
        "h",
        "8.8.8.8",
        [HopNode(1, "10.0.0.1", 5.0, False), HopNode.timeout(2)],
    )
    store.append_ping_samples("h", snapshot)
    hop1 = store.hop_stats_summary("h", 1)
    hop2 = store.hop_stats_summary("h", 2)
    assert hop1 is not None
    assert hop1.loss_pct == 0.0
    assert hop2 is not None
    assert hop2.loss_pct == 100.0


def test_session_db_persists_hop_stats(tmp_path) -> None:
    from pathlib import Path

    from pingui.models import HostSessionData

    db_path = Path(tmp_path) / "session.db"
    db = SessionDatabase(db_path)
    stats = HopProbeStats(probes=3, successes=2, rtt_samples=[1.0, 2.0])
    data = HostSessionData(hop_stats={1: stats})
    db.save("host", data)
    loaded = db.load("host")
    db.close()
    assert loaded is not None
    assert loaded.hop_stats[1].probes == 3
    assert loaded.hop_stats[1].rtt_samples == [1.0, 2.0]

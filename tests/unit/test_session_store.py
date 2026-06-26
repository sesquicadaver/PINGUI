"""SessionStore unit tests."""

from __future__ import annotations

from pingui.models import HopNode, RouteSnapshot
from pingui.monitor.session_store import MAX_PING_SAMPLES, SessionStore


def _snapshot(host: str, ips: list[str]) -> RouteSnapshot:
    nodes = [
        HopNode(hop=i + 1, ip=ip, ping_ms=float(10 + i), is_timeout=False)
        for i, ip in enumerate(ips)
    ]
    return RouteSnapshot(target=host, target_ip=ips[-1], nodes=nodes)


def test_update_and_extract_route_ips() -> None:
    store = SessionStore(["a.example"])
    snap = _snapshot("a.example", ["10.0.0.1", "10.0.0.2"])
    store.update_route("a.example", snap)
    assert SessionStore.extract_route_ips(snap) == ["10.0.0.1", "10.0.0.2"]
    assert store.get("a.example").current_route[0].ip == "10.0.0.1"


def test_ping_history_trim() -> None:
    store = SessionStore(["h"])
    for i in range(MAX_PING_SAMPLES + 5):
        snap = RouteSnapshot(
            target="h",
            target_ip="1.1.1.1",
            nodes=[HopNode(hop=1, ip="1.1.1.1", ping_ms=float(i), is_timeout=False)],
        )
        store.append_ping_samples("h", snap)
    samples = store.get("h").ping_history["1.1.1.1"]
    assert len(samples) == MAX_PING_SAMPLES
    assert samples[0] == 5.0
    assert samples[-1] == float(MAX_PING_SAMPLES + 4)


def test_avg_ping_none_without_samples() -> None:
    store = SessionStore(["x"])
    assert store.avg_ping("x", "9.9.9.9") is None


def test_skips_timeout_nodes_in_history() -> None:
    store = SessionStore(["x"])
    snap = RouteSnapshot(
        target="x",
        target_ip="1.1.1.1",
        nodes=[HopNode.timeout(1), HopNode(hop=2, ip="1.1.1.1", ping_ms=20.0)],
    )
    store.append_ping_samples("x", snap)
    assert "*" not in store.get("x").ping_history
    assert store.avg_ping("x", "1.1.1.1") == 20.0


def test_previous_route_on_change() -> None:
    store = SessionStore(["h"])
    first = _snapshot("h", ["10.0.0.1", "10.0.0.2"])
    second = _snapshot("h", ["10.0.0.9", "10.0.0.2"])
    store.update_route("h", first)
    assert store.get("h").previous_route == []
    store.update_route("h", second)
    assert len(store.get("h").previous_route) == 2
    assert store.get("h").previous_route[0].ip == "10.0.0.1"


def test_inactive_route_fills_timeout_from_last_known() -> None:
    store = SessionStore(["h"])
    snap_known = RouteSnapshot(
        target="h",
        target_ip="2.2.2.2",
        nodes=[
            HopNode(1, "1.1.1.1", 10.0),
            HopNode(2, "2.2.2.2", 20.0),
        ],
    )
    snap_timeout = RouteSnapshot(
        target="h",
        target_ip="2.2.2.2",
        nodes=[HopNode(1, "1.1.1.1", 10.0), HopNode.timeout(2)],
    )
    snap_changed = _snapshot("h", ["9.9.9.9", "2.2.2.2"])
    store.update_route("h", snap_known)
    store.update_route("h", snap_timeout)
    store.update_route("h", snap_changed)
    inactive = store.inactive_route("h")
    assert inactive[1].ip == "2.2.2.2"


def test_remove_host() -> None:
    store = SessionStore(["a.example", "b.example"])
    store.remove_host("a.example")
    assert store.hosts() == ["b.example"]


def test_add_host_respects_limit() -> None:
    store = SessionStore(["only.example"])
    added = store.add_host("8.8.8.8")
    assert added == "8.8.8.8"
    assert "8.8.8.8" in store.hosts()


def test_set_enabled_flag() -> None:
    store = SessionStore(["h.example"])
    assert not store.get("h.example").enabled
    store.set_enabled("h.example", True)
    assert store.get("h.example").enabled

"""Contract test: worker route change logic with session store."""

from __future__ import annotations

from pingui.models import HopNode, RouteSnapshot
from pingui.monitor.route_change import detect_route_change
from pingui.monitor.session_store import SessionStore


def test_worker_store_route_change_flow() -> None:
    store = SessionStore(["host"])
    last: list[str] = []

    def process(snapshot: RouteSnapshot) -> bool:
        store.update_route("host", snapshot)
        store.append_ping_samples("host", snapshot)
        current = SessionStore.extract_route_ips(snapshot)
        changed, old, new = detect_route_change(last, current)
        if changed:
            last.clear()
            last.extend(current)
            return True
        last.clear()
        last.extend(current)
        return False

    snap1 = RouteSnapshot(
        "host",
        "2.2.2.2",
        [HopNode(1, "1.1.1.1", 10.0), HopNode(2, "2.2.2.2", 20.0)],
    )
    assert process(snap1) is False

    snap2 = RouteSnapshot(
        "host",
        "2.2.2.2",
        [HopNode(1, "9.9.9.9", 15.0), HopNode(2, "2.2.2.2", 25.0)],
    )
    assert process(snap2) is True
    assert store.avg_ping("host", "9.9.9.9") == 15.0

"""Graph canvas unit tests."""

from __future__ import annotations

import matplotlib

matplotlib.use("Agg")

from pingui.models import HopNode
from pingui.ui.graph_canvas import GraphCanvas, ping_color


def test_ping_color_thresholds() -> None:
    assert ping_color(10.0, False) == "#98fb98"
    assert ping_color(100.0, False) == "#ffa500"
    assert ping_color(200.0, False) == "#ff6347"
    assert ping_color(None, True) == "#d3d3d3"


def test_render_empty_route() -> None:
    canvas = GraphCanvas()
    canvas.render_route([], avg_ping_fn=lambda _ip: None)
    assert canvas._ax.get_title() == "Немає даних маршруту"


def test_render_route_with_hops() -> None:
    canvas = GraphCanvas()
    route = [
        HopNode(1, "10.0.0.1", 12.0),
        HopNode.timeout(2),
        HopNode(3, "8.8.8.8", 20.0),
    ]
    canvas.render_route(route, avg_ping_fn=lambda ip: 15.0 if ip == "10.0.0.1" else 25.0)

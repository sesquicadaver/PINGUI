"""Graph canvas unit tests."""

from __future__ import annotations

import matplotlib

matplotlib.use("Agg")

from pingui.geoip import configure as configure_geoip
from pingui.models import HopNode
from pingui.ui.graph_canvas import (
    GraphCanvas,
    _box_height,
    _chain_nodes,
    _column_layouts,
    _columns_separated,
    _node_label,
    _routes_differ,
    ping_color,
)


def test_ping_color_thresholds() -> None:
    assert ping_color(10.0, False) == "#98fb98"
    assert ping_color(100.0, False) == "#ffa500"
    assert ping_color(200.0, False) == "#ff6347"
    assert ping_color(None, True) == "#d3d3d3"


def test_node_label_compact() -> None:
    configure_geoip(enabled=True)
    node = HopNode(3, "192.168.0.1", 12.0)
    assert _node_label(node, lambda _ip: 12.0) == "Hop 3\n192.168.0.1\nLAN\n12 ms"
    assert _node_label(HopNode.timeout(2), lambda _ip: None) == "Hop 2\n*"
    assert _node_label(HopNode(4, "8.8.8.8", 5.0), lambda _ip: 5.0) == "Hop 4\n8.8.8.8\nUS\n5 ms"
    configure_geoip(enabled=False)
    assert _node_label(HopNode(4, "8.8.8.8", 5.0), lambda _ip: 5.0) == "Hop 4\n8.8.8.8\n5 ms"
    configure_geoip(enabled=True)


def test_dual_columns_do_not_overlap() -> None:
    inactive_col, active_col = _column_layouts(show_previous=True)
    assert inactive_col is not None
    inactive_nodes, _ = _chain_nodes(
        [HopNode(1, "9.9.9.9", 1.0)],
        lambda _ip: None,
        column=inactive_col,
        inactive=True,
        id_prefix="prev",
    )
    active_nodes, _ = _chain_nodes(
        [HopNode(1, "1.1.1.1", 1.0)],
        lambda _ip: None,
        column=active_col,
        inactive=False,
        id_prefix="act",
    )
    assert _columns_separated(inactive_nodes, active_nodes)


def test_render_empty_route() -> None:
    canvas = GraphCanvas()
    canvas.render_route([], avg_ping_fn=lambda _ip: None)
    assert not canvas._ax.get_title()
    assert len(canvas._ax.patches) == 0


def test_render_route_draws_boxes() -> None:
    canvas = GraphCanvas()
    route = [
        HopNode(1, "10.0.0.1", 12.0),
        HopNode.timeout(2),
        HopNode(3, "8.8.8.8", 20.0),
    ]
    canvas.render_route(route, avg_ping_fn=lambda ip: 15.0 if ip == "10.0.0.1" else 25.0)
    assert len(canvas._ax.patches) >= len(route) + 1


def test_vertical_layout_top_to_bottom() -> None:
    _, active_col = _column_layouts(show_previous=False)
    route = [HopNode(1, "10.0.0.1", 1.0), HopNode(2, "10.0.0.2", 2.0)]
    nodes, _ = _chain_nodes(
        route,
        lambda _ip: None,
        column=active_col,
        inactive=False,
        id_prefix="act",
    )
    ys = [n.y for n in nodes]
    assert ys[0] == max(ys)
    assert ys == sorted(ys, reverse=True)


def test_routes_differ() -> None:
    current = [HopNode(1, "1.1.1.1", 10.0)]
    previous = [HopNode(1, "9.9.9.9", 15.0)]
    assert _routes_differ(current, previous)
    assert not _routes_differ(current, list(current))


def test_previous_route_draws_extra_track() -> None:
    canvas = GraphCanvas()
    current = [HopNode(1, "1.1.1.1", 10.0), HopNode(2, "2.2.2.2", 20.0)]
    previous = [HopNode(1, "9.9.9.9", 15.0), HopNode(2, "2.2.2.2", 25.0)]
    canvas.render_route(current, avg_ping_fn=lambda _ip: 12.0, previous_route=previous)
    only_current = GraphCanvas()
    only_current.render_route(current, avg_ping_fn=lambda _ip: 12.0)
    assert len(canvas._ax.patches) > len(only_current._ax.patches)


def test_box_height_grows_with_lines() -> None:
    assert _box_height("a") < _box_height("Hop 1\n1.1.1.1\n10 ms")

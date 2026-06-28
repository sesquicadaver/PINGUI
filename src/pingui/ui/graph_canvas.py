"""Matplotlib route graph canvas."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

import matplotlib.pyplot as plt
from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch

from pingui.geoip import country_code_for_ip
from pingui.models import TIMEOUT_IP, HopNode

LOCALHOST_ID = "localhost"
INACTIVE_NODE = "#b0b0b0"
INACTIVE_EDGE = "#c8c8c8"
ORIGIN_COLOR = "#add8e6"
MARGIN_X = 0.03
MARGIN_Y = 0.03
COL_GAP = 0.04
TEXT_PAD = 0.012


def ping_color(avg_ms: float | None, is_timeout: bool) -> str:
    """Map average RTT to node fill color."""
    if is_timeout or avg_ms is None:
        return "#d3d3d3"
    if avg_ms < 50:
        return "#98fb98"
    if avg_ms < 150:
        return "#ffa500"
    return "#ff6347"


@dataclass(frozen=True, slots=True)
class _GraphNode:
    """Internal node for layout and drawing."""

    node_id: str
    label: str
    color: str
    x: float
    y: float
    width: float
    height: float


@dataclass(frozen=True, slots=True)
class _ColumnLayout:
    """Horizontal placement for one route column (normalized 0–1)."""

    center_x: float
    width: float


def _hop_node_id(hop: int, ip: str, is_timeout: bool) -> str:
    if is_timeout or ip == TIMEOUT_IP:
        return f"hop_{hop}_timeout"
    return f"hop_{hop}_{ip}"


def _node_color(
    node: HopNode,
    avg_ping_fn: Callable[[str], float | None],
    *,
    inactive: bool,
) -> str:
    if inactive:
        return INACTIVE_NODE
    if node.is_timeout or node.ip == TIMEOUT_IP:
        return ping_color(None, True)
    avg = avg_ping_fn(node.ip)
    return ping_color(avg if avg is not None else node.ping_ms, False)


def _country_line(ip: str) -> str:
    code = country_code_for_ip(ip)
    return f"\n{code}" if code else ""


def _node_label(
    node: HopNode,
    avg_ping_fn: Callable[[str], float | None],
) -> str:
    if node.is_timeout or node.ip == TIMEOUT_IP:
        return f"Hop {node.hop}\n*"
    country = _country_line(node.ip)
    avg = avg_ping_fn(node.ip)
    if avg is not None:
        return f"Hop {node.hop}\n{node.ip}{country}\n{int(avg)} ms"
    if node.ping_ms is not None:
        return f"Hop {node.hop}\n{node.ip}{country}\n{int(node.ping_ms)} ms"
    if country:
        return f"Hop {node.hop}\n{node.ip}{country}"
    return f"Hop {node.hop}\n{node.ip}"


def _box_height(label: str) -> float:
    line_count = max(1, len(label.split("\n")))
    return 0.038 + 0.024 * line_count


def _column_layouts(show_previous: bool) -> tuple[_ColumnLayout | None, _ColumnLayout]:
    """Split canvas in half when previous route is shown."""
    if show_previous:
        col_width = (1.0 - 2 * MARGIN_X - COL_GAP) / 2.0
        inactive = _ColumnLayout(
            center_x=MARGIN_X + col_width / 2.0,
            width=col_width,
        )
        active = _ColumnLayout(
            center_x=MARGIN_X + col_width + COL_GAP + col_width / 2.0,
            width=col_width,
        )
        return inactive, active
    col_width = 1.0 - 2 * MARGIN_X
    return None, _ColumnLayout(center_x=0.5, width=col_width)


def _layout_y_for_chain(chain_len: int) -> list[float]:
    """Even vertical spacing top → bottom in normalized coordinates."""
    if chain_len <= 0:
        return []
    if chain_len == 1:
        return [0.5]
    top = 1.0 - MARGIN_Y
    bottom = MARGIN_Y
    step = (top - bottom) / (chain_len - 1)
    return [top - index * step for index in range(chain_len)]


def _chain_nodes(
    route: list[HopNode],
    avg_ping_fn: Callable[[str], float | None],
    *,
    column: _ColumnLayout,
    inactive: bool,
    id_prefix: str,
) -> tuple[list[_GraphNode], list[tuple[str, str]]]:
    """Build one vertical track inside its column."""
    nodes: list[_GraphNode] = []
    edges: list[tuple[str, str]] = []
    y_coords = _layout_y_for_chain(len(route) + 1)
    y_index = 0

    pc_id = f"{id_prefix}_{LOCALHOST_ID}"
    pc_label = "Ваш ПК"
    nodes.append(
        _GraphNode(
            node_id=pc_id,
            label=pc_label,
            color=INACTIVE_NODE if inactive else ORIGIN_COLOR,
            x=column.center_x,
            y=y_coords[y_index],
            width=column.width,
            height=_box_height(pc_label),
        )
    )
    prev_id = pc_id
    y_index += 1

    for node in route:
        label = _node_label(node, avg_ping_fn)
        base_id = _hop_node_id(node.hop, node.ip, node.is_timeout)
        node_id = f"{id_prefix}_{base_id}"
        nodes.append(
            _GraphNode(
                node_id=node_id,
                label=label,
                color=_node_color(node, avg_ping_fn, inactive=inactive),
                x=column.center_x,
                y=y_coords[y_index],
                width=column.width,
                height=_box_height(label),
            )
        )
        edges.append((prev_id, node_id))
        prev_id = node_id
        y_index += 1

    return nodes, edges


def _routes_differ(
    current: list[HopNode],
    previous: list[HopNode],
) -> bool:
    if not previous:
        return False
    return [n.ip for n in current] != [n.ip for n in previous]


def _node_by_id(nodes: list[_GraphNode]) -> dict[str, _GraphNode]:
    return {node.node_id: node for node in nodes}


def _columns_separated(inactive_nodes: list[_GraphNode], active_nodes: list[_GraphNode]) -> bool:
    if not inactive_nodes or not active_nodes:
        return True
    inactive_right = max(n.x + n.width / 2 for n in inactive_nodes)
    active_left = min(n.x - n.width / 2 for n in active_nodes)
    return inactive_right < active_left


def _draw_box(ax: plt.Axes, node: _GraphNode) -> None:
    left = node.x - node.width / 2
    bottom = node.y - node.height / 2
    patch = FancyBboxPatch(
        (left, bottom),
        node.width,
        node.height,
        boxstyle="square,pad=0.005",
        facecolor=node.color,
        edgecolor="#555555",
        linewidth=0.7,
        zorder=2,
    )
    ax.add_patch(patch)
    if node.label:
        ax.text(
            left + TEXT_PAD,
            node.y,
            node.label,
            ha="left",
            va="center",
            fontsize=6,
            family="monospace",
            zorder=3,
        )


def _draw_edge(
    ax: plt.Axes,
    src: _GraphNode,
    dst: _GraphNode,
    *,
    inactive: bool,
) -> None:
    start = (src.x, src.y - src.height / 2)
    end = (dst.x, dst.y + dst.height / 2)
    arrow = FancyArrowPatch(
        start,
        end,
        arrowstyle="-|>",
        mutation_scale=8,
        color=INACTIVE_EDGE if inactive else "#666666",
        linewidth=0.9,
        linestyle="dashed" if inactive else "solid",
        shrinkA=0,
        shrinkB=0,
        zorder=1,
    )
    ax.add_patch(arrow)


class GraphCanvas(FigureCanvas):  # type: ignore[misc]
    """Qt widget rendering route chain top-to-bottom."""

    def __init__(self, figure: Figure | None = None) -> None:
        self._figure = figure or plt.figure(figsize=(4, 7))
        self._ax = self._figure.add_subplot(111)
        super().__init__(self._figure)

    def render_route(
        self,
        route: list[HopNode],
        avg_ping_fn: Callable[[str], float | None],
        previous_route: list[HopNode] | None = None,
    ) -> None:
        """Draw hop boxes top-down; previous route in left half if changed."""
        self._ax.clear()
        if not route:
            self._ax.axis("off")
            self.draw()
            return

        prev = previous_route or []
        show_previous = _routes_differ(route, prev)
        inactive_col, active_col = _column_layouts(show_previous)

        inactive_nodes: list[_GraphNode] = []
        inactive_edges: list[tuple[str, str]] = []
        if show_previous and inactive_col is not None:
            inactive_nodes, inactive_edges = _chain_nodes(
                prev,
                avg_ping_fn,
                column=inactive_col,
                inactive=True,
                id_prefix="prev",
            )

        active_nodes, active_edges = _chain_nodes(
            route,
            avg_ping_fn,
            column=active_col,
            inactive=False,
            id_prefix="act",
        )

        inactive_map = _node_by_id(inactive_nodes)
        active_map = _node_by_id(active_nodes)

        for src_id, dst_id in inactive_edges:
            _draw_edge(
                self._ax,
                inactive_map[src_id],
                inactive_map[dst_id],
                inactive=True,
            )
        for src_id, dst_id in active_edges:
            _draw_edge(
                self._ax,
                active_map[src_id],
                active_map[dst_id],
                inactive=False,
            )

        for node in inactive_nodes + active_nodes:
            _draw_box(self._ax, node)

        self._ax.set_xlim(0.0, 1.0)
        self._ax.set_ylim(0.0, 1.0)
        self._ax.axis("off")
        self._figure.subplots_adjust(left=0.02, right=0.98, top=0.98, bottom=0.02)
        self.draw()

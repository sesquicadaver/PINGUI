"""Matplotlib + NetworkX route graph canvas."""

from __future__ import annotations

from collections.abc import Callable

import matplotlib.pyplot as plt
import networkx as nx
from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
from matplotlib.figure import Figure

from pingui.models import TIMEOUT_IP, HopNode


def ping_color(avg_ms: float | None, is_timeout: bool) -> str:
    """Map average RTT to node fill color."""
    if is_timeout or avg_ms is None:
        return "#d3d3d3"
    if avg_ms < 50:
        return "#98fb98"
    if avg_ms < 150:
        return "#ffa500"
    return "#ff6347"


class GraphCanvas(FigureCanvas):  # type: ignore[misc]
    """Qt widget rendering topological route graph."""

    def __init__(self, figure: Figure | None = None) -> None:
        self._figure = figure or plt.figure(figsize=(6, 4))
        self._ax = self._figure.add_subplot(111)
        super().__init__(self._figure)

    def render_route(
        self,
        route: list[HopNode],
        avg_ping_fn: Callable[[str], float | None],
    ) -> None:
        """Draw directed graph for route hops with ping coloring."""
        self._ax.clear()
        if not route:
            self._ax.set_title("Немає даних маршруту")
            self.draw()
            return

        graph: nx.DiGraph = nx.DiGraph()
        prev = "localhost"
        graph.add_node(prev, label="Ваш ПК", color="#add8e6")

        for node in route:
            if node.is_timeout or node.ip == TIMEOUT_IP:
                node_id = f"star_{node.hop}"
                label = f"Hop {node.hop}\n*"
                color = ping_color(None, True)
            else:
                node_id = node.ip
                avg = avg_ping_fn(node.ip)
                if avg is not None:
                    label = f"Hop {node.hop}\n{node.ip}\n{int(avg)} ms"
                elif node.ping_ms is not None:
                    label = f"Hop {node.hop}\n{node.ip}\n{int(node.ping_ms)} ms"
                else:
                    label = f"Hop {node.hop}\n{node.ip}"
                color = ping_color(avg if avg is not None else node.ping_ms, False)

            graph.add_node(node_id, label=label, color=color)
            graph.add_edge(prev, node_id)
            prev = node_id

        pos = nx.spring_layout(graph, seed=42)
        labels = nx.get_node_attributes(graph, "label")
        colors = [graph.nodes[n]["color"] for n in graph.nodes()]

        nx.draw(
            graph,
            pos,
            ax=self._ax,
            with_labels=True,
            labels=labels,
            node_color=colors,
            node_size=2200,
            font_size=7,
            font_weight="bold",
            edge_color="#888888",
            arrows=True,
        )
        self.draw()

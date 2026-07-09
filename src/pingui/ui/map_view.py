"""Qt widget displaying folium route maps."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from PyQt6.QtCore import QUrl
from PyQt6.QtWidgets import QLabel, QVBoxLayout, QWidget

from pingui.geoip.map_builder import build_route_map_html
from pingui.models import HopNode

if TYPE_CHECKING:
    from PyQt6.QtWebEngineWidgets import QWebEngineView


class RouteMapView(QWidget):
    """Separate view tab: folium map rendered in QWebEngineView when available."""

    def __init__(self, *, enabled: bool = True) -> None:
        super().__init__()
        self._enabled = enabled
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        self._web: QWebEngineView | None = None
        self._placeholder = QLabel()
        self._placeholder.setWordWrap(True)

        if not enabled:
            self._placeholder.setText("Карта вимкнена (--no-geo-map).")
            layout.addWidget(self._placeholder)
            return

        web_engine: Any
        try:
            from PyQt6.QtWebEngineWidgets import QWebEngineView as _WebEngineView

            web_engine = _WebEngineView()
            self._web = web_engine
            layout.addWidget(web_engine)
        except ImportError:
            self._placeholder.setText(
                "Для вкладки «Карта» потрібен пакет PyQt6-WebEngine.\n"
                "Перевстановіть залежності: ./pingui.sh --deploy"
            )
            layout.addWidget(self._placeholder)

    def render_route(self, route: list[HopNode], *, target: str) -> None:
        """Update map HTML for the active host route."""
        if not self._enabled or self._web is None:
            return
        html = build_route_map_html(route, target=target)
        base = QUrl("https://pingui.local/")
        self._web.setHtml(html, baseUrl=base)

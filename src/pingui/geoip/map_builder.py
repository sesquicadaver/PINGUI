"""Build folium HTML maps for monitored routes."""

from __future__ import annotations

import folium

from pingui.geoip.coordinates import HopGeoLocation, hop_geo_location
from pingui.models import TIMEOUT_IP, HopNode

DEFAULT_CENTER = (20.0, 0.0)
DEFAULT_ZOOM = 2


def route_geo_locations(route: list[HopNode]) -> list[HopGeoLocation]:
    """Collect mappable hop locations in trace order."""
    locations: list[HopGeoLocation] = []
    for node in route:
        if node.is_timeout or node.ip == TIMEOUT_IP:
            continue
        loc = hop_geo_location(node.ip, node.hop)
        if loc is not None:
            locations.append(loc)
    return locations


def build_route_map_html(route: list[HopNode], *, target: str) -> str:
    """Render an interactive folium map for the given hop chain."""
    locations = route_geo_locations(route)
    if not locations:
        folium_map = folium.Map(location=DEFAULT_CENTER, zoom_start=DEFAULT_ZOOM)
        folium.Marker(
            location=DEFAULT_CENTER,
            popup=f"Немає публічних hop для карти ({target})",
            icon=folium.Icon(color="gray", icon="info-sign"),
        ).add_to(folium_map)
        return _map_html(folium_map)

    center_lat = sum(loc.lat for loc in locations) / len(locations)
    center_lon = sum(loc.lon for loc in locations) / len(locations)
    folium_map = folium.Map(location=[center_lat, center_lon], zoom_start=4)

    path = [[loc.lat, loc.lon] for loc in locations]
    if len(path) > 1:
        folium.PolyLine(
            path,
            color="#3366cc",
            weight=3,
            opacity=0.85,
            tooltip=f"Маршрут до {target}",
        ).add_to(folium_map)

    for node, loc in _paired_route_locations(route, locations):
        ping_line = ""
        if node.ping_ms is not None:
            ping_line = f"\n{int(node.ping_ms)} ms"
        popup = f"Hop {loc.hop}\n{loc.ip}\n{loc.country}{ping_line}"
        folium.CircleMarker(
            location=[loc.lat, loc.lon],
            radius=8,
            popup=popup,
            color="#333333",
            fill=True,
            fill_color="#ffa500",
            fill_opacity=0.9,
            tooltip=f"Hop {loc.hop}: {loc.ip}",
        ).add_to(folium_map)

    folium_map.get_root().html.add_child(folium.Element(f"<title>PINGUI — {target}</title>"))
    return _map_html(folium_map)


def _paired_route_locations(
    route: list[HopNode],
    locations: list[HopGeoLocation],
) -> list[tuple[HopNode, HopGeoLocation]]:
    by_hop = {loc.hop: loc for loc in locations}
    paired: list[tuple[HopNode, HopGeoLocation]] = []
    for node in route:
        loc = by_hop.get(node.hop)
        if loc is not None:
            paired.append((node, loc))
    return paired


def _map_html(folium_map: folium.Map) -> str:
    rendered = folium_map.get_root().render()
    return str(rendered)

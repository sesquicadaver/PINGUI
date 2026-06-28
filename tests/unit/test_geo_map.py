"""Folium geo-map unit tests."""

from __future__ import annotations

import pytest

from pingui.geoip import configure as configure_geoip
from pingui.geoip.coordinates import hop_geo_location
from pingui.geoip.map_builder import build_route_map_html, route_geo_locations
from pingui.models import HopNode


@pytest.fixture(autouse=True)
def _geoip_enabled() -> None:
    configure_geoip(enabled=True)
    yield
    configure_geoip(enabled=True)


def test_hop_geo_location_public_dns() -> None:
    loc = hop_geo_location("8.8.8.8", 3)
    assert loc is not None
    assert loc.country == "US"
    assert loc.hop == 3


def test_hop_geo_location_private_is_none() -> None:
    assert hop_geo_location("10.0.0.1", 1) is None


def test_route_geo_locations_skips_timeout() -> None:
    route = [HopNode(1, "10.0.0.1", 1.0), HopNode.timeout(2), HopNode(3, "8.8.8.8", 5.0)]
    locations = route_geo_locations(route)
    assert len(locations) == 1
    assert locations[0].ip == "8.8.8.8"


def test_build_route_map_html_contains_leaflet() -> None:
    route = [HopNode(1, "8.8.8.8", 12.0)]
    html = build_route_map_html(route, target="8.8.8.8")
    assert "leaflet" in html.lower()
    assert "8.8.8.8" in html


def test_build_route_map_html_empty_route() -> None:
    html = build_route_map_html([], target="example.com")
    assert "leaflet" in html.lower()
    assert "немає" in html.lower() or "Немає" in html

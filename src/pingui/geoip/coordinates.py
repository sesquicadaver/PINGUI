"""Rough geographic coordinates for hop IPs (country centroids + jitter)."""

from __future__ import annotations

from dataclasses import dataclass

from pingui.geoip.country import LAN_TAG, country_code_for_ip

# ISO 3166-1 alpha-2 → approximate country centroid (lat, lon).
COUNTRY_CENTROIDS: dict[str, tuple[float, float]] = {
    "AU": (-25.2744, 133.7751),
    "CN": (35.8617, 104.1954),
    "DE": (51.1657, 10.4515),
    "FR": (46.2276, 2.2137),
    "GB": (55.3781, -3.4360),
    "IE": (53.4129, -8.2439),
    "JP": (36.2048, 138.2529),
    "PL": (51.9194, 19.1451),
    "RU": (61.5240, 105.3188),
    "US": (37.0902, -95.7129),
}


@dataclass(frozen=True, slots=True)
class HopGeoLocation:
    """Map marker for one hop."""

    hop: int
    ip: str
    lat: float
    lon: float
    country: str


def hop_geo_location(ip: str, hop: int) -> HopGeoLocation | None:
    """
    Resolve hop IP to a rough map point.

    Private/reserved/unknown IPs return None. Same-country hops are offset slightly
    so markers do not fully overlap on the folium map.
    """
    code = country_code_for_ip(ip)
    if code is None or code == LAN_TAG:
        return None
    centroid = COUNTRY_CENTROIDS.get(code)
    if centroid is None:
        return None
    base_lat, base_lon = centroid
    jitter_lat = (hop % 5) * 0.35
    jitter_lon = (hop % 7) * 0.45
    return HopGeoLocation(
        hop=hop,
        ip=ip,
        lat=base_lat + jitter_lat,
        lon=base_lon + jitter_lon,
        country=code,
    )

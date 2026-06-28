"""Offline country lookup for hop IP addresses."""

from __future__ import annotations

import ipaddress
from pathlib import Path

import yaml

DEFAULT_HINTS_PATH = Path("config/geoip_hints.yaml")
LAN_TAG = "LAN"

_enabled = True
_lookup: CountryLookup | None = None


class GeoIpHintsError(ValueError):
    """Raised when GeoIP hints YAML is invalid."""


class CountryLookup:
    """Longest-prefix match against bundled or custom prefix hints."""

    def __init__(self, prefixes: list[tuple[ipaddress.IPv4Network, str]]) -> None:
        self._prefixes = sorted(prefixes, key=lambda item: item[0].prefixlen, reverse=True)

    @classmethod
    def load(cls, path: Path) -> CountryLookup:
        """Load prefix hints from YAML; fall back to embedded defaults if missing."""
        if path.is_file():
            prefixes = _parse_hints_yaml(path.read_text(encoding="utf-8"))
        else:
            prefixes = _embedded_prefixes()
        return cls(prefixes)

    def lookup(self, ip: str) -> str | None:
        """Return ISO country code, LAN for private space, or None if unknown."""
        try:
            addr = ipaddress.IPv4Address(ip)
        except ipaddress.AddressValueError:
            return None
        if addr.is_private or addr.is_loopback or addr.is_link_local:
            return LAN_TAG
        if addr.is_multicast or addr.is_reserved or addr.is_unspecified:
            return None
        for network, code in self._prefixes:
            if addr in network:
                return code
        return None


def configure(*, enabled: bool = True, hints_path: Path | None = None) -> None:
    """Enable or disable GeoIP labels and optionally override hints file path."""
    global _enabled, _lookup
    _enabled = enabled
    if not enabled:
        _lookup = None
        return
    _lookup = CountryLookup.load(hints_path or DEFAULT_HINTS_PATH)


def country_code_for_ip(ip: str) -> str | None:
    """Return country hint for an IP when GeoIP is enabled."""
    if not _enabled or _lookup is None:
        return None
    return _lookup.lookup(ip)


def _parse_hints_yaml(payload: str) -> list[tuple[ipaddress.IPv4Network, str]]:
    raw = yaml.safe_load(payload)
    if not isinstance(raw, dict):
        msg = "GeoIP hints YAML root must be a mapping"
        raise GeoIpHintsError(msg)
    entries = raw.get("prefixes")
    if entries is None:
        return _embedded_prefixes()
    if not isinstance(entries, dict):
        msg = "GeoIP hints 'prefixes' must be a mapping"
        raise GeoIpHintsError(msg)
    prefixes: list[tuple[ipaddress.IPv4Network, str]] = []
    for cidr, code in entries.items():
        if not isinstance(cidr, str) or not isinstance(code, str):
            msg = f"Invalid prefix entry: {cidr!r} -> {code!r}"
            raise GeoIpHintsError(msg)
        normalized = code.strip().upper()
        if len(normalized) != 2 or not normalized.isalpha():
            msg = f"Country code must be ISO alpha-2 or LAN tag: {code!r}"
            raise GeoIpHintsError(msg)
        try:
            network = ipaddress.IPv4Network(cidr.strip(), strict=False)
        except ValueError as exc:
            msg = f"Invalid CIDR in GeoIP hints: {cidr!r}"
            raise GeoIpHintsError(msg) from exc
        prefixes.append((network, normalized))
    return prefixes


def _embedded_prefixes() -> list[tuple[ipaddress.IPv4Network, str]]:
    """Minimal defaults when no hints file is present."""
    return [
        (ipaddress.IPv4Network("8.8.8.0/24"), "US"),
        (ipaddress.IPv4Network("8.8.4.0/24"), "US"),
        (ipaddress.IPv4Network("1.1.1.0/24"), "AU"),
        (ipaddress.IPv4Network("1.0.0.0/24"), "AU"),
    ]

"""Offline country lookup for hop IP addresses."""

from __future__ import annotations

import ipaddress
from dataclasses import dataclass
from pathlib import Path

import yaml

DEFAULT_HINTS_PATH = Path("config/geoip_hints.yaml")
LAN_TAG = "LAN"
_doc_v6 = ipaddress.IPv6Network("2001:db8::/32")

_enabled = True
_lookup: CountryLookup | None = None


class GeoIpHintsError(ValueError):
    """Raised when GeoIP hints YAML is invalid."""


@dataclass(frozen=True, slots=True)
class _PrefixTables:
    v4: list[tuple[ipaddress.IPv4Network, str]]
    v6: list[tuple[ipaddress.IPv6Network, str]]


class CountryLookup:
    """Longest-prefix match against bundled or custom prefix hints."""

    def __init__(self, tables: _PrefixTables) -> None:
        self._v4 = sorted(tables.v4, key=lambda item: item[0].prefixlen, reverse=True)
        self._v6 = sorted(tables.v6, key=lambda item: item[0].prefixlen, reverse=True)

    @classmethod
    def load(cls, path: Path) -> CountryLookup:
        """Load prefix hints from YAML; fall back to embedded defaults if missing."""
        if path.is_file():
            tables = _parse_hints_yaml(path.read_text(encoding="utf-8"))
        else:
            tables = _embedded_prefixes()
        return cls(tables)

    def lookup(self, ip: str) -> str | None:
        """Return ISO country code, LAN for private space, or None if unknown."""
        try:
            addr = ipaddress.IPv4Address(ip)
        except ipaddress.AddressValueError:
            pass
        else:
            if addr.is_private or addr.is_loopback or addr.is_link_local:
                return LAN_TAG
            if addr.is_multicast or addr.is_reserved or addr.is_unspecified:
                return None
            for v4_net, code in self._v4:
                if addr in v4_net:
                    return code
            return None

        try:
            addr6 = ipaddress.IPv6Address(ip)
        except ipaddress.AddressValueError:
            return None

        if (
            addr6.is_loopback
            or addr6.is_link_local
            or (addr6.is_private and addr6 not in _doc_v6)
        ):
            return LAN_TAG
        if addr6.is_multicast or addr6.is_reserved or addr6.is_unspecified:
            return None
        for v6_net, code in self._v6:
            if addr6 in v6_net:
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


def _parse_country_code(code: str, *, context: str) -> str:
    normalized = code.strip().upper()
    if len(normalized) != 2 or not normalized.isalpha():
        msg = f"Country code must be ISO alpha-2 or LAN tag: {context}"
        raise GeoIpHintsError(msg)
    return normalized


def _parse_v4_mapping(
    entries: object,
) -> list[tuple[ipaddress.IPv4Network, str]]:
    if not isinstance(entries, dict):
        msg = "GeoIP hints 'prefixes' must be a mapping"
        raise GeoIpHintsError(msg)
    prefixes: list[tuple[ipaddress.IPv4Network, str]] = []
    for cidr, code in entries.items():
        if not isinstance(cidr, str) or not isinstance(code, str):
            msg = f"Invalid prefix entry: {cidr!r} -> {code!r}"
            raise GeoIpHintsError(msg)
        normalized = _parse_country_code(code, context=code)
        try:
            network = ipaddress.IPv4Network(cidr.strip(), strict=False)
        except ValueError as exc:
            msg = f"Invalid CIDR in GeoIP hints: {cidr!r}"
            raise GeoIpHintsError(msg) from exc
        prefixes.append((network, normalized))
    return prefixes


def _parse_v6_mapping(
    entries: object,
) -> list[tuple[ipaddress.IPv6Network, str]]:
    if entries is None:
        return []
    if not isinstance(entries, dict):
        msg = "GeoIP hints 'prefixes_v6' must be a mapping"
        raise GeoIpHintsError(msg)
    prefixes: list[tuple[ipaddress.IPv6Network, str]] = []
    for cidr, code in entries.items():
        if not isinstance(cidr, str) or not isinstance(code, str):
            msg = f"Invalid IPv6 prefix entry: {cidr!r} -> {code!r}"
            raise GeoIpHintsError(msg)
        normalized = _parse_country_code(code, context=code)
        try:
            network = ipaddress.IPv6Network(cidr.strip(), strict=False)
        except ValueError as exc:
            msg = f"Invalid IPv6 CIDR in GeoIP hints: {cidr!r}"
            raise GeoIpHintsError(msg) from exc
        prefixes.append((network, normalized))
    return prefixes


def _parse_hints_yaml(payload: str) -> _PrefixTables:
    raw = yaml.safe_load(payload)
    if not isinstance(raw, dict):
        msg = "GeoIP hints YAML root must be a mapping"
        raise GeoIpHintsError(msg)
    v4_entries = raw.get("prefixes")
    v6_entries = raw.get("prefixes_v6")
    if v4_entries is None and v6_entries is None:
        return _embedded_prefixes()
    v4 = _parse_v4_mapping(v4_entries) if v4_entries is not None else []
    v6 = _parse_v6_mapping(v6_entries)
    return _PrefixTables(v4=v4, v6=v6)


def _embedded_prefixes() -> _PrefixTables:
    """Minimal defaults when no hints file is present."""
    return _PrefixTables(
        v4=[
            (ipaddress.IPv4Network("8.8.8.0/24"), "US"),
            (ipaddress.IPv4Network("8.8.4.0/24"), "US"),
            (ipaddress.IPv4Network("1.1.1.0/24"), "AU"),
            (ipaddress.IPv4Network("1.0.0.0/24"), "AU"),
        ],
        v6=[
            (ipaddress.IPv6Network("2001:db8::/32"), "US"),
        ],
    )

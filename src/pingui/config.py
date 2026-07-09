"""Configuration loading for monitored hosts."""

from __future__ import annotations

import ipaddress
import re
import socket
from enum import Enum
from pathlib import Path

import yaml

MIN_HOSTS = 0
MAX_HOSTS = 10
_HOSTNAME_PATTERN = re.compile(r"^[a-zA-Z0-9.-]+$")


class HostAddressKind(Enum):
    """Normalized host entry classification."""

    IPV4 = "ipv4"
    IPV6 = "ipv6"
    HOSTNAME = "hostname"


class ConfigError(ValueError):
    """Raised when configuration is invalid."""


def _strip_brackets(value: str) -> str:
    if value.startswith("[") and value.endswith("]") and len(value) >= 2:
        return value[1:-1].strip()
    return value


def _is_ipv4_literal(value: str) -> bool:
    try:
        ipaddress.IPv4Address(value)
    except ipaddress.AddressValueError:
        return False
    return True


def normalize_ipv6_literal(host: str, *, original: str | None = None) -> str:
    """Return RFC 5952 canonical IPv6 text."""
    if "%" in host:
        entry = original or host
        msg = f"IPv6 zone identifiers are not supported: {entry!r}"
        raise ConfigError(msg)
    try:
        return ipaddress.IPv6Address(host).compressed
    except ipaddress.AddressValueError as exc:
        entry = original or host
        msg = f"Invalid IPv6 address: {entry!r}"
        raise ConfigError(msg) from exc


def normalize_host_entry(entry: str) -> str:
    """Validate and normalize IPv4/IPv6 literal or hostname."""
    if not entry or not entry.strip():
        msg = f"Invalid host entry: {entry!r}"
        raise ConfigError(msg)
    host = _strip_brackets(entry.strip())
    if not host:
        msg = f"Invalid host entry: {entry!r}"
        raise ConfigError(msg)
    if ":" in host:
        return normalize_ipv6_literal(host, original=entry)
    if _is_ipv4_literal(host):
        return host
    if len(host) > 253 or not _HOSTNAME_PATTERN.fullmatch(host):
        msg = f"Invalid host entry: {entry!r}"
        raise ConfigError(msg)
    return host


def host_address_kind(normalized: str) -> HostAddressKind:
    """Classify a normalized host entry."""
    if _is_ipv4_literal(normalized):
        return HostAddressKind.IPV4
    if ":" in normalized:
        return HostAddressKind.IPV6
    return HostAddressKind.HOSTNAME


def duplicate_key(normalized: str) -> str:
    """Case-insensitive duplicate key (canonical for IPv6)."""
    if host_address_kind(normalized) == HostAddressKind.IPV4:
        return normalized
    return normalized.lower()


def is_ipv6_literal(host: str) -> bool:
    """Return True when ``host`` is a normalized or raw IPv6 literal."""
    try:
        return host_address_kind(normalize_host_entry(host)) == HostAddressKind.IPV6
    except ConfigError:
        return False


def validate_session_host(host: str, existing: list[str]) -> str:
    """Validate a host for in-session addition (dedup + MAX_HOSTS)."""
    normalized = normalize_host_entry(host)
    key = duplicate_key(normalized)
    seen = {duplicate_key(h) for h in existing}
    if key in seen:
        msg = f"Duplicate host: {normalized}"
        raise ConfigError(msg)
    if len(existing) >= MAX_HOSTS:
        msg = f"Maximum {MAX_HOSTS} hosts allowed in one session"
        raise ConfigError(msg)
    return normalized


def load_hosts_config(path: Path | str) -> list[str]:
    """
    Load 0–10 host targets from YAML config.

    IPv6 literals are normalized to RFC 5952 canonical form.
    """
    config_path = Path(path)
    if not config_path.is_file():
        msg = f"Config file not found: {config_path}"
        raise ConfigError(msg)

    raw = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        msg = "Config root must be a mapping"
        raise ConfigError(msg)

    hosts = raw.get("hosts")
    if not isinstance(hosts, list):
        msg = "Config must contain 'hosts' as a list"
        raise ConfigError(msg)

    if not MIN_HOSTS <= len(hosts) <= MAX_HOSTS:
        msg = f"hosts count must be between {MIN_HOSTS} and {MAX_HOSTS}, got {len(hosts)}"
        raise ConfigError(msg)

    normalized: list[str] = []
    seen: set[str] = set()
    for entry in hosts:
        if not isinstance(entry, str):
            msg = f"Each host must be a string, got {type(entry).__name__}"
            raise ConfigError(msg)
        host = normalize_host_entry(entry)
        key = duplicate_key(host)
        if key in seen:
            msg = f"Duplicate host: {host}"
            raise ConfigError(msg)
        seen.add(key)
        normalized.append(host)

    return normalized


def save_hosts_config(path: Path | str, hosts: list[str]) -> None:
    """Write host list to YAML (0–10 entries)."""
    if not 0 <= len(hosts) <= MAX_HOSTS:
        msg = f"hosts count must be between 0 and {MAX_HOSTS}, got {len(hosts)}"
        raise ConfigError(msg)

    normalized: list[str] = []
    seen: set[str] = set()
    for entry in hosts:
        host = normalize_host_entry(entry)
        key = duplicate_key(host)
        if key in seen:
            msg = f"Duplicate host: {host}"
            raise ConfigError(msg)
        seen.add(key)
        normalized.append(host)

    config_path = Path(path)
    config_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"hosts": normalized}
    config_path.write_text(
        yaml.safe_dump(payload, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )


def resolve_host_ipv4(host: str) -> str:
    """Resolve hostname or IPv4 literal to dotted-quad string."""
    normalized = normalize_host_entry(host)
    if host_address_kind(normalized) == HostAddressKind.IPV6:
        msg = f"IPv6 literal cannot be resolved as IPv4: {host!r}"
        raise ConfigError(msg)
    if host_address_kind(normalized) == HostAddressKind.IPV4:
        return normalized
    try:
        infos = socket.getaddrinfo(normalized, None, socket.AF_INET, socket.SOCK_STREAM)
    except socket.gaierror as exc:
        msg = f"Cannot resolve host {host!r}: {exc}"
        raise ConfigError(msg) from exc
    if not infos:
        msg = f"No IPv4 address for host {host!r}"
        raise ConfigError(msg)
    return str(infos[0][4][0])


def resolve_trace_target(host: str) -> str:
    """Return trace target IP string (IPv4 dotted quad or IPv6 canonical)."""
    normalized = normalize_host_entry(host)
    if host_address_kind(normalized) == HostAddressKind.IPV6:
        return normalized
    return resolve_host_ipv4(normalized)

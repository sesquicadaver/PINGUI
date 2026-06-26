"""Configuration loading for monitored hosts."""

from __future__ import annotations

import ipaddress
import socket
from pathlib import Path

import yaml

MIN_HOSTS = 0
MAX_HOSTS = 10


class ConfigError(ValueError):
    """Raised when configuration is invalid."""


def _is_valid_host(value: str) -> bool:
    """Check whether value is a valid IPv4 address or resolvable hostname."""
    value = value.strip()
    if not value:
        return False
    try:
        ipaddress.IPv4Address(value)
        return True
    except ipaddress.AddressValueError:
        pass
    if len(value) > 253:
        return False
    allowed = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-")
    return all(c in allowed for c in value)


def normalize_host_entry(entry: str) -> str:
    """Validate and normalize a single host entry."""
    host = entry.strip()
    if not _is_valid_host(host):
        msg = f"Invalid host entry: {entry!r}"
        raise ConfigError(msg)
    return host


def validate_session_host(host: str, existing: list[str]) -> str:
    """Validate a host for in-session addition (dedup + MAX_HOSTS)."""
    normalized = normalize_host_entry(host)
    key = normalized.lower()
    seen = {h.lower() for h in existing}
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

    Expected format::

        hosts:
          - "8.8.8.8"
          - "google.com"
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
        host = entry.strip()
        if not _is_valid_host(host):
            msg = f"Invalid host entry: {entry!r}"
            raise ConfigError(msg)
        key = host.lower()
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
        key = host.lower()
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
    try:
        ipaddress.IPv4Address(host)
        return host
    except ipaddress.AddressValueError:
        pass
    try:
        infos = socket.getaddrinfo(host, None, socket.AF_INET, socket.SOCK_STREAM)
    except socket.gaierror as exc:
        msg = f"Cannot resolve host {host!r}: {exc}"
        raise ConfigError(msg) from exc
    if not infos:
        msg = f"No IPv4 address for host {host!r}"
        raise ConfigError(msg)
    return str(infos[0][4][0])

"""Telemetry YAML config (P16-040 / ADR_TELEMETRY).

Mirrors Java ``TelemetryConfig`` under profile ``telemetry:`` (or top-level for legacy).
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

import yaml

from pingui.config import ConfigError

GelfTransport = Literal["tcp", "udp"]


@dataclass(frozen=True, slots=True)
class SyslogSinkConfig:
    host: str
    port: int = 514
    tls: bool = False


@dataclass(frozen=True, slots=True)
class GelfSinkConfig:
    host: str
    port: int = 12201
    transport: GelfTransport = "tcp"


@dataclass(frozen=True, slots=True)
class LokiSinkConfig:
    url: str
    site: str = "default"


@dataclass(frozen=True, slots=True)
class TelemetryConfig:
    """Optional local + remote LOG sinks from YAML ``telemetry:``."""

    events_only: bool = True
    log_aggregates: bool = False
    sqlite: Path | None = None
    jsonl_dir: Path | None = None
    syslog: SyslogSinkConfig | None = None
    gelf: GelfSinkConfig | None = None
    loki: LokiSinkConfig | None = None

    @classmethod
    def defaults(cls) -> TelemetryConfig:
        return cls()

    def is_default(self) -> bool:
        return (
            self.events_only
            and not self.log_aggregates
            and self.sqlite is None
            and self.jsonl_dir is None
            and self.syslog is None
            and self.gelf is None
            and self.loki is None
        )


def load_telemetry_config(path: Path | str) -> TelemetryConfig:
    """Load ``telemetry:`` from active profile, else top-level; missing → defaults."""
    config_path = Path(path)
    if not config_path.is_file():
        return TelemetryConfig.defaults()

    raw = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        return TelemetryConfig.defaults()

    block = _resolve_telemetry_block(raw)
    if block is None:
        return TelemetryConfig.defaults()
    if not isinstance(block, dict):
        msg = "telemetry must be a mapping"
        raise ConfigError(msg)
    return _parse_telemetry(block)


def _resolve_telemetry_block(root: dict[str, Any]) -> Any:
    profiles = root.get("profiles")
    if isinstance(profiles, dict) and profiles:
        active = root.get("active_profile")
        if not isinstance(active, str) or not active.strip():
            active = next(iter(profiles.keys()))
        profile = profiles.get(active.strip())
        if isinstance(profile, dict) and "telemetry" in profile:
            return profile.get("telemetry")
    return root.get("telemetry")


def _parse_telemetry(block: dict[str, Any]) -> TelemetryConfig:
    events_only = _bool_field(block, "events_only", default=True)
    log_aggregates = _bool_field(block, "log_aggregates", default=False)
    sqlite = _optional_path(block.get("sqlite"), "telemetry.sqlite")
    jsonl_dir = _optional_path(block.get("jsonl_dir"), "telemetry.jsonl_dir")
    syslog = _parse_syslog(block.get("syslog"))
    gelf = _parse_gelf(block.get("gelf"))
    loki = _parse_loki(block.get("loki"))
    return TelemetryConfig(
        events_only=events_only,
        log_aggregates=log_aggregates,
        sqlite=sqlite,
        jsonl_dir=jsonl_dir,
        syslog=syslog,
        gelf=gelf,
        loki=loki,
    )


def _optional_path(raw: Any, label: str) -> Path | None:
    if raw is None:
        return None
    if not isinstance(raw, str) or not raw.strip():
        msg = f"{label} must be a non-empty string path"
        raise ConfigError(msg)
    return Path(raw.strip())


def _parse_syslog(raw: Any) -> SyslogSinkConfig | None:
    if raw is None:
        return None
    if not isinstance(raw, dict):
        msg = "telemetry.syslog must be a mapping"
        raise ConfigError(msg)
    host = _require_str(raw.get("host"), "telemetry.syslog.host")
    port = _port_field(raw.get("port"), default=514, label="telemetry.syslog.port")
    tls = _bool_field(raw, "tls", default=False)
    return SyslogSinkConfig(host=host, port=port, tls=tls)


def _parse_gelf(raw: Any) -> GelfSinkConfig | None:
    if raw is None:
        return None
    if not isinstance(raw, dict):
        msg = "telemetry.gelf must be a mapping"
        raise ConfigError(msg)
    host = _require_str(raw.get("host"), "telemetry.gelf.host")
    port = _port_field(raw.get("port"), default=12201, label="telemetry.gelf.port")
    transport_raw = raw.get("transport", "tcp")
    if not isinstance(transport_raw, str):
        msg = "telemetry.gelf.transport must be tcp or udp"
        raise ConfigError(msg)
    transport = transport_raw.strip().lower()
    if transport not in ("tcp", "udp"):
        msg = "telemetry.gelf.transport must be tcp or udp"
        raise ConfigError(msg)
    return GelfSinkConfig(host=host, port=port, transport=transport)  # type: ignore[arg-type]


def _parse_loki(raw: Any) -> LokiSinkConfig | None:
    if raw is None:
        return None
    if not isinstance(raw, dict):
        msg = "telemetry.loki must be a mapping"
        raise ConfigError(msg)
    url = _require_str(raw.get("url"), "telemetry.loki.url")
    site = "default"
    site_raw = raw.get("site")
    if site_raw is not None:
        if not isinstance(site_raw, str) or not site_raw.strip():
            msg = "telemetry.loki.site must be a non-empty string"
            raise ConfigError(msg)
        site = site_raw.strip()
    return LokiSinkConfig(url=url, site=site)


def _require_str(raw: Any, label: str) -> str:
    if not isinstance(raw, str) or not raw.strip():
        msg = f"{label} must be a non-empty string"
        raise ConfigError(msg)
    return raw.strip()


def _port_field(raw: Any, *, default: int, label: str) -> int:
    if raw is None:
        return default
    if isinstance(raw, bool) or not isinstance(raw, int):
        msg = f"{label} must be an integer"
        raise ConfigError(msg)
    if raw < 1 or raw > 65535:
        msg = f"{label} must be 1..65535"
        raise ConfigError(msg)
    return raw


def _bool_field(mapping: dict[str, Any], key: str, *, default: bool) -> bool:
    if key not in mapping:
        return default
    value = mapping[key]
    if not isinstance(value, bool):
        msg = f"telemetry.{key} must be a boolean"
        raise ConfigError(msg)
    return value

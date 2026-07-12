"""Canonical telemetry metric names and bus labels (P16-014)."""

from __future__ import annotations

RTT_MS = "pingui_rtt_ms"
TARGET_REACHABLE = "pingui_target_reachable"
TRACE_DURATION_MS = "pingui_trace_duration_ms"
HOP_LOSS_PCT = "pingui_hop_loss_pct"
ROUTE_CHANGE_TOTAL = "pingui_route_change_total"

LABEL_PROFILE = "profile"
LABEL_PROBE_MODE = "probe_mode"
LABEL_EDITION = "edition"

EDITION_JAVA = "java"
EDITION_PYTHON = "python"


def labels(
    profile: str | None,
    probe_mode: str,
    edition: str = EDITION_PYTHON,
) -> dict[str, str]:
    """Build the standard bus label set (sorted keys)."""
    safe_profile = "default" if profile is None or not str(profile).strip() else profile
    if not probe_mode or not str(probe_mode).strip():
        msg = "probe_mode must be non-blank"
        raise ValueError(msg)
    if not edition or not str(edition).strip():
        msg = "edition must be non-blank"
        raise ValueError(msg)
    return {
        LABEL_EDITION: edition,
        LABEL_PROBE_MODE: probe_mode,
        LABEL_PROFILE: safe_profile,
    }


def python_labels(profile: str | None, probe_mode: str) -> dict[str, str]:
    return labels(profile, probe_mode, EDITION_PYTHON)

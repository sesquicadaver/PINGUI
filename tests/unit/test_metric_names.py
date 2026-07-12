"""Canonical metric names / bus labels (P16-014)."""

from __future__ import annotations

import pytest

from pingui.metric_names import (
    EDITION_JAVA,
    EDITION_PYTHON,
    HOP_LOSS_PCT,
    LABEL_EDITION,
    LABEL_PROBE_MODE,
    LABEL_PROFILE,
    ROUTE_CHANGE_TOTAL,
    RTT_MS,
    TARGET_REACHABLE,
    TRACE_DURATION_MS,
    labels,
    python_labels,
)


def test_canonical_names_match_prometheus_contract() -> None:
    assert RTT_MS == "pingui_rtt_ms"
    assert TARGET_REACHABLE == "pingui_target_reachable"
    assert TRACE_DURATION_MS == "pingui_trace_duration_ms"
    assert HOP_LOSS_PCT == "pingui_hop_loss_pct"
    assert ROUTE_CHANGE_TOTAL == "pingui_route_change_total"


def test_python_labels_include_profile_probe_mode_and_edition() -> None:
    got = python_labels("noc", "trace")
    assert got[LABEL_PROFILE] == "noc"
    assert got[LABEL_PROBE_MODE] == "trace"
    assert got[LABEL_EDITION] == EDITION_PYTHON
    assert set(got) == {LABEL_EDITION, LABEL_PROBE_MODE, LABEL_PROFILE}


def test_blank_profile_defaults() -> None:
    assert labels(None, "mtr", EDITION_JAVA)[LABEL_PROFILE] == "default"
    assert labels("  ", "mtr", EDITION_JAVA)[LABEL_PROFILE] == "default"


def test_rejects_blank_probe_mode() -> None:
    with pytest.raises(ValueError, match="probe_mode"):
        labels("p", " ", EDITION_PYTHON)

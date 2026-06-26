"""Route change detection helpers."""

from __future__ import annotations


def detect_route_change(
    previous_ips: list[str],
    current_ips: list[str],
) -> tuple[bool, list[str], list[str]]:
    """
    Compare route IP sequences.

    Returns (changed, previous_ips, current_ips). No alert on first observation
    when previous_ips is empty.
    """
    if not previous_ips:
        return False, previous_ips, current_ips
    if previous_ips == current_ips:
        return False, previous_ips, current_ips
    return True, previous_ips, current_ips

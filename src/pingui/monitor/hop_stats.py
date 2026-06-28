"""Per-hop jitter and packet loss statistics."""

from __future__ import annotations

import math

from pingui.models import MAX_HOP_RTT_SAMPLES, TIMEOUT_IP, HopNode, HopProbeStats, HopStatsSummary


def record_hop_probe(stats: HopProbeStats, node: HopNode) -> None:
    """Record one probe cycle outcome for a hop."""
    stats.probes += 1
    if node.is_timeout or node.ip == TIMEOUT_IP or node.ping_ms is None:
        return
    stats.successes += 1
    stats.rtt_samples.append(node.ping_ms)
    if len(stats.rtt_samples) > MAX_HOP_RTT_SAMPLES:
        del stats.rtt_samples[:-MAX_HOP_RTT_SAMPLES]


def loss_pct(stats: HopProbeStats) -> float:
    """Return packet loss percentage for the hop."""
    if stats.probes == 0:
        return 0.0
    failures = stats.probes - stats.successes
    return failures * 100.0 / stats.probes


def jitter_ms(samples: list[float]) -> float | None:
    """Population standard deviation of RTT samples; None if fewer than two."""
    if len(samples) < 2:
        return None
    mean = sum(samples) / len(samples)
    variance = sum((value - mean) ** 2 for value in samples) / len(samples)
    return math.sqrt(variance)


def summarize_hop_stats(stats: HopProbeStats) -> HopStatsSummary | None:
    """Build display summary when at least one probe was recorded."""
    if stats.probes == 0:
        return None
    return HopStatsSummary(
        jitter_ms=jitter_ms(stats.rtt_samples),
        loss_pct=loss_pct(stats),
    )

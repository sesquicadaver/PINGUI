> **Language:** English · [Українська](../ADR_ALERTS.md)

# ADR: Route change alert policy (P10-001)

**Status:** accepted (P10-001)  
**Date:** 2026-07-09

## Context

PINGUI detects hop IP sequence changes (`RouteChangeDetector` / `detect_route_change`) and already invokes an `onRouteChanged` callback in the monitor layer. NOC/SRE users need **channels beyond the GUI**: webhooks into runbooks and (optionally) desktop notifications on the operator workstation.

Python ships a reference implementation (`alert_dispatcher.py`, `alert_rate_limiter.py`, `desktop_notifier.py`). Java ships the full alert pipeline (phase 10, P10-010…P10-050) on `main` and `beta` after merge.

Product constraint: PINGUI is a route-focused utility, not a full alert manager (see ROADMAP X-003).

## Decision

### 1. Trigger event

- Alerts only on a **real route change** (compare hop IP lists).
- **First observation** (empty `previous_ips`) — **no alert** (avoid startup noise).
- Probe errors (`onProbeError`) — **do not** emit route-change alerts (separate channels in phase 16 telemetry).

### 2. v1 channels (in scope)

| Channel | v1 | Implementation | Notes |
|---------|----|----------------|-------|
| **Webhook** | ✅ | `POST` JSON | Generic schema; Slack Incoming Webhooks accept JSON — generic body or consumer-side mapper |
| **Desktop** | ✅ (Linux) | `notify-send` | Python: `DesktopAlertDispatcher`; Java: P10-020 (Linux first; Win/macOS best-effort later) |
| **Email** | ❌ | — | Out of scope v1 |
| **SNMP trap** | ❌ | — | Out of scope v1 |
| **PagerDuty/Opsgenie native** | ❌ | — | Via generic webhook |

Multiple channels — **fan-out** (`CompositeAlertDispatcher`); one channel failing **does not block** others.

### 3. Payload — `RouteChangeEvent`

Single model for webhook and (future) persistence / telemetry (P16-050).

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `event` | string | yes | Always `"route_change"` |
| `host` | string | yes | Monitored target (YAML address) |
| `old_ips` | string[] | yes | Previous route (hop IPs, no timeouts) |
| `new_ips` | string[] | yes | New route |
| `timestamp` | string | yes | ISO-8601 UTC (`…Z`) |
| `profile` | string | no | Profile name (default `"default"`) |

Example:

```json
{
  "event": "route_change",
  "host": "8.8.8.8",
  "old_ips": ["10.0.0.1", "192.168.1.1"],
  "new_ips": ["10.0.0.1", "8.8.8.8"],
  "timestamp": "2026-07-09T07:30:00Z",
  "profile": "default"
}
```

Serialization: Python `RouteChangeEvent.to_json()` / `from_json()`; Java mirror in `RouteChangeEvent.java` (P10-010).

### 4. Rate limit

- **Per host**, rolling **1 hour** window.
- Default: **10 alerts / host / hour** (`--alert-rate-limit`, future YAML `alert_rate_limit`).
- Over limit — **drop + debug log**; monitor loop continues.
- Rate limit applies **before fan-out** (one counter per event, not per channel).

### 5. Errors and security

| Situation | Behaviour |
|-----------|-----------|
| Webhook timeout / HTTP error | `WARNING` log; **no crash** |
| Desktop notify unavailable | skip (Linux without `notify-send`) |
| Webhook URL in logs | **redact** credentials/query (`redact_webhook_url`) |
| TLS / self-signed | v1: OS HTTP client defaults; custom CA — out of scope v1 |

### 6. Configuration (precedence)

1. CLI flags (highest)
2. Active YAML profile fields (P10-021 / P10-031)
3. Default: alerts **disabled** (`NoOp` / `null` dispatcher)

Python CLI (reference):

- `--alert-webhook URL`
- `--desktop-alerts`
- `--alert-rate-limit N` (default 10)

Java parity: `PinguiApplication` + `CliProfileOverrides` (P10-031, P10-021).

### 7. Monitor integration

```
RoutePoller → RouteChangeDetector
     → RouteChangeEvent.from_route_change(...)
     → RateLimitedAlertDispatcher
           → CompositeAlertDispatcher [Webhook, Desktop, …]
```

Java: invoke from `MonitorService` after `onRouteChanged` (P10-011), not from the UI thread for webhook I/O.

The Java GUI keeps journal/route graph as-is; desktop alert is an optional channel, not a UI replacement.

### 8. Future (not v1)

- **P16-050:** ✅ `WebhookTelemetrySink` + `WebhookAlertDispatcher` delegates HTTP — single emit path, no duplicate client.
- YAML `alerts:` section with multiple sinks (phase 16).
- Payload enrichment (ASN, geo, diff summary) — separate tickets, backward-compatible fields.

## Consequences

- **Python:** implementation matches this ADR; ROADMAP PY-041…045 — reference for Java.
- **Java:** P10-010…P10-050 implement this ADR; tests — contract JSON + rate limit burst.
- **Docs:** CONFIGURATION (P10-021), CHECKLIST alert smoke (P10-050), LIVING_SPEC matrix.
- **Do not build:** separate alert engine, durable retry queue, ack/nack protocol.

## References

- [ROADMAP.md](ROADMAP.md) — phase 10 (P10-*), phase 16 (P16-050)  
- Python: `src/pingui/models.py` (`RouteChangeEvent`), `src/pingui/monitor/alert_dispatcher.py`  
- Java (shipped): `monitor/AlertDispatcher.java`, `monitor/WebhookAlertDispatcher.java`, …

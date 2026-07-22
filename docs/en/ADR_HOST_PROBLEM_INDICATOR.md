> **Language:** English · [Українська](../ADR_HOST_PROBLEM_INDICATOR.md)

# ADR: Host-row problem indicator + session DB auto-naming (P22-001)

**Status:** accepted (P22-001)  
**Date:** 2026-07-22

## Context

Operators see RTT row color and metrics but **no explicit badge** for reachability problems with incident details. Webhook/desktop channels ([ADR_ALERTS](ADR_ALERTS.md) / [ADR_ALERT_RULES](ADR_ALERT_RULES.md)) do not replace in-app indication.

Session SQLite via file picker is awkward for a quick NOC start: need an **auto-create** button with a predictable filename.

User decisions (2026-07-22):

| Topic | Decision |
|-------|----------|
| Badge scope v1 | **`endpoint_down` only**; `latency_high` / loss → **later** with ADR_ALERT_RULES v2 |
| After RESOLVED | Badge **stays** (session history) |
| After viewing | Badge **acks off** until next FIRING |
| DB record | Incident **remains** in SQLite after ack (when DB connected) |
| Dialog source | Session **RAM** (`AlertRuleEngine`) **and** SQLite (when present) |
| Max duration | Longest **single** incident (FIRING→RESOLVED, or until now if still FIRING) |
| Repeats | How many times entered **FIRING** this session |
| Auto session DB | Button in “Database…” beside Browse; dir `data/`; name `YYYY-MM-DD_HH-mm-ss_<local-ip>.db` |
| Local IP | Operator machine LAN address (typically RFC1918: `10/8`, `192.168/16`, `172.16/12`) |

## Decision

### 1. Layer split

| Layer | Responsibility |
|-------|----------------|
| Rules / channels | [ADR_ALERT_RULES](ADR_ALERT_RULES.md), [ADR_ALERTS](ADR_ALERTS.md) — *when* to emit FIRING/RESOLVED |
| Session problem UX | this ADR — badge, ack, dialog, session aggregates |
| Persistence | SQLite `persistence_event` — durable incident rows |
| Session DB naming | auto-name utility + GUI button |

Do not turn PINGUI into an NMS ([ROADMAP X-003](ROADMAP.md)).

### 2. Session problem model (RAM)

Per `(host, rule)` in the GUI/monitor session:

| Field | Meaning |
|-------|---------|
| `unread` | `true` after first FIRING (or new FIRING after ack); `false` after ack |
| `fire_count` | FIRING entries this session |
| `max_duration` | max single-incident duration |
| `last_started_at` | start of current/last incident |
| `last_resolved_at` | last RESOLVED time (if any) |
| `last_state` | `firing` \| `resolved` \| `ok` (after ack with no active FIRING) |
| `description` | short text (e.g. `endpoint_down: target unreachable`) |

**Ack:** UI after opening the dialog calls `ack(host)` → `unread=false`; badge hides. Counters/history **are not** reset. Next FIRING sets `unread=true` again.

Badge visible **only when `unread=true`**.

### 3. Detail dialog

Shows at least:

1. **Time** — `last_started_at` (and `last_resolved_at` if any)
2. **Description** — `description` / rule id
3. **Max duration** — `max_duration` (human-readable)
4. **Repeat count** — `fire_count`

If session DB is connected, the dialog **may enrich** with SQLite aggregates/history. RAM remains primary for “this session now”.

### 4. SQLite

New event type (or agreed id in `persistence_event`):

- `endpoint_down` (same discriminator as quality payload), payload ≈ `QualityAlertEvent.toJson()` (+ optional `duration_ms` on RESOLVED)

Write on FIRING and RESOLVED from the monitor layer **only when** session DB is open and policy allows (P22-003 decides policy flag; default: write when DB connected).

**Ack does not delete** DB rows.

Schema migration only if a new `event_type` CHECK/enum is required; otherwise add the id to the existing table.

### 5. Host row UI

- In `HostListCell` — compact badge (e.g. ⚠ / “!”) next to the host name.
- Visible/managed only when `unread`.
- Click → dialog → ack.

RTT row color **does not** replace the badge (different signals).

### 6. Auto-create session DB

In `PersistenceSettingsDialog`, beside Browse:

- Button like “Create…” / “Auto”.
- Path: `data/YYYY-MM-DD_HH-mm-ss_<local-ip>.db` (operator local time).
- IP: first suitable non-loopback IPv4 site-local / RFC1918; if none — documented fallback in P22-005 (e.g. `unknown` or primary non-loopback).
- Dots in IP → `-` in the filename (recommended; lock in tests).
- After generation — fill the path field; Apply as normal connect.

CLI `--session-db` / YAML lock — button disabled (same as Browse).

### 7. Out of this ADR (follow-up tickets)

| ID | Content |
|----|---------|
| **P22-002** | Engine: stats + ack API |
| **P22-003** | SQLite write path |
| **P22-004** | Icon + dialog UI |
| **P22-005** | Auto-create button + LocalIp naming |

## Consequences

- Positive: fast problem awareness without desktop/webhook dependency.
- Positive: predictable session DB names for archives.
- Negative: another persistence event type; duration aggregation tests required.
- Latency badge **deliberately** deferred — no UI heuristic without a rule.

## References

- [ADR_ALERT_RULES.md](ADR_ALERT_RULES.md) — `endpoint_down` lifecycle
- [ADR_ALERTS.md](ADR_ALERTS.md) — delivery channels
- [ROADMAP.md](ROADMAP.md) — phase 22

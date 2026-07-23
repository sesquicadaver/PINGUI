> **Language:** English · [Українська](../ADR_ALERT_RULES.md)

# ADR: Quality alert rules (P21-001)

**Status:** accepted (P21-001)  
**Date:** 2026-07-22

## Context

[ADR_ALERTS](ADR_ALERTS.md) (P10) defines **delivery channels** (webhook / desktop) and a single **`route_change`** trigger. Operators also need alerts for **endpoint unreachability** (and later rising loss / latency) without turning PINGUI into a full NMS/alert manager ([ROADMAP X-003](ROADMAP.md)).

User decisions (2026-07-22):

| Topic | Decision |
|-------|----------|
| v1 scope | **`endpoint_down` only**; `loss_high` / `latency_high` → **v2** |
| RESOLVED | User option (`notify_resolved`, default **false**) |
| Latency (v2) | Default: `rtt ≥ 2×AVG`; FIRING after **3** consecutive bad pings (**no** time window); absolute/other multiplier only when set |
| Per-host overrides | **Not in v1** (reserved; see below) |
| ADR shape | Separate **ADR_ALERT_RULES**; ADR_ALERTS remains SSOT for channels |

## Decision

### 1. Layer split

| Layer | Document | Responsibility |
|-------|----------|----------------|
| Channels | [ADR_ALERTS](ADR_ALERTS.md) | desktop / webhook / `rate_limit` / redact / fan-out |
| Rules | this ADR | when to FIRING/RESOLVED; lifecycle; rule parameters |
| Telemetry | [ADR_TELEMETRY](ADR_TELEMETRY.md) | metrics/events to LOG sinks; **not** an alert substitute |

Rules **must not** duplicate webhook URLs per host. Channels stay profile-scoped.

### 2. Lifecycle (anti false-positive)

Per **(host, rule)** state machine:

```text
OK → PENDING → FIRING → (cooldown while still bad) → OK
                ↑___________ RESOLVED (optional notify) ____|
```

| State | Meaning |
|-------|---------|
| **OK** | condition false |
| **PENDING** | condition true, below `fail_after` consecutive |
| **FIRING** | confirmed; **one** emit on enter FIRING |
| after clear | `clear_after` successes → OK; if `notify_resolved` — one RESOLVED emit |

**Cooldownoff** (`cooldown_minutes`): while still bad after FIRING — **do not** re-emit FIRING (journal/telemetry may update without toast/webhook duplicates). Global `alerts.rate_limit` (ADR_ALERTS) remains a backstop.

### 3. v1 rule — `endpoint_down`

**Signal:** target unreachable / terminal timeout (`HostTargetStats.timeout` or ping-only / trace equivalent).

**Not a signal:** single bad poll; `onProbeError` (stays telemetry); route change (separate `route_change`).

| Parameter | Default («balanced» preset) | Meaning |
|-----------|----------------------------|---------|
| `enabled` | `false` | default off |
| `fail_after` | `3` | consecutive fails → FIRING |
| `clear_after` | `2` | consecutive oks → RESOLVED/OK |
| `cooldown_minutes` | `15` | between repeat FIRING |

Sensitivity presets (GUI / YAML alias):

| Preset | fail_after | clear_after | cooldown_minutes |
|--------|------------|-------------|------------------|
| Calm | 5 | 3 | 30 |
| Balanced | 3 | 2 | 15 |
| Sensitive | 2 | 1 | 5 |

Time-to-FIRING ≈ `fail_after × effective_interval` (per-host interval already exists).

### 4. `notify_resolved` option

Profile flag `alerts.notify_resolved` (default **false**):

- `false` — webhook/desktop only on FIRING (and `route_change` as today);
- `true` — also one emit on FIRING→OK.

UX note: desktop resolve may stay off even when webhook resolve is on (implementation ticket; contract minimum is one profile bool).

### 5. Payload envelope (extension)

Channels remain per ADR_ALERTS. Quality events use a JSON discriminator `event` (not only `"route_change"`):

| Field | Required | Description |
|-------|----------|-------------|
| `event` | yes | `endpoint_down` (v1); reserved: `loss_high`, `latency_high` |
| `state` | yes | `firing` \| `resolved` |
| `host` | yes | target address |
| `timestamp` | yes | ISO-8601 UTC |
| `profile` | no | profile name |
| `rule` | yes | rule id (`endpoint_down`) |
| `detail` | no | e.g. `fail_after`, counters, last signal |

`route_change` payload **unchanged** (ADR_ALERTS). New consumers key off `event`.

Implementation may generalize `AlertDispatcher` to a shared `AlertEvent` — **outside** this ADR ticket (P21-002+).

### 6. YAML (profile contract)

```yaml
alerts:
  desktop: false
  webhook: null
  rate_limit: 10
  notify_resolved: false
  rules:
    endpoint_down:
      enabled: false
      fail_after: 3
      clear_after: 2
      cooldown_minutes: 15
      # or: preset: balanced   # calm | balanced | sensitive
```

Priority: CLI (future flags) > profile YAML > defaults.  
**Per-host `alerts:` — not in v1.**

### 7. `latency_high` (v2 — accepted decisions 2026-07-22)

Distinct from `endpoint_down`: a successful reply with high RTT is **not** down.

| Parameter | Default (critical) | Description |
|-----------|-------------------|-------------|
| `enabled` | `false` | default off |
| `multiplier` | `2.0` | signal: `rtt ≥ multiplier × AVG` (AVG = running mean of successful terminal RTTs **before** the current sample) |
| `fail_after` | `3` | consecutive bad pings → FIRING (**no** time window) |
| `clear_after` | `2` | consecutive ok → RESOLVED/OK |
| `cooldown_minutes` | `15` | between repeat FIRING emits |
| `threshold_ms` | omit | optional absolute OR with relative when set in YAML/GUI |

Warm-up: until AVG exists (0 successful samples) relative does not fire. Unreachable poll → **does not** update latency state (only `endpoint_down`). Bad samples are **not** folded into AVG (anti-poison during spikes).

YAML:

```yaml
alerts:
  rules:
    latency_high:
      enabled: true
      multiplier: 2.0
      fail_after: 3
      clear_after: 2
      cooldown_minutes: 15
      # optional: threshold_ms: 500
```

### 7b. Still reserved

- **`loss_high`:** threshold + clear (hysteresis) + window + sustain.
- **Sparse per-host YAML** rule overrides (same pattern as `intervalSecondsOverride`); GUI per-host only after YAML + clear demand.

### 8. Non-goals (X-003)

- Ack/nack, escalation policies, dependency trees, multi-condition expressions.
- Separate durable retry queue for alerts.
- Per-host channel duplication.
- Replacing journal / route graph / telemetry sinks.

### 9. Integration (follow-up tickets)

```text
Poll → HostTargetStats
    → AlertRuleEngine (pure)  # P21-002
         → AlertEvent
              → existing RateLimited + Composite channels  # ADR_ALERTS
YAML/GUI rules + notify_resolved  # P21-003
```

## Consequences

- Docs: this ADR + Related patch on ADR_ALERTS; CONFIGURATION reserved §; LIVING_SPEC.
- Rule engine code is **not** part of P21-001.
- Python rules parity — after Java engine if needed.

## References
- [ADR_HOST_PROBLEM_INDICATOR.md](ADR_HOST_PROBLEM_INDICATOR.md) — in-app badge / ack / session stats (P22)

- [ADR_ALERTS.md](ADR_ALERTS.md) — channels and `route_change`
- [ROADMAP.md](ROADMAP.md) — phase 21 (P21-*)
- `HostTargetStats`, `AlertConfig`, `AlertsSettingsDialog` (existing channel GUI)

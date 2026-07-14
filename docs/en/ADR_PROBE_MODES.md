> **Language:** English · [Українська](../ADR_PROBE_MODES.md)

# ADR: probe modes — `trace | mtr | ping_only` (P13-001)

**Date:** 2026-07-09  
**Status:** accepted  
**Branch:** `beta`

## Context

PINGUI currently has two **different** “mode” concepts:

| Concept | Where | Meaning |
|---------|-------|---------|
| `probe: auto\|process\|raw` | Profile YAML (`ProbeMode`) | Trace **transport**: subprocess `traceroute`/`tracert` vs raw ICMP (Linux) |
| `ping_only: true` | Host YAML / GUI checkbox | Poll **strategy**: target RTT only, no hops |

Problems with the current model:

1. **Full trace every cycle** — expensive on Windows (`tracert` 3 probes/hop, minutes per target).
2. **`ping_only`** only covers “target without hops”, not **per-hop RTT/loss** without a full trace.
3. **Single profile `interval`** — ping-only and trace need different optimal intervals (1–2 s vs 30–300 s).

Phase 13 introduces an explicit **`probe_mode`** at profile level with per-host override.

## Decision

### Three monitoring modes (`probe_mode`)

| Mode | Poll cycle | Hops | Route change |
|------|------------|------|--------------|
| **`trace`** | Full traceroute/tracert/raw trace to `max_hops` | Yes, every cycle | Yes (`route_ips` compare) |
| **`mtr`** | **Continuous per-hop**: one or more hops per cycle (state machine) | Yes, incremental | Yes (when path changes) |
| **`ping_only`** | Single ping to target (existing `pollHostPingOnly`) | No (empty route) | No (target RTT/loss only) |

**MTR ≠ trace:** do not run a full trace on every tick. `MtrProbe` keeps a TTL/hop cursor and probes the next hop or rotates hops per cycle (details — P13-010).

### YAML (target schema, P13-011)

```yaml
profiles:
  default:
    interval: 30.0          # default for trace
    probe_mode: trace       # trace | mtr | ping_only
    hosts:
      - address: 8.8.8.8
        enabled: true
        probe_mode: ping_only   # optional per-host override
```

**Backward compatibility:**

- Missing `probe_mode` → **`trace`**.
- Existing `ping_only: true` on host → equivalent to **`probe_mode: ping_only`** (mapped in `ProfilesConfig`; deprecated flag kept until P13-050).
- `probe: auto|process|raw` **unchanged** — trace transport, orthogonal to `probe_mode`.

### Intervals (P13-020, P13-021)

| `probe_mode` | Recommended default interval | Note |
|--------------|-------------------------------|------|
| `ping_only` | 1–2 s | Light ICMP to target |
| `mtr` | 5–15 s | Per-hop steps; N hops per cycle |
| `trace` | 30–300 s | Full trace; Windows ≥ 60 s |

**Burst on change (P13-021):** after `route_change` — `effective_interval = interval × 0.25` for 5 min, then restore.

### Concurrency (P13-030)

`max_concurrent_traces` (default **3**) caps simultaneous subprocess/raw traces — does not apply to `ping_only` (light pings).

### Platforms

| OS | `trace` | `mtr` | `ping_only` |
|----|---------|-------|-------------|
| Linux | ✅ recommended | ✅ target (mtr-like state machine) | ✅ |
| Windows | ⚠ slow trace | ⚠ MTR via subprocess limited | ✅ **recommended** |
| macOS | ✅ | best-effort / not shipped as full MTR | ✅ |

Windows preset (P13-040): `probe_mode: ping_only`, `interval: 60` in `hosts.windows.example.yaml`.

## Alternatives

1. **Only increase `interval` without MTR** — rejected: no per-hop metrics without full trace.
2. **External `mtr` subprocess** — deferred: P13-010 starts with in-process state machine + existing probes; subprocess MTR optional spike.
3. **Merge `probe` and `probe_mode` into one field** — rejected: conflates transport (raw vs tracert) and strategy (trace vs ping).

## Consequences

- New types: `HostProbeMode` enum, `MtrProbe`, `HostPollSchedule`, `BurstSchedulePolicy`.
- `MonitorService.pollHostOnce` branches on `resolveProbeMode(host)` instead of `pingOnly` boolean alone.
- GUI: “Ping only” checkbox → displays `probe_mode=ping_only` (P13-011).
- LIVING_SPEC / JAVA.md — MTR vs traceroute limitations (P13-050).

## Follow-ups

| ID | Task |
|----|------|
| P13-010 | `MtrProbe` state machine |
| P13-011 | YAML + `HostEntry.probeMode()` |
| P13-020 | `HostPollSchedule` smart intervals |
| P13-021 | `BurstSchedulePolicy` |
| P13-030 | `max_concurrent_traces` |
| P13-040 | Windows example YAML |
| P13-050 | LIVING_SPEC + JAVA.md |

## Link to current code (evidence)

- `MonitorService.pollHostOnce` — `hostPingOnly ? pollHostPingOnly : pollHostRoute` (`beta`).
- `ProbeMode` enum — trace transport, not poll strategy.
- `HostEntry.pingOnly()` + GUI checkbox — interim path to `probe_mode: ping_only`.

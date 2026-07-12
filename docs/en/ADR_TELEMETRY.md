> **Language:** English · [Українська](../ADR_TELEMETRY.md)

# ADR: Telemetry — events / samples / aggregates and sinks (P16-001)

**Status:** accepted (P16-001)  
**Date:** 2026-07-12  
**Branch:** `beta` (after merge — `main` and `beta`).

## Context

After phases 10–15, PINGUI has several **parallel** data paths from the poll loop:

| Channel | Phase | Carries |
|---------|-------|---------|
| Alerts (webhook / desktop) | P10 | Operator notify on `route_change` |
| SQLite session | P11 | GUI history / session state |
| Prometheus scrape | P15 | Pull gauges/counters |
| Influx/Timescale push | P15 / B-05 | Write RTT + route markers |
| REST read-only | P15-040 | Host/route snapshot |

NOC still needs a **local archive** of samples/events and **LOG-server export** (syslog/GELF). Without an ADR it is easy to:

- send hop-RTT every second to syslog;
- duplicate webhook payloads via a separate “telemetry” HTTP client;
- mix session SQLite with a telemetry archive;
- keep dual-emit from MonitorService forever.

[ADR_OBSERVABILITY.md](ADR_OBSERVABILITY.md) fixes P15 boundaries and explicitly defers a unified bus to P16.

## Decision

### 1. Three data classes

| Class | Examples | Frequency | Where (v1) |
|-------|----------|-----------|------------|
| **Events** | `route_change`, `probe_error`, (optional) `daemon_start` | Rare | LOG-server (syslog/GELF); local event store; alerts remain separate notify |
| **Samples** | RTT per hop, loss/jitter snapshot, `trace_duration_ms`, `target_reachable` | High (each poll) | TS / Prometheus / local SQLite-or-JSONL; **not** syslog by default |
| **Aggregates** | avg/max RTT per hop over 5 min | Low | Optionally to LOG (`log_aggregates`); or local only |

**Rule:** *events → LOG*; *samples → TS / scrape / local archive*; *aggregates → LOG only when explicitly enabled*.

### 2. Topology: bus → sinks

```mermaid
flowchart LR
  MS[MonitorService / poll] --> BUS[TelemetryBus]
  BUS --> REG[SinkRegistry]
  REG --> L1[SqliteTelemetrySink]
  REG --> L2[JsonlRotateSink]
  REG --> R1[SyslogSink]
  REG --> R2[GelfSink]
  REG --> R3[PrometheusTelemetrySink]
  REG --> R4[InfluxTelemetrySink]
  REG --> R5[WebhookAlertSink optional]
  REG --> R6[LokiPushSink P2]
```

*Note:* `InfluxTelemetrySink` covers Influx **and** Timescale (wrapper over P15-020 / B-05).
| Component | Role | Ticket |
|-----------|------|--------|
| `MetricSample` / `TelemetryEvent` | Serializable records (host, hop, labels, ts) | P16-010 ✅ |
| `TelemetrySink` + `SinkRegistry` | Pluggable writers; no-op default | P16-011 ✅ |
| `TelemetryBus` | Async queue, batch flush, backpressure, drop policy | P16-012 ✅ |
| Wire from MonitorService | Single emit; **must not** block poll | P16-013 ✅ |

**Poll-loop consequence:** bus emit must be non-blocking (queue offer). Overflow → drop + counter/log per bus policy (documented in P16-012).

### 3. Local vs remote sinks

| Sink | Kind | Default | Notes |
|------|------|---------|-------|
| `SqliteTelemetrySink` | local samples+events | **off** | Separate from P11 `host_session`; retention P16-022 |
| `JsonlRotateSink` | local file | **off** | `telemetry.jsonl.%Y-%m-%d` |
| `SyslogSink` | remote events | **off** | RFC 5424 TCP; TLS optional; `events_only=true` by default (P16-033); MSG = single-line JSON |
| `GelfSink` | remote events | **off** | Graylog; TCP preferred / UDP lab; `events_only=true` by default (P16-033) |
| `LokiPushSink` | remote | **off** | P2 (P16-032); same `events_only` default |
| `PrometheusTelemetrySink` | in-process scrape state | via `--metrics-port` | Not remote_write (see ADR_OBSERVABILITY) |
| `InfluxTelemetrySink` | remote samples | via TS config | Wrapper over B-05 / P15-020 |
| Webhook as sink | remote events | via alerts config | P16-050 — one emit path, not a second HTTP client |

### 4. Boundaries with P10 and P15

| Boundary | Decision |
|----------|----------|
| **P10 alerts** | Remain **operator notify**. Telemetry **may** mirror `route_change` as an event to sinks, but LOG does not replace UX notify. P16-050 refactors webhook into a `TelemetrySink` without changing the ADR_ALERTS payload contract. |
| **P15 Prometheus** | Pull/scrape remains. P16-051: exporter becomes a sink that updates in-process gauges from the bus. |
| **P15 TS push** | P16-052: Influx/Timescale as a bus sink; MonitorService dual-emit removed after P16-013. |
| **P11 session DB** | GUI history / policy events. **Not** a Grafana datasource and not a telemetry archive replacement. |
| **REST API** | Read snapshot; not the telemetry bus. |
| **OTLP** | Out of scope for v1 (P16-080). |

### 5. Metric names (aligned with P15)

Keep the `pingui_` prefix. P16-014 ✅ canonicalizes names/labels for the bus (`MetricNames` / `metric_names.py`); the scrape minimum already exists:

| Name | Class | Notes |
|------|-------|-------|
| `pingui_rtt_ms` | sample | host/hop are sample fields; bus labels: profile, probe_mode, edition |
| `pingui_hop_loss_pct` | sample | hop loss % |
| `pingui_route_change_total` | derived from events | counter |
| `pingui_target_reachable` | sample/gauge | |
| `pingui_trace_duration_ms` | sample/gauge | |

New sample fields (loss/jitter) are added via P16-010 without changing this ADR’s boundaries.

### 6. Configuration (priority)

1. CLI: `--telemetry-*` (P16-041)  
2. YAML `telemetry:` on the active profile (P16-040)  
3. Default: **all sinks off** (zero remote IO)

Secrets (URL, token) must **not** be logged in plaintext (P16-042).

### 7. Failure policy

| Situation | Behaviour |
|-----------|-----------|
| Sink write failure | `WARNING`; poll **does not** stop |
| Bus overflow | drop oldest/newest per P16-012 policy; drop-count metric |
| Sink misconfigured | fail-fast at daemon start **or** disable sink + WARN (chosen in the sink ticket) |

## Consequences

- **Documentation:** this ADR gates P16-010+; protocol SPIKE is P16-002 ✅ (`docs/SPIKE_LOG_SINKS.md`).
- **Implementation:** model + bus first (P16-010…013), then local sinks, then LOG, then P10/P15 wrappers.
- **Operators:** syslog gets rare events; high-freq RTT only via TS/Prometheus/local archive.
- **Do not:** high-freq RTT in syslog; a second webhook client beside alerts; session SQLite as TS for Grafana.

## References

- [ROADMAP.md](ROADMAP.md) — phase 16 (P16-*)  
- [ADR_OBSERVABILITY.md](ADR_OBSERVABILITY.md) — Prometheus vs TS; dual-emit debt  
- [ADR_ALERTS.md](ADR_ALERTS.md) — notify channel  
- [ADR_DAEMON.md](ADR_DAEMON.md) — headless process  
- [SPIKE_PERSISTENCE.md](SPIKE_PERSISTENCE.md) — SQLite session ≠ TS  
- Java (planned): `telemetry/`  
- Python (planned): mirrored models + sinks

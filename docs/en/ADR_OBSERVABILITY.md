> **Language:** English · [Українська](../ADR_OBSERVABILITY.md)

# ADR: Observability boundaries — Prometheus vs time-series backend (P15-001)

**Status:** accepted (P15-001)  
**Date:** 2026-07-10  
**Branch (historical):** accepted on `beta`; after merge — on `main` and `beta`.

## Context

NOC/SRE expect two different integrations from PINGUI:

1. **Pull metrics** — Prometheus/Grafana scrape gauges and counters from the daemon.
2. **Push time-series** — write RTT/route samples to InfluxDB or Timescale (already in Python B-05).

Other data channels already exist and are **not** metrics backends:

| Channel | Phase | Purpose |
|---------|-------|---------|
| SQLite session (`--session-db`) | P11 | Session state + discrete events (`route_change`, `probe_error`) for GUI/history |
| Alerts (webhook / desktop) | P10 | Operator notify; not a time-series store |
| REST read-only API | P15-040 | Operational host/route snapshot; not metrics scrape |
| Telemetry bus + LOG-server | P16 | Unified emit → sinks (future) |

Without an ADR it is easy to conflate scrape with push, or try to feed Grafana from the SQLite session DB.

## Decision

### 1. Two orthogonal paths (P15)

| Path | Direction | Protocol | When | Ticket |
|------|-----------|----------|------|--------|
| **Prometheus** | **read / pull** | HTTP `GET /metrics` (text exposition) | Daemon mode; external scrape | P15-010, P15-011 |
| **TS backend** | **write / push** | Influx line protocol / SQL insert | Optional when configured | P15-020 ✅ (Java); Python B-05 ✅ |

- Prometheus is **not** a write store for PINGUI in v1 (no `remote_write` client).
- Influx/Timescale do **not** replace `/metrics`: Grafana may use **both** datasources independently.
- Both paths are **off by default** (no listeners / no remote writes without explicit CLI/YAML).

### 2. Boundaries with other layers

```
MonitorService / poll loop
        │
        ├─► SessionStore + SQLite (P11)     — session / discrete events
        ├─► AlertDispatcher (P10)          — operator notify
        ├─► PrometheusExporter (P15)       — in-process gauges → scrape
        ├─► TimeSeriesBackend (P15/B-05)   — push samples (optional)
        └─► TelemetryBus (P16, later)      — unified async emit → sinks
```

| Do not do in P15 | Why |
|------------------|-----|
| Write hop-RTT into SQLite as a “Prometheus replacement” | Session DB ≠ TS; volume and query model differ |
| Duplicate alert webhook as metrics push | P10 stays notify; metrics are a separate contract |
| OTLP / OpenTelemetry export | Deferred (P16-080) |
| Prometheus `remote_write` | Out of scope v1; scrape is enough for daemon |
| High-freq RTT to syslog | That is P16 LOG-server (`events_only`) |

### 3. Prometheus contract (v1, P15-010)

Minimal metric names (`pingui_` prefix):

| Metric | Type | Labels (v1) | Description |
|--------|------|-------------|-------------|
| `pingui_rtt_ms` | gauge | `host`, `hop` | Last known RTT (ms) |
| `pingui_route_change_total` | counter | `host` | Detected route-change count |
| `pingui_target_reachable` | gauge | `host` | `1` / `0` — target reachable on last poll (hop IP == `targetIp`) |
| `pingui_trace_duration_ms` | gauge | `host`, `probe_mode` | Last trace/mtr/ping duration |

- Default bind: **localhost** (`--metrics-port`, P15-011).
- **Daemon** only (and optionally headless monitor); GUI-only has no listener by default.
- Auth / TLS on `/metrics` — **out of scope v1**; reverse proxy — P15-041 ✅ (`docs/DEPLOYMENT.md`).

### 4. TS backend contract (write)

- Backends: **`influx`** | **`timescale`** (same as Python `create_timeseries_backend`).
- Data: `PingSample` (RTT per hop) + `RouteEvent` (snapshot / change marker).
- Config: CLI / env (`INFLUXDB_*`, `PINGUI_TIMESCALE_DSN`) — priority as in alerts ADR §6.
- Write failure → `WARNING`, **without** stopping the poll loop.
- Secrets (token, DSN) — **never** log plaintext.

Java P15-020 must mirror the Python API (`TimeSeriesBackend`), not invent a third model.

### 5. Link to phase 16

P15 adapters are **temporary direct** calls from the monitor (**dual-emit debt** until P16-013): MonitorService may update Prometheus gauges and push to a TS backend at the same time. Acceptable for v1, **not** the target topology.

After P16-011…013:

- `PrometheusExporter` → `PrometheusTelemetrySink` (P16-051) — **in-process scrape state-holder**, not a Prometheus `remote_write` / push client
- Influx/Timescale → `InfluxTelemetrySink` / wrapper (P16-052)
- Single emit path via `TelemetryBus` (no dual HTTP/SQL from MonitorService)

**SQLite samples (P16-020):** a future `SqliteTelemetrySink` may store `ping_sample` locally (default off / retention). That is a **local archive**, not a Prometheus scrape replacement and not the default Grafana TS datasource. The §2 boundary (“do not write hop-RTT into SQLite as a Prometheus replacement”) still applies for P15 and for operator dashboards.

ADR P16-001 ✅ details events vs samples vs aggregates and metric-name mapping (align with `pingui_*` / P16-014); this ADR fixes P15 boundaries.

### 6. Configuration (priority)

1. CLI (highest): `--metrics-port`, TS flags / env  
2. Active profile YAML (future `metrics:` / `timeseries:` sections — in implementation tickets)  
3. Default: **both paths off**

## Consequences

- **Docs:** this ADR is the gate before P15-010/P15-020; indexed in `docs/README.md`.
- **Implementation:** P15-010 ✅ (`PrometheusExporter` / `MetricsHttpServer`); P15-011 ✅ (`--metrics-port`); P15-020 ✅ (`persistence/timeseries/` + CLI `--ts-backend`); P15-040 ✅ (read-only API); P15-041 ✅ (nginx/TLS in DEPLOYMENT).
- **Operators:** Grafana = scrape Prometheus **and/or** Influx/Timescale datasource; SQLite session is for GUI history, not dashboards.
- **Do not build:** a single “observability blob” mixing alerts + SQLite + Prometheus remote_write.

## References

- [ROADMAP.md](ROADMAP.md) — phase 15 (P15-*), phase 16 (P16-051…052)  
- [ADR_ALERTS.md](ADR_ALERTS.md) — separate notify channel  
- [ADR_DAEMON.md](ADR_DAEMON.md) — headless process hosting `/metrics`  
- [SPIKE_PERSISTENCE.md](SPIKE_PERSISTENCE.md) — SQLite ≠ TS  
- Python: `src/pingui/persistence/timeseries/`  
- Java: `observability/PrometheusExporter.java`, `persistence/timeseries/` (P15-020 ✅)

> **Language:** English · [Українська](../ADR_SESSION_SCHEMA.md)

# ADR: SQLite session schema evolution (P30)

**Status:** accepted (P30-001+)  
**Date:** 2026-09-03  
**Branch:** `beta` (after merge — `main` and `beta`).  
**Source:** `pingui-evo-db.md`.

## Context

Schema v7 normalized hops/history into child tables, but the **host address remained the PRIMARY KEY**. That caused:

- `rename()` rewrote related rows;
- no stable key for incidents / poll history / SLA;
- awkward historical queries.

Replacing SQLite / adding an ORM / splitting per-subsystem DBs is out of scope for desktop mode.

## Decision

Evolve **without migrating old `.db` files**: `schema_version != supported` → fail-fast; operator deletes the file and recreates (same as P27).

### Target minimal schema (phase 30)

| Step | ID | Intent |
|------|-----|--------|
| 1 | **P30-001** | `host_session.id` INTEGER PK; `address` UNIQUE; children/`persistence_event` → `host_id` |
| 2 | **P30-002** | `incident` table (FIRING/RESOLVED, duration, ack) — **done** (schema v9) |
| 3 | **P30-003** | `poll_result` — canonical finished-poll aggregate — **done** (schema v10) |
| 4 | **P30-004** | deduplicated `route` (signature + hops_json) — **done** (schema v11) |
| 5 | **P30-005** | `metric_rollup` + bounded retention — **done** (schema v12) |
| 6 | **P30-006** | RO export connection / integrity_check CLI / backup-before-irreversible — **done** |

### P30-001 (done)

```sql
CREATE TABLE host_session (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    address    TEXT NOT NULL UNIQUE,
    enabled    INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

- Public API (`load`/`save`/`rename`/`listHosts`) stays **address-keyed**.
- `rename` = `UPDATE address` on the same `id` (events are not rewritten).
- `PRAGMA busy_timeout = 5000` on open.
- `telemetry_*` still store address TEXT (no FK); historical canon comes in later steps.

### P30-002 (done)

```sql
CREATE TABLE incident (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host_id INTEGER NOT NULL,
    kind TEXT NOT NULL,
    severity TEXT NOT NULL,
    state TEXT NOT NULL,
    started_at TEXT NOT NULL,
    ended_at TEXT,
    acknowledged_at TEXT,
    occurrences INTEGER NOT NULL DEFAULT 1,
    peak_value REAL,
    details_json TEXT NOT NULL DEFAULT '{}',
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
);
```

- Quality FIRING/RESOLVED (`endpoint_down` / `latency_high`) syncs `incident` via `PersistenceEventWriter`.
- Ack sets `acknowledged_at` on open FIRING rows.
- MTTR / duration from `started_at`/`ended_at` (no JSON parsing).

### P30-003 (done)

```sql
CREATE TABLE poll_result (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host_id INTEGER NOT NULL,
    observed_at TEXT NOT NULL,
    probe_mode TEXT NOT NULL,
    reachable INTEGER,
    terminal_rtt_ms REAL,
    jitter_ms REAL,
    loss_percent REAL,
    duration_ms REAL,
    route_id INTEGER,
    error_code TEXT,
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
);
```

- Written after each finished poll (`PollResultEffects` → `PersistenceEventWriter`).
- Canonical RTT/loss/uptime history; `telemetry_*` remains an export channel.
- `route_id` nullable until P30-004.

### P30-004 (done)

```sql
CREATE TABLE route (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host_id INTEGER NOT NULL,
    signature TEXT NOT NULL,
    hops_json TEXT NOT NULL,
    first_seen TEXT NOT NULL,
    last_seen TEXT NOT NULL,
    seen_count INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE,
    UNIQUE(host_id, signature)
);
```

- Signature: `ip|ip|*|ip` (`*` = timeout).
- Successful poll upserts route and sets `poll_result.route_id`.

### P30-005 (done)

```sql
CREATE TABLE metric_rollup (
    host_id INTEGER NOT NULL,
    bucket_start TEXT NOT NULL,
    bucket_size INTEGER NOT NULL,
    samples INTEGER NOT NULL,
    uptime_ratio REAL,
    rtt_min REAL,
    rtt_avg REAL,
    rtt_max REAL,
    loss_avg REAL,
    PRIMARY KEY(host_id, bucket_start, bucket_size),
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
);
```

- Bounded retention (`PollResultRetentionJob` / `--poll-retention`): raw `poll_result` 7 days; 5-minute rollup (300s) through 90 days; hourly (3600s) beyond; incidents and deduped `route` are not purged.
- Legacy v11 `.db` → fail-fast (delete & recreate).

### P30-006 (done)

- **`SessionDatabase.readOnly(path)`** — `mode=ro` for long export/dump without competing for the daemon write lock.
- CLI **`--integrity-check`** (+ `--session-db`) — `PRAGMA integrity_check`; non-zero exit on failure.
- **Backup note:** copy `.db` before retention/purge/delete-recreate (see DEPLOYMENT § SQLite session).

### Out of scope

- PostgreSQL for desktop session DB;
- ORM;
- one table per telemetry event type;
- full normalization of hops / ASN / DNS IPs;
- silent migrate of legacy files.

## Consequences

- Rename and correlation become cheaper.
- Later tables (`incident`, `poll_result`, `route`) attach to `host_id`.
- Operators on v7 `.db` must delete the file before upgrading.
### P32-003 (done, schema v13)

`poll_result` gains:

- `probe_outcome TEXT NOT NULL` — SUCCESS / TIMEOUT / REFUSED / DNS_ERROR / NETWORK_ERROR;
- `target_sampled INTEGER NOT NULL` — whether this cycle sampled the target.

`loss_percent` / `jitter_ms` are no longer synthesized from reachability (NULL when unmeasured; jitter only from an RTT series). Transactional migrate from older versions — **P32-004**.
### P32-004 (done, schema v14)

`metric_rollup` stores additive counters (`sample_count`, `reachable_*`, `rtt_samples`/`rtt_sum`, `loss_samples`/`loss_sum`); averages are computed on read. `PollResultRetentionJob` runs upsert+delete in **one** transaction. Opening a DB migrates **v13→v14** in-place.

### P32-008 (done) — persistence hotspot split

Public API remains `SessionDatabase` (connection + transactions). SQL lives in package-private helpers:

| Class | Role |
|------|------|
| `DbCommit` | `Connection`, `deferCommit`, `maybeCommit` / `rollbackQuietly` |
| `SchemaManager` | DDL, `schema_meta`, migrate v13→v14 |
| `SessionStateRepository` | `host_session` + child hop/ping/stats tables |
| `HistoryRepository` | events, incident, poll_result, route, rollup, telemetry |

No ORM and no per-table repository interfaces. Monitor side-effects were already extracted earlier (`PollResultEffects`, P26/P32).

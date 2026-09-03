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
| 2 | **P30-002** | `incident` table (FIRING/RESOLVED, duration, ack) |
| 3 | **P30-003** | `poll_result` — canonical finished-poll aggregate |
| 4 | **P30-004** | deduplicated `route` (signature + hops_json) |
| 5 | **P30-005** | `metric_rollup` + bounded retention |
| 6 | **P30-006** | RO export connection / integrity_check CLI / backup-before-irreversible |

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

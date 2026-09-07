> **Language:** English · [Українська](../pingui-route-persistence.md)

# Correctness follow-up — route / target / persistence (P34)

**Source for phase 34.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Audit of `main`≡`beta` @ `45374cc` (2026-09-06, after P33 close). Not feature expansion — focus on NOC / unattended reliability.

> Historical audits: [pingui-correctness.md](pingui-correctness.md) (P33), [pingui-stabilization.md](pingui-stabilization.md) (P32) — **archival**.

## Summary

P32/P33 materially improved the project (fresh-hop, span, tri-state poll_result, bounded writers/webhook, latency reset, schema v12→v14). Declaring `NEXT = DONE` was premature for unattended NOC: route-change can still be false, MTR can mis-label a router as the target, a current timeout can leave the endpoint `UP`, and structural persistence jobs can be dropped under DROP_OLDEST.

## Linear queue

| ID | Priority | Task | DoD (short) |
|----|----------|------|-------------|
| **P34-001** | P0 | Route identity + candidate-route FSM | [x] TRACE/MTR: hop-indexed `(hop, ip\|timeout)`; active/candidate; confirm to target; transient `*` ≠ new route |
| **P34-002** | P0 | MTR target-unknown / exhaustion | [x] `targetHop` only after real target match; `TARGET_UNKNOWN`/`INCOMPLETE`; bounded rediscovery; no `target_sampled` until identified |
| **P34-003** | P0 | Current endpoint outcome vs history | [x] Current TIMEOUT → DOWN/PENDING; session-lifetime loss must not override a live timeout |
| **P34-004** | P0 | Reliable structural persistence + shutdown | [x] Control lane (delete/rename/barrier) never drops; coalesced SaveHost; lossy telemetry; safe close |
| **P34-005** | P1 | Immutable `CompletedPoll` + GUI/daemon parity | [x] One probe-derived poll_result; no mutable SessionStore from probe/FX |
| **P34-006** | P1 | loss / jitter / rollup semantics | [x] Single-packet loss=NULL or explicit window; jitter moments/window; availability from target_sampled/reachable |
| **P34-007** | P1 | Bounded DNS + ops counters | Bounded DNS queues + coalesce per host; counters in App Status / API / Prometheus |
| **P34-008** | P1 | v12 migration repair | error rows → `target_sampled=0`, `reachable=NULL`; repair CLI/online for existing DBs |
| **P34-009** | P2 | Python compatibility edition | Lock bugfix-only; minimal shutdown harden; align version |
| **P34-010** | P2 | Soak / fault matrix + docs sync | Audit regression matrix; README/ROADMAP/`main`≡`beta` |

## Required regression tests (phase)

* target never reached / maxHops exhausted;
* full route change across several MTR steps;
* transient TRACE timeout ≠ route change;
* timeout after success history → not UP;
* overflow must not drop delete/rename control jobs;
* GUI/daemon poll_result parity;
* stuck writer shutdown;
* migrate/repair old probe-error rows.

## Out of scope

New protocols, ORM, large GUI surfaces, silent `.db` delete.

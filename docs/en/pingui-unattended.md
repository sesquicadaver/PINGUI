> **Language:** English · [Українська](../pingui-unattended.md)

# Unattended NOC — probe identity / loss / persistence (P35)

> **Archive:** phase 35 **closed** (P35-001…010). Authoritative ROADMAP **NEXT=P36-008** — [pingui-geoip.md](pingui-geoip.md).

**Source for phase 35.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Audit of `main`≡`beta` @ `a1ee5aa` (2026-09-07, after P34-010). Canonical in-repo copy of the external `pingui-current` findings.

> Historical: [pingui-route-persistence.md](pingui-route-persistence.md) (P34), [pingui-correctness.md](pingui-correctness.md) (P33), [pingui-stabilization.md](pingui-stabilization.md) (P32) — **archival**.

## Summary

P34 closed most route/persistence defects, but `NEXT = DONE` was again premature for unattended NOC: subprocess TRACE can label an intermediate router as the target, MTR `TARGET_UNKNOWN` can become `target_sampled` again, and loss is not a real window metric.

## Linear queue

| ID | Priority | Task | DoD (short) |
|----|----------|------|-------------|
| **P35-001** | P0 | TRACE real target identity | [x] `targetIp` from header/resolve (not last hop); `targetReached` with no last-router fallback |
| **P35-002** | P0 | MTR TARGET_UNKNOWN scope + rediscovery | [x] `probedHop<1` ≠ `PollSampleScope.FULL`; periodic rediscovery with backoff; no downtime from UNKNOWN |
| **P35-003** | P0 | Loss window semantics | [x] Sliding attempt window (≤50); loss=NULL when <2 probes |
| **P35-004** | P0 | Timeout ↔ target hop | [x] Attribute timeouts by `targetHop`/`freshHop`, not timeout-node IP `*` |
| **P35-005** | P1 | DNS-control bounded dispatcher | [x] Bounded/coalesce per host; outer-queue metrics |
| **P35-006** | P1 | MTR invalidate on DNS change | [x] Confirmed address-set change clears targetIp/candidate/latency |
| **P35-007** | P1 | Unified SQLite persistence pipeline | [x] CompletedPoll → one ordered write path (events+poll+route) |
| **P35-008** | P2 | Stuck-writer fault test | [x] close must not caller-drain while a stuck worker is alive |
| **P35-009** | P2 | Route signature ≡ hops_json | [x] One stabilized hop-indexed source |
| **P35-010** | P2 | Python lifecycle harden | [x] join/drain without closing DB under a live worker; no new features |

## Out of scope

New protocols, ORM, large GUI surfaces, Python feature parity, silent `.db` delete.

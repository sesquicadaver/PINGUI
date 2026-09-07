> **Language:** English · [Українська](../pingui-route-persistence.md)

# Correctness follow-up — route / target / persistence (P34)

> **Archive:** phase 34 **closed** (P34-001…010). Authoritative ROADMAP **NEXT=P35-010** — [pingui-unattended.md](pingui-unattended.md). Correctness: [pingui-correctness.md](pingui-correctness.md). Stabilization: [pingui-stabilization.md](pingui-stabilization.md).

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
| **P34-007** | P1 | Bounded DNS + ops counters | [x] Bounded DNS queues + coalesce per host; counters in App Status / API / Prometheus |
| **P34-008** | P1 | v12 migration repair | [x] error rows → `target_sampled=0`, `reachable=NULL`; repair CLI/online for existing DBs |
| **P34-009** | P2 | Python compatibility edition | [x] Lock bugfix-only; minimal shutdown harden; align version |
| **P34-010** | P2 | Soak / fault matrix + docs sync | [x] Audit regression matrix; README/ROADMAP/`main`≡`beta` |

## Regression / soak–fault matrix (P34-010)

| # | Scenario | Canonical tests | Status |
|---|----------|-----------------|--------|
| 1 | target never reached / maxHops exhausted | `MtrProbeTest.maxHopsExhaustedWithoutTargetEntersTargetUnknown`, `allTimeoutsExhaustionDoesNotClaimTargetSampled`, `boundedRediscoveryFindsTargetAfterExhaustion`, `rediscoveryStopsAfterMaxAttempts` | [x] |
| 2 | full route change across several MTR steps | `MtrProbeTest.detectsRouteChangeDuringMonitoring`, `RoutePollerTest.pollHostMtrConfirmsRouteChangeOnlyAfterTarget` | [x] |
| 3 | transient TRACE timeout ≠ route change | `RouteChangeDetectorTest.transientTimeoutIsNotRouteChange`, `RoutePollerTest.pollHostRouteTransientTimeoutIsNotRouteChange`, `SessionStoreTest.intermediateTimeoutDoesNotConfirmRouteChange`, `SessionDatabaseRouteTest.transientTimeoutDoesNotCreateNewRouteRow` | [x] |
| 4 | timeout after success history → not UP | `HostNetworkStateClassifierTest.currentTimeoutIsDownEvenWithHealthyHistory`, `SessionStoreTest.timeoutAfterSuccessHistoryIsEndpointDown` | [x] |
| 5 | overflow must not drop delete/rename control jobs | `SessionPersistenceWriterTest.telemetryOverflowDoesNotDropDelete`, `telemetryOverflowDoesNotDropRename` | [x] |
| 6 | GUI/daemon poll_result parity | `CompletedPollTest.guiAndDaemonListenersShareSamePollResultWhenUsingCompletedPoll`, `recordCompletedPollDoesNotTouchSessionStore` | [x] |
| 7 | stuck writer shutdown | `SessionPersistenceWriterTest.closeStopsWorkerBeforeReturning` | [x] |
| 8 | migrate/repair old probe-error rows | `SessionDatabaseMetricRollupTest.migratesV12PollResultAndRollupToV14`, `repairsLegacyProbeErrorTriStateOnAlreadyV14Db`, `PinguiApplicationTest.parseOptions_repairPollResult*` | [x] |

Java run: `cd java && ./gradlew test --tests 'io.pingui.probe.MtrProbeTest' --tests 'io.pingui.monitor.RoutePollerTest' --tests 'io.pingui.monitor.RouteChangeDetectorTest' --tests 'io.pingui.monitor.SessionStoreTest' --tests 'io.pingui.monitor.HostNetworkStateClassifierTest' --tests 'io.pingui.persistence.SessionPersistenceWriterTest' --tests 'io.pingui.monitor.CompletedPollTest' --tests 'io.pingui.persistence.SessionDatabaseMetricRollupTest' --tests 'io.pingui.persistence.SessionDatabaseRouteTest' --tests 'io.pingui.PinguiApplicationTest'`

## P34-010 (done) — Soak / docs sync

**Goal:** lock the audit regression matrix, align README/ROADMAP with `main`≡`beta`, mark phase 34 **closed**.

**Done:**

* ROADMAP **NEXT=DONE**; all P34-001…010 `[x]`; phase index ✅ DONE;
* 8 soak/fault scenarios → tests (table above);
* rename control-lane under telemetry overflow covered by test;
* archival banner on this doc; LIVING_SPEC + JAVA + docs index.

**Branches:** `main` ≡ `beta` after this PR merges.

## Out of scope

New protocols, ORM, large GUI surfaces, silent `.db` delete.

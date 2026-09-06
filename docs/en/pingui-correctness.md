> **Language:** English · [Українська](../pingui-correctness.md)

# Correctness — MTR / projection / side-effects (P33)

> **Archival:** phase 33 **closed** (P33-001…008). Linear queue — **NEXT=DONE**. Stabilization audit: [pingui-stabilization.md](pingui-stabilization.md).

**Source for phase 33.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Audit of `main` @ `28bdb41` (after P32 close). Correctness phase — **not** feature expansion.

> Historical stabilization audit (phase 32): [pingui-stabilization.md](pingui-stabilization.md) — **archival**.

## Summary

P32 landed well (fresh-hop, `target_sampled`, rollup v14, bounded DNS/webhook telemetry, alert lifecycle, i18n, DB split). Remaining blockers: MTR shrinks the monitoring span after an intermediate timeout, the projection layer ignores phase/`targetSampled`, and SQLite/Influx/Timescale may still run on FX/probe threads.

## Linear queue

| ID | Priority | Task | DoD (short) |
|----|----------|------|-------------|
| **P33-001** | P0 | Stable `targetHop` + cursor `1..targetHop` | Intermediate timeout must not skip target; repeated target timeouts stay identifiable; recovery |
| **P33-002** | P0 | Projection: phase / targetSampled / routeChanged | Partial discovery ≠ route change; endpoint only when target sampled |
| **P33-003** | P0 | SessionStore + bounded writers | Fast in-memory path; SQLite/TS off FX/probe; immutable API snapshot |
| **P33-004** | P1 | `poll_result` tri-state | Probe/internal error → `target_sampled=false`, `reachable=null` |
| **P33-005** | P1 | Latency baseline reset | Clear EWMA on route change / probe mode change |
| **P33-006** | P1 | Webhook lifecycle | Bounded queue + rejected counter; closeable dispatcher |
| **P33-007** | P2 | DB migrate + chunked retention | v12→v14 (or offline CLI); chunked retention |
| **P33-008** | P2 | Docs / branch sync | README/ROADMAP/phases match `main`≡`beta` |

## P33-001 (done) — MTR monitoring span

**Bug:** `nextMonitoringCursor()` used `monitoringHopCount()` (reachable prefix). A hop2 timeout shrunk the span so the target was never polled again; a target timeout lost slot identity.

**Fix:**

* `MtrProbeState.targetHop` — stable 1-based target index after discovery;
* `monitoringSpan()` = `targetHop` (fallback: reachable prefix before target is known);
* cursor rotates in `1..monitoringSpan()`;
* `isTargetSlot(hop)` by hop number / `targetHop`;
* re-entering `DISCOVERING` resets `targetHop` to `0`.

**Tests:** `MtrProbeTest` — intermediate timeout→recovery, repeated target timeouts, target recovery.

## P33-002 (done) — Projection semantics

**Bug:** `SessionStore.updateRoute()` independently compared any snapshots; `targetStats()` used the last hop of a partial route; the classifier treated a reachable router as the target.

**Fix:**

* `applyPollSnapshot(..., confirmedRouteChange)` — authoritative `routeChanged` from the probe outcome;
* discovery/timeout must not update `previousRoute` or write a topology change to time-series;
* `lastTargetIp` / `lastTargetHop` + `resolveTargetHop` for endpoint metrics;
* `HostNetworkStateClassifier.targetReached(hops, targetIp)` — intermediate ≠ target.

**Tests:** `SessionStoreTest` (discovery/timeout/confirmed change), `HostNetworkStateClassifierTest`.

## P33-003 (done) — SessionStore + bounded writers

**Bug:** SQLite / time-series HTTP ran synchronously on FX/probe threads; `SessionStore` was unsynchronized for daemon workers.

**Fix:**

* `SessionPersistenceWriter` — bounded queue + single worker; DROP_OLDEST/DROP_NEWEST + `droppedCount`;
* hot path is in-memory under a lock; SQLite/TS via immutable `HostSessionData.copy()` deltas;
* API: `snapshot()` / `currentRouteSnapshot()`.

**Tests:** `SessionPersistenceWriterTest`, updated `SessionStore*Test`.

## P33-004 (done) — `poll_result` tri-state

**Bug:** the failure path always wrote `target_sampled=true` and `reachable=false`, so DNS/permission/internal errors lowered uptime like real downtime.

**Fix:**

* `PollResultEffects.recordPollResult` — set `reachable` only when the target was actually sampled and there is no monitor error;
* `MonitorService` failure → `targetSampled=false`;
* target timeout stays `sampled=true`, `reachable=false`.

**Tests:** `PollResultEffectsPollResultTest` (error/null, timeout/false, caller-marked sampled).

## P33-005 (done) — Latency baseline reset

**Bug:** after a confirmed route change or probe-mode switch, the `latency_high` EWMA still reflected the old path/mode, so the first RTT in the new context could false-FIRE.

**Fix:**

* `PollResultEffects.resetLatencyBaseline(host)` → `AlertRuleEngine.clearLatencyHost`;
* `MonitorService` poll path: when `routeChanged && !oldIps.isEmpty()`, reset **before** `evaluateLatencyHigh`;
* `setHostProbeMode` clears latency baseline for that host.

**Tests:** `AlertRuleEngineTest.clearLatencyHostDropsBaselineForWarmup`, `PollResultEffectsTest.resetLatencyBaselineWarmsUpWithoutFalseHigh`.

## P33-006 (done) — Webhook lifecycle

**Bug:** the webhook executor used an unbounded queue; replacing or stopping alerts did not close the previous pool → leak and unbounded backlog under burst.

**Fix:**

* `WebhookTelemetrySink` — `ThreadPoolExecutor` + `ArrayBlockingQueue` + AbortPolicy; `rejectedCount()` / `queueCapacity()`;
* `AlertDispatcher extends AutoCloseable`; `WebhookAlertDispatcher` / composite / rate-limited close the sink;
* `PollResultEffects.setAlertDispatcher` closes the previous pipeline; `MonitorService.close` → noop (closes pipeline).

**Tests:** saturation reject; close reject; replace closes previous; `AlertDispatchersTest.closeClosesOwnedWebhookPipeline`.

## P33-007 (done) — DB migrate + chunked retention

**Bug:** v12 `.db` files were rejected; retention loaded all old polls in one transaction → long locks / memory on large histories.

**Fix:**

* `SchemaManager.MIN_MIGRATE_FROM = 12`; `migrateV12ToV13` (probe_outcome/target_sampled backfill) → `migrateV13ToV14`;
* `PollResultRetentionJob` — chunked transactions (`DEFAULT_CHUNK_SIZE=500`); delete by ids/keys;
* Tests: `migratesV12PollResultAndRollupToV14`, `processesLargeHistoryInChunks`.

## P33-008 (done) — Docs / branch sync

**Goal:** align README/ROADMAP/indexes with actual `main`≡`beta`, mark phase 33 **closed**, keep archival links to the P32/P33 audits.

**Done:**

* ROADMAP **NEXT=DONE**; all P33-001…008 `[x]`; phase index ✅ DONE;
* README no longer references stale P29-001;
* `pingui-stabilization.md` / `pingui-correctness.md` — archival banners; LIVING_SPEC + JAVA + docs index.

**Branches:** `main` ≡ `beta` after this PR merges.

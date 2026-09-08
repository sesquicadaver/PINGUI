> **Мова:** Українська · [English](en/pingui-route-persistence.md)

# Correctness follow-up — route / target / persistence (P34)

> **Архів:** фаза 34 **closed** (P34-001…010). Актуальний ROADMAP **NEXT=DONE** — [pingui-geoip.md](pingui-geoip.md). Correctness: [pingui-correctness.md](pingui-correctness.md). Stabilization: [pingui-stabilization.md](pingui-stabilization.md).

**Джерело для фази 34.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Аудит `main`≡`beta` @ `45374cc` (2026-09-06, після закриття P33). Фаза — **не** нове функціональне розширення; фокус на NOC/unattended надійність.

> Історичні аудити: [pingui-correctness.md](pingui-correctness.md) (P33), [pingui-stabilization.md](pingui-stabilization.md) (P32) — **архівні**.

## Висновок

P32/P33 суттєво покращили проєкт (fresh-hop, span, tri-state poll_result, bounded writers/webhook, latency reset, schema v12→v14). Але `NEXT = DONE` був передчасним для unattended NOC: route-change все ще може бути хибним, MTR може помилково оголосити router ціллю, поточний timeout може лишити endpoint `UP`, а structural persistence jobs можуть губитися під DROP_OLDEST.

## Черга (лінійна)

| ID | Пріоритет | Задача | DoD (коротко) |
|----|-----------|--------|---------------|
| **P34-001** | P0 | Route identity + candidate-route FSM | [x] TRACE/MTR: hop-indexed `(hop, ip\|timeout)`; active/candidate; confirmation до target; transient `*` ≠ новий route |
| **P34-002** | P0 | MTR target-unknown / exhaustion | [x] `targetHop` лише після real target match; `TARGET_UNKNOWN`/`INCOMPLETE`; bounded rediscovery; без `target_sampled` до ідентифікації |
| **P34-003** | P0 | Current endpoint outcome vs history | [x] Current TIMEOUT → DOWN/PENDING; session-lifetime loss не перекриває актуальний timeout |
| **P34-004** | P0 | Reliable structural persistence + shutdown | [x] Control lane (delete/rename/barrier) без drop; coalesced SaveHost; lossy telemetry; безпечний close |
| **P34-005** | P1 | Immutable `CompletedPoll` + GUI/daemon parity | [x] Один probe-derived poll_result; без mutable SessionStore з probe/FX |
| **P34-006** | P1 | loss / jitter / rollup semantics | [x] Single-packet loss=NULL або явний window; jitter moments/window; availability з target_sampled/reachable |
| **P34-007** | P1 | Bounded DNS + ops counters | [x] Bounded DNS queues + coalesce per host; counters у App Status / API / Prometheus |
| **P34-008** | P1 | v12 migration repair | [x] error rows → `target_sampled=0`, `reachable=NULL`; repair CLI/онлайн для наявних DB |
| **P34-009** | P2 | Python compatibility edition | [x] Зафіксувати bugfix-only; мінімальний shutdown harden; вирівняти version |
| **P34-010** | P2 | Soak / fault matrix + docs sync | [x] Regression matrix з аудиту; README/ROADMAP/`main`≡`beta` |

## Regression / soak–fault matrix (P34-010)

| # | Сценарій | Тести (канон) | Статус |
|---|----------|---------------|--------|
| 1 | target never reached / maxHops exhausted | `MtrProbeTest.maxHopsExhaustedWithoutTargetEntersTargetUnknown`, `allTimeoutsExhaustionDoesNotClaimTargetSampled`, `boundedRediscoveryFindsTargetAfterExhaustion`, `rediscoveryStopsAfterMaxAttempts` | [x] |
| 2 | повна зміна маршруту через кілька MTR-кроків | `MtrProbeTest.detectsRouteChangeDuringMonitoring`, `RoutePollerTest.pollHostMtrConfirmsRouteChangeOnlyAfterTarget` | [x] |
| 3 | transient TRACE timeout ≠ route change | `RouteChangeDetectorTest.transientTimeoutIsNotRouteChange`, `RoutePollerTest.pollHostRouteTransientTimeoutIsNotRouteChange`, `SessionStoreTest.intermediateTimeoutDoesNotConfirmRouteChange`, `SessionDatabaseRouteTest.transientTimeoutDoesNotCreateNewRouteRow` | [x] |
| 4 | timeout після історії успіхів → не UP | `HostNetworkStateClassifierTest.currentTimeoutIsDownEvenWithHealthyHistory`, `SessionStoreTest.timeoutAfterSuccessHistoryIsEndpointDown` | [x] |
| 5 | overflow із delete/rename не губить control jobs | `SessionPersistenceWriterTest.telemetryOverflowDoesNotDropDelete`, `telemetryOverflowDoesNotDropRename` | [x] |
| 6 | GUI/daemon parity для poll_result | `CompletedPollTest.guiAndDaemonListenersShareSamePollResultWhenUsingCompletedPoll`, `recordCompletedPollDoesNotTouchSessionStore` | [x] |
| 7 | stuck writer shutdown | `SessionPersistenceWriterTest.closeStopsWorkerBeforeReturning` | [x] |
| 8 | міграція/repair старого probe error | `SessionDatabaseMetricRollupTest.migratesV12PollResultAndRollupToV14`, `repairsLegacyProbeErrorTriStateOnAlreadyV14Db`, `PinguiApplicationTest.parseOptions_repairPollResult*` | [x] |

Прогін (Java): `cd java && ./gradlew test --tests 'io.pingui.probe.MtrProbeTest' --tests 'io.pingui.monitor.RoutePollerTest' --tests 'io.pingui.monitor.RouteChangeDetectorTest' --tests 'io.pingui.monitor.SessionStoreTest' --tests 'io.pingui.monitor.HostNetworkStateClassifierTest' --tests 'io.pingui.persistence.SessionPersistenceWriterTest' --tests 'io.pingui.monitor.CompletedPollTest' --tests 'io.pingui.persistence.SessionDatabaseMetricRollupTest' --tests 'io.pingui.persistence.SessionDatabaseRouteTest' --tests 'io.pingui.PinguiApplicationTest'`

## P34-010 (зроблено) — Soak / docs sync

**Мета:** зафіксувати regression matrix аудиту, узгодити README/ROADMAP з `main`≡`beta`, позначити фазу 34 **closed**.

**Зроблено:**

* ROADMAP **NEXT=DONE**; усі P34-001…010 `[x]`; індекс фаз ✅ DONE;
* матриця 8 soak/fault сценаріїв → тести (вище);
* rename control-lane під telemetry overflow покрито тестом;
* цей документ — архівний банер; LIVING_SPEC + JAVA + docs index.

**Гілки:** `main` ≡ `beta` після merge цього PR.

## Поза scope

Нові протоколи, ORM, великі GUI-екрани, silent delete `.db`.

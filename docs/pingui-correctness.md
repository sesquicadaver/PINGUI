> **Мова:** Українська · [English](en/pingui-correctness.md)

# Correctness — MTR / projection / side-effects (P33)

**Джерело для фази 33.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Аудит `main` @ `28bdb41` (після закриття P32). Фаза correctness — **не** нове функціональне розширення.

> Історичний stabilization-аудит (фаза 32): [pingui-stabilization.md](pingui-stabilization.md) — **архівний**.

## Висновок

P32 закрито якісно (fresh-hop, `target_sampled`, rollup v14, bounded DNS/webhook telemetry, alert lifecycle, i18n, DB split). Але MTR після intermediate timeout стискає monitoring span, projection layer ігнорує phase/`targetSampled`, а SQLite/Influx/Timescale все ще можуть працювати на FX/probe threads.

## Черга (лінійна)

| ID | Пріоритет | Задача | DoD (коротко) |
|----|-----------|--------|---------------|
| **P33-001** | P0 | Стабільний `targetHop` + cursor `1..targetHop` | Intermediate timeout не пропускає target; повторні target timeouts ідентифікуються; recovery |
| **P33-002** | P0 | Projection: phase / targetSampled / routeChanged | Partial discovery ≠ route change; endpoint лише при target sampled |
| **P33-003** | P0 | SessionStore + bounded writers | In-memory швидко; SQLite/TS поза FX/probe; immutable snapshot для API |
| **P33-004** | P1 | `poll_result` tri-state | Probe/internal error → `target_sampled=false`, `reachable=null` |
| **P33-005** | P1 | Latency baseline reset | Скидання EWMA на route change / probe mode change |
| **P33-006** | P1 | Webhook lifecycle | Bounded queue + rejected counter; closeable dispatcher |
| **P33-007** | P2 | DB migrate + chunked retention | v12→v14 (або offline CLI); retention порціями |
| **P33-008** | P2 | Docs / branch sync | README/ROADMAP/фази узгоджені з `main`≡`beta` |

## P33-001 (зроблено) — MTR monitoring span

**Проблема:** `nextMonitoringCursor()` використовував `monitoringHopCount()` (reachable-prefix). Timeout на hop2 стискав span → target більше не опитувався; timeout на target губив ідентичність слота.

**Виправлення:**

* `MtrProbeState.targetHop` — стабільний 1-based індекс цілі після discovery;
* `monitoringSpan()` = `targetHop` (fallback — reachable prefix до відкриття цілі);
* cursor обертається в `1..monitoringSpan()`;
* `isTargetSlot(hop)` за номером hop / `targetHop`;
* при поверненні в `DISCOVERING` — `targetHop = 0`.

**Тести:** `MtrProbeTest` — intermediate timeout→recovery, повторні target timeouts, target recovery.

## P33-002 (зроблено) — Projection semantics

**Проблема:** `SessionStore.updateRoute()` самостійно порівнював будь-які snapshots; `targetStats()` брав останній hop partial route; classifier вважав reachable router ціллю.

**Виправлення:**

* `applyPollSnapshot(..., confirmedRouteChange)` — авторитетний `routeChanged` з probe outcome;
* discovery/timeout не оновлюють `previousRoute` і не пишуть topology change у time-series;
* `lastTargetIp` / `lastTargetHop` + `resolveTargetHop` для endpoint metrics;
* `HostNetworkStateClassifier.targetReached(hops, targetIp)` — intermediate ≠ target.

**Тести:** `SessionStoreTest` (discovery/timeout/confirmed change), `HostNetworkStateClassifierTest`.

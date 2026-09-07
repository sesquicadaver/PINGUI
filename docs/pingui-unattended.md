> **Мова:** Українська · [English](en/pingui-unattended.md)

# Unattended NOC — probe identity / loss / persistence (P35)

**Джерело для фази 35.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Аудит `main`≡`beta` @ `a1ee5aa` (2026-09-07, після P34-010) — [pingui-current.md](../../GITFOLDER/p-only/pingui-current.md) (зовнішній звіт; канон у репо — цей файл).

> Історичні: [pingui-route-persistence.md](pingui-route-persistence.md) (P34), [pingui-correctness.md](pingui-correctness.md) (P33), [pingui-stabilization.md](pingui-stabilization.md) (P32) — **архівні**.

## Висновок

P34 закрила більшість route/persistence дефектів, але `NEXT = DONE` знову передчасний для unattended NOC: subprocess TRACE може оголосити проміжний router ціллю, MTR `TARGET_UNKNOWN` знову стає `target_sampled`, loss не є справжнім window.

## Черга (лінійна)

| ID | Пріоритет | Задача | DoD (коротко) |
|----|-----------|--------|---------------|
| **P35-001** | P0 | TRACE real target identity | [x] `targetIp` з header/resolve (не last hop); `targetReached` без fallback на останній router |
| **P35-002** | P0 | MTR TARGET_UNKNOWN scope + rediscovery | `probedHop<1` ≠ `PollSampleScope.FULL`; periodic rediscovery з backoff; без downtime з UNKNOWN |
| **P35-003** | P0 | Loss window semantics | Справжнє fixed/sliding window attempts/successes **або** loss=NULL у single-packet |
| **P35-004** | P0 | Timeout ↔ target hop | Timeout атрибуція за `targetHop`/`freshHop`, не за IP `*` |
| **P35-005** | P1 | DNS-control bounded dispatcher | Bounded/coalesce per host; метрики outer queue |
| **P35-006** | P1 | MTR invalidate on DNS change | Підтверджений address-set change скидає targetIp/candidate/latency |
| **P35-007** | P1 | Unified SQLite persistence pipeline | CompletedPoll → один ordered write path (events+poll+route) |
| **P35-008** | P2 | Stuck-writer fault test | close не drain-ить паралельно з живим stuck worker |
| **P35-009** | P2 | Route signature ≡ hops_json | Один stabilized hop-indexed source |
| **P35-010** | P2 | Python lifecycle harden | join/drain без закриття DB під живим worker; без нових фіч |

## Поза scope

Нові протоколи, ORM, великі GUI-екрани, Python feature parity, silent delete `.db`.

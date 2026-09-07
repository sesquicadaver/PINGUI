> **Мова:** Українська · [English](en/pingui-route-persistence.md)

# Correctness follow-up — route / target / persistence (P34)

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
| **P34-004** | P0 | Reliable structural persistence + shutdown | Control lane (delete/rename/barrier) без drop; coalesced SaveHost; lossy telemetry; безпечний close |
| **P34-005** | P1 | Immutable `CompletedPoll` + GUI/daemon parity | Один probe-derived poll_result; без mutable SessionStore з probe/FX |
| **P34-006** | P1 | loss / jitter / rollup semantics | Single-packet loss=NULL або явний window; jitter moments/window; availability з target_sampled/reachable |
| **P34-007** | P1 | Bounded DNS + ops counters | Bounded DNS queues + coalesce per host; counters у App Status / API / Prometheus |
| **P34-008** | P1 | v12 migration repair | error rows → `target_sampled=0`, `reachable=NULL`; repair CLI/онлайн для наявних DB |
| **P34-009** | P2 | Python compatibility edition | Зафіксувати bugfix-only; мінімальний shutdown harden; вирівняти version |
| **P34-010** | P2 | Soak / fault matrix + docs sync | Regression matrix з аудиту; README/ROADMAP/`main`≡`beta` |

## Обовʼязкові regression-тести (фаза)

* target never reached / maxHops exhausted;
* повна зміна маршруту через кілька MTR-кроків;
* transient TRACE timeout ≠ route change;
* timeout після історії успіхів → не UP;
* overflow із delete/rename не губить control jobs;
* GUI/daemon parity для poll_result;
* stuck writer shutdown;
* міграція/repair старого probe error.

## Поза scope

Нові протоколи, ORM, великі GUI-екрани, silent delete `.db`.

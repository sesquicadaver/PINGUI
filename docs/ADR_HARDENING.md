> **Мова:** Українська · [English](en/ADR_HARDENING.md)

# ADR: Hardening post-audit (P26-009)

**Дата:** 2026-09-03  
**Статус:** accepted  
**Гілка:** `beta` (після merge — `main` і `beta`)

## Контекст

Статичний аудит `main` @ `baff7cc` (PR #14) і залишки після P24/P25 на `beta` показали розриви resilience / launchers / structural debt / coverage / latency baseline. GUI paint (buffer churn, coalesce, scene cache) уже закриті в [ADR_GUI_PAINT.md](ADR_GUI_PAINT.md) — **не** повторювати. Фаза 26 (`P26-001`…`008`) впроваджує інкрементальні виправлення; цей ADR фіксує **політику** як SSOT.

Окремий follow-up з аудиту `pimgui-5.md` винесено в **фазу 28** (черга після P27): hang isolation `SinkRegistry`, `inFlight` перед pool, Python schema gate.

## Рішення

### 1. Docs phase sync (P26-001)

| Правило | Деталі |
|---------|--------|
| NEXT SSOT | Лише [ROADMAP.md § NEXT](ROADMAP.md#next--єдине-джерело-правди) |
| README / JAVA | Не казати «фаза N DONE», якщо NEXT ще в цій фазі |
| main vs beta | Таблиця ролей відображає **поточний** NEXT на `beta` |

### 2. Telemetry failure isolation (P26-002)

| Правило | Деталі |
|---------|--------|
| Poll ≠ sink I/O | Падіння / timeout sink **не** блокує poll loop |
| Call timeout | Per-sink call timeout (5s) у `SinkRegistry` |
| Backpressure | Non-blocking bus offers; drop + `failureCount` / metric |
| Shutdown | Documented flush; contract tests |

Деталі шини — [ADR_TELEMETRY.md](ADR_TELEMETRY.md) §7. Повна hang-ізоляція bounded executor — **P28-001**.

### 3. SQLite reopen / corrupt (P26-003)

| Правило | Деталі |
|---------|--------|
| Reopen | Append після reopen без втрати контракту API |
| Legacy migrate | v1/v3→v4 у межах pre-P27 schema; corrupt/truncated → `PersistenceException` |
| Concurrent | Export smoke під час append |

Нормалізація записів schema v5–v7 (без міграції старих `.db`) — **фаза 27** (уже `[x]` у черзі).

### 4. Launcher smoke (P26-004)

| Правило | Деталі |
|---------|--------|
| Quoting | Пробіли в шляхах — `scripts/smoke_launcher.*` + CI |
| Detach | Detached GUI / `--foreground`; Windows `PINGUI_JAVAW` |
| Fail path | Помилка старту → `gui.log` |

### 5. Structural LOC gates (P26-005 / P26-006)

| Правило | Деталі |
|---------|--------|
| `MainController` | Shell ≤ **550** LOC (`MainControllerLocGateTest`); dialogs/geometry/lifecycle — coordinators |
| `MonitorService` | Poll orchestration окремо від post-poll effects (`PollResultEffects`, `TelemetryEmission`) |

### 6. Package JaCoCo (P26-007)

| Gate | Мінімум |
|------|---------|
| `config` / `probe` / `monitor` | ≥ 0.85 |
| `telemetry` | ≥ 0.80 |
| `persistence` | ≥ 0.75 |
| BUNDLE | ≥ 0.80 |
| UI | явний exclude `io/pingui/ui/**` (не «прихований» gap) |

Один bundle-поріг **недостатній** — package minima обов’язкові.

### 7. Latency baseline EWMA (P26-008)

| Правило | Деталі |
|---------|--------|
| AVG | **EWMA** α=`0.2` (`AlertRuleEngine.LATENCY_EWMA_ALPHA`) |
| Anti-poison | «Погані» семпли не оновлюють baseline |
| ETA UI | Help/Settings: ≈ `fail_after × interval` (`LatencyHighRuleConfig.approximateFiringEta`) |

Контракт правила — [ADR_ALERT_RULES.md](ADR_ALERT_RULES.md) §7.

## Наслідки

- Позитив: poll стійкіший до sink fail; launchers перевірені; LOC/coverage gates у CI; latency AVG не роздувається unbounded mean.
- Негатив: hang isolation і PING_ONLY `inFlight` ще в P28; Python session schema parity відстає від Java v7.
- UI package coverage свідомо виключена з PACKAGE minima — manual CHECKLIST лишається обов’язковим.

## Follow-ups (фаза 28+)

| ID | Тема |
|----|------|
| **P28-001** | `SinkRegistry` bounded executor + hang isolation |
| **P28-002** | Reserve `inFlight` before `probePool.execute` |
| **P28-003** | Python `session_db` reject `version != SCHEMA_VERSION` |

## Посилання

- [ROADMAP.md](ROADMAP.md) — фаза 26 / 28
- [CHECKLIST.md](CHECKLIST.md) — § Hardening smoke (P26-009)
- [LIVING_SPEC.md](LIVING_SPEC.md) — матриця P26 → тести
- [ADR_TELEMETRY.md](ADR_TELEMETRY.md), [ADR_ALERT_RULES.md](ADR_ALERT_RULES.md), [ADR_GUI_PAINT.md](ADR_GUI_PAINT.md)

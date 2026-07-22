> **Мова:** Українська · [English](en/ADR_ALERT_RULES.md)

# ADR: Правила якісних алертів (P21-001)

**Статус:** accepted (P21-001)  
**Дата:** 2026-07-22

## Контекст

`ADR_ALERTS` (P10) визначає **канали** доставки (webhook / desktop) і єдиний тригер **`route_change`**. Оператори також потребують оповіщень про **недоступність кінцевої точки** (і пізніше — ріст втрат / затримки), без перетворення PINGUI на повноцінний NMS/alert manager ([ROADMAP X-003](ROADMAP.md)).

Рішення користувача (2026-07-22):

| Тема | Рішення |
|------|---------|
| Scope v1 | Лише **`endpoint_down`**; `loss_high` / `latency_high` → **v2** |
| RESOLVED | Опція користувача (`notify_resolved`, default **false**) |
| Latency (v2) | Default: `rtt ≥ 2×AVG`; FIRING після **3** поганих пінгів **поспіль** (без часового вікна); absolute/інший множник — лише якщо задано |
| Per-host overrides | **Не в v1** (зарезервовано; див. нижче) |
| Форма ADR | Окремий **ADR_ALERT_RULES**; `ADR_ALERTS` лишається SSOT каналів |

## Рішення

### 1. Розділення шарів

| Шар | Документ | Відповідальність |
|-----|----------|------------------|
| Channels | [ADR_ALERTS](ADR_ALERTS.md) | desktop / webhook / `rate_limit` / redact / fan-out |
| Rules | цей ADR | *коли* FIRING/RESOLVED; lifecycle; параметри правил |
| Telemetry | [ADR_TELEMETRY](ADR_TELEMETRY.md) | метрики/події в LOG sinks; **не** заміна alerts |

Правила **не** дублюють URL webhook на рівні host. Канали — лише на рівні профілю.

### 2. Lifecycle (анти-фальшспрацювання)

Для кожного quality-правила — скінченний автомат **на (host, rule)**:

```text
OK → PENDING → FIRING → (cooldown while still bad) → OK
                ↑___________ RESOLVED (опційно notify) ____|
```

| Стан | Зміст |
|------|--------|
| **OK** | умова не виконується |
| **PENDING** | умова є, але ще не `fail_after` поспіль |
| **FIRING** | умова підтверджена; **один** emit на вхід у FIRING |
| після clear | `clear_after` успіхів → OK; якщо `notify_resolved` — один emit RESOLVED |

**Cooldownoff** (`cooldown_minutes`): поки умова true після FIRING — **не** повторювати FIRING (journal/telemetry можуть оновлюватись без toast/webhook-дубля). Глобальний `alerts.rate_limit` (ADR_ALERTS) лишається запасним запобіжником.

### 3. Правило v1 — `endpoint_down`

**Сигнал:** кінцева ціль unreachable / terminal timeout (`HostTargetStats.timeout` або еквівалент ping-only / trace).

**Не сигнал:** одиничний поганий poll; `onProbeError` (лишається telemetry); зміна маршруту (окремо `route_change`).

| Параметр | Default (пресет «Збалансовано») | Опис |
|----------|----------------------------------|------|
| `enabled` | `false` | default off |
| `fail_after` | `3` | послідовні fail → FIRING |
| `clear_after` | `2` | послідовні ok → RESOLVED/OK |
| `cooldown_minutes` | `15` | між повторними FIRING |

Пресет чутливості (GUI / YAML alias):

| Пресет | fail_after | clear_after | cooldown_minutes |
|--------|------------|-------------|------------------|
| Спокійно | 5 | 3 | 30 |
| Збалансовано | 3 | 2 | 15 |
| Чутливо | 2 | 1 | 5 |

Фактичний час до FIRING ≈ `fail_after × effective_interval` (interval може бути per-host уже сьогодні).

### 4. Опція `notify_resolved`

Профільний прапор `alerts.notify_resolved` (default **false**):

- `false` — webhook/desktop лише на FIRING (і `route_change` як раніше);
- `true` — додатково один emit при переході FIRING→OK.

Рекомендація UX: desktop resolve можна лишити вимкненим навіть коли webhook resolve увімкнено (реалізація — окремий ticket; контракт мінімум — один булів на профіль).

### 5. Payload envelope (розширення)

Канали лишаються з ADR_ALERTS. Для quality-подій — JSON з дискримінатором `event` (не лише `"route_change"`):

| Поле | Обовʼязкове | Опис |
|------|-------------|------|
| `event` | так | `endpoint_down` (v1); reserved: `loss_high`, `latency_high` |
| `state` | так | `firing` \| `resolved` |
| `host` | так | адреса цілі |
| `timestamp` | так | ISO-8601 UTC |
| `profile` | ні | імʼя профілю |
| `rule` | так | id правила (`endpoint_down`) |
| `detail` | ні | напр. `fail_after`, лічильник, останній сигнал |

`route_change` payload **без зламу** (ADR_ALERTS). Нові споживачі читають `event`.

Імплементація може узагальнити `AlertDispatcher` до спільного `AlertEvent` або адаптера — **поза** цим ADR ticket (P21-002+).

### 6. YAML (контракт профілю)

```yaml
alerts:
  desktop: false
  webhook: null
  rate_limit: 10
  notify_resolved: false
  rules:
    endpoint_down:
      enabled: false
      fail_after: 3
      clear_after: 2
      cooldown_minutes: 15
      # або: preset: balanced   # calm | balanced | sensitive
```

Пріоритет: CLI (майбутні flags) > YAML профілю > defaults.  
**Per-host `alerts:` — не в v1.**

### 7. Зарезервовано (v2, не імплементувати в P21-001…003 без окремого ID)

- **`loss_high`:** threshold + clear (hysteresis) + window + sustain.
- **`latency_high`** (рішення 2026-07-22 — пресет «критичний» / defaults):
  - **Сигнал за замовчуванням:** `rtt ≥ 2 × AVG` (terminal RTT; AVG з успішних проб сесії). Інший множник / absolute `threshold_ms` — лише якщо явно задано в YAML/GUI.
  - **FIRING:** **`fail_after = 3`** послідовні «погані» пінги; **без** часового вікна (interval не входить у умову).
  - Lifecycle далі як у §2: `clear_after`, `cooldown_minutes`, `notify_resolved`; warm-up до появи AVG (немає relative-алерту, доки AVG ще немає).
  - Не плутати з `endpoint_down`: висока затримка при успішному reply — лише `latency_high`.
- **Sparse per-host YAML override** правил (патерн як `intervalSecondsOverride`); GUI per-host — лише після YAML і явного попиту.

### 8. Non-goals (X-003)

- Ack/nack, escalation policies, dependency trees, multi-condition expressions.
- Окремий durable retry queue для alerts.
- Дублювання channels на кожному host.
- Заміна journal / route graph / telemetry sinks.

### 9. Інтеграція (наступні ticket-и)

```text
Poll → HostTargetStats
    → AlertRuleEngine (pure)  # P21-002
         → AlertEvent
              → існуючий RateLimited + Composite channels  # ADR_ALERTS
YAML/GUI rules + notify_resolved  # P21-003
```

## Наслідки

- Документація: цей ADR + патч Related у ADR_ALERTS; CONFIGURATION § reserved; LIVING_SPEC.
- Код правил — **не** частина P21-001.
- Python parity rules — окремо після Java engine (якщо потрібно).

## Посилання
- [ADR_HOST_PROBLEM_INDICATOR.md](ADR_HOST_PROBLEM_INDICATOR.md) — in-app badge / ack / session stats (P22)

- [ADR_ALERTS.md](ADR_ALERTS.md) — канали й `route_change`
- [ROADMAP.md](ROADMAP.md) — фаза 21 (P21-*)
- `HostTargetStats`, `AlertConfig`, `AlertsSettingsDialog` (існуючі channels GUI)

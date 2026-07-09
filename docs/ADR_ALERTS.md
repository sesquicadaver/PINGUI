> **Мова:** Українська · [English](en/ADR_ALERTS.md)

# ADR: Політика оповіщень про зміну маршруту (P10-001)

**Статус:** accepted (P10-001)  
**Дата:** 2026-07-09

## Контекст

PINGUI виявляє зміну послідовності hop-IP (`RouteChangeDetector` / `detect_route_change`) і вже викликає callback `onRouteChanged` у monitor-шарі. Для NOC/SRE потрібні **канали поза GUI**: webhook у runbook-систему та (опційно) desktop notification на робочій станції оператора.

Python `beta` містить референсну реалізацію (`alert_dispatcher.py`, `alert_rate_limiter.py`, `desktop_notifier.py`). Java `beta` — повний alert pipeline (фаза 10, P10-010…P10-050).

Обмеження продукту: PINGUI — route-focused utility, не повноцінний alert manager (див. ROADMAP X-003).

## Рішення

### 1. Подія-тригер

- Оповіщення лише при **реальній зміні** маршруту (порівняння списків IP hop-ів).
- **Перше спостереження** (`previous_ips` порожній) — **без alert** (уникнення шуму при старті).
- Probe errors (`onProbeError`) — **не** генерують route-change alert (окремі канали — фаза 16 telemetry).

### 2. Канали v1 (in scope)

| Канал | v1 | Реалізація | Примітка |
|-------|----|------------|----------|
| **Webhook** | ✅ | `POST` JSON | Generic schema; Slack Incoming Webhook приймає JSON — достатньо generic body або тонкий mapper у споживача |
| **Desktop** | ✅ (Linux) | `notify-send` | Python: `DesktopAlertDispatcher`; Java: P10-020 (Linux пріоритет; Win/macOS — best-effort пізніше) |
| **Email** | ❌ | — | Out of scope v1 |
| **SNMP trap** | ❌ | — | Out of scope v1 |
| **PagerDuty/Opsgenie native** | ❌ | — | Через generic webhook |

Кілька каналів одночасно — **fan-out** (`CompositeAlertDispatcher`); помилка одного каналу **не блокує** інші.

### 3. Payload — `RouteChangeEvent`

Єдина модель для webhook і (майбутньої) персистенції / telemetry (P16-050).

| Поле | Тип | Обовʼязкове | Опис |
|------|-----|-------------|------|
| `event` | string | так | Завжди `"route_change"` |
| `host` | string | так | Ціль моніторингу (YAML address) |
| `old_ips` | string[] | так | Попередній маршрут (hop IP, без timeout) |
| `new_ips` | string[] | так | Новий маршрут |
| `timestamp` | string | так | ISO-8601 UTC (`…Z`) |
| `profile` | string | ні | Імʼя профілю (default `"default"`) |

Приклад:

```json
{
  "event": "route_change",
  "host": "8.8.8.8",
  "old_ips": ["10.0.0.1", "192.168.1.1"],
  "new_ips": ["10.0.0.1", "8.8.8.8"],
  "timestamp": "2026-07-09T07:30:00Z",
  "profile": "default"
}
```

Серіалізація: Python `RouteChangeEvent.to_json()` / `from_json()`; Java — дзеркало в `RouteChangeEvent.java` (P10-010).

### 4. Rate limit

- **Per host**, rolling window **1 година**.
- Default: **10 alerts / host / годину** (`--alert-rate-limit`, майбутнє YAML `alert_rate_limit`).
- Перевищення — **drop + debug log**, monitor loop не зупиняється.
- Rate limit застосовується **до fan-out** (один лічильник на подію, не на канал).

### 5. Помилки та безпека

| Ситуація | Поведінка |
|----------|-----------|
| Webhook timeout / HTTP error | `WARNING` у лог; **без crash** |
| Desktop notify недоступний | skip (Linux без `notify-send`) |
| URL webhook у логах | **redact** credentials/query (`redact_webhook_url`) |
| TLS / self-signed | v1: стандартна поведінка HTTP-клієнта ОС; custom CA — out of scope v1 |

### 6. Конфігурація (пріоритет)

1. CLI flags (найвищий пріоритет)
2. Поля активного YAML-профілю (P10-021 / P10-031)
3. Default: alerts **вимкнено** (`NoOp` / `null` dispatcher)

Python CLI (reference):

- `--alert-webhook URL`
- `--desktop-alerts`
- `--alert-rate-limit N` (default 10)

Java parity: `PinguiApplication` + `CliProfileOverrides` (P10-031, P10-021).

### 7. Інтеграція в monitor

```
RoutePoller → RouteChangeDetector
     → RouteChangeEvent.from_route_change(...)
     → RateLimitedAlertDispatcher
           → CompositeAlertDispatcher [Webhook, Desktop, …]
```

Java: виклик з `MonitorService` після `onRouteChanged` (P10-011), не з UI thread для webhook.

GUI Java лишає journal/route graph як є; desktop alert — опційний канал, не заміна UI.

### 8. Майбутнє (не v1)

- **P16-050:** `WebhookAlertDispatcher` стає `TelemetrySink` — один emit шлях, без дублювання HTTP.
- YAML `alerts:` секція з кількома sinks (фаза 16).
- Збагачення payload (ASN, geo, diff summary) — окремі ticket-и, backward-compatible поля.

## Наслідки

- **Python `beta`:** реалізація відповідає ADR; ROADMAP PY-041…045 — reference для Java.
- **Java:** P10-010…P10-050 реалізують цей ADR; тести — contract JSON + rate limit burst.
- **Документація:** CONFIGURATION (P10-021), CHECKLIST alert smoke (P10-050), LIVING_SPEC матриця.
- **Не робити:** окремий alert engine, retry queue з durable delivery, ack/nack протокол.

## Посилання

- [ROADMAP.md](ROADMAP.md) — фаза 10 (P10-*), фаза 16 (P16-050)  
- Python: `src/pingui/models.py` (`RouteChangeEvent`), `src/pingui/monitor/alert_dispatcher.py`  
- Java (planned): `monitor/AlertDispatcher.java`, `monitor/WebhookAlertDispatcher.java`
